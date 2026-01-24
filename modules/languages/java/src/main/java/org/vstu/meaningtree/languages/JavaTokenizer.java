package org.vstu.meaningtree.languages;

import org.treesitter.TSNode;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.PlainCollectionLiteral;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.NewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.BytePosition;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;
import org.vstu.meaningtree.utils.tokens.*;

import java.util.*;
import java.util.stream.Collectors;

public class JavaTokenizer extends LanguageTokenizer {
    private static final List<String> stopNodes = List.of("string_literal", "char_literal");
    private Set<Long> valueSetNodes = new HashSet<>();

    protected static final Map<String, OperatorToken> operators = new HashMap<>() {{
        List<OperatorToken> braces = OperatorToken.makeComplex(1,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, true,
                new String[] {"(", ")"},
                new TokenType[] {TokenType.CALL_OPENING_BRACE, TokenType.CALL_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("CALL_(", braces.getFirst());
        put("CALL_)", braces.getLast());

        List<OperatorToken> collection = OperatorToken.makeComplex(1,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"{", "}"},
                new TokenType[] {TokenType.INITIALIZER_LIST_OPENING_BRACE, TokenType.INITIALIZER_LIST_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("{", collection.getFirst());
        put("}", collection.getLast());

        List<OperatorToken> subscript = OperatorToken.makeComplex(1,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"[", "]"},
                new TokenType[] {TokenType.SUBSCRIPT_OPENING_BRACE, TokenType.SUBSCRIPT_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("[", subscript.getFirst());
        put("]", subscript.getLast());

        put("new", new OperatorToken("new", TokenType.OPERATOR, 1, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        get("new").additionalOpType = OperatorType.NEW;
        put(".", new OperatorToken(".", TokenType.OPERATOR, 1, OperatorAssociativity.LEFT, OperatorArity.UNARY, false, OperatorTokenPosition.POSTFIX));

        put("++", new OperatorToken("++", TokenType.OPERATOR, 2, OperatorAssociativity.LEFT, OperatorArity.UNARY, false, OperatorTokenPosition.POSTFIX).setFirstOperandToEvaluation(OperandPosition.LEFT));   // Постфиксный инкремент
        put("--", new OperatorToken("--", TokenType.OPERATOR, 2, OperatorAssociativity.LEFT, OperatorArity.UNARY, false, OperatorTokenPosition.POSTFIX).setFirstOperandToEvaluation(OperandPosition.LEFT));   // Постфиксный декремент

        put("+U", new OperatorToken("+", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));   // Унарный плюс
        put("-U", new OperatorToken("-", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));   // Унарный минус
        put("++U", new OperatorToken("++", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false)); // Префиксный инкремент
        put("--U", new OperatorToken("--", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false)); // Префиксный декремент
        put("!", new OperatorToken("!", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));     // Логическое НЕ
        put("~", new OperatorToken("~", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));     // Побитовая инверсия

        put("CAST", new OperatorToken("CAST", TokenType.CAST, 4, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));

        put("*", new OperatorToken("*", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Умножение
        put("/", new OperatorToken("/", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Деление
        put("%", new OperatorToken("%", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Остаток от деления

        put("+", new OperatorToken("+", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Сложение (бинарный)
        put("-", new OperatorToken("-", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Вычитание (бинарный)

        put("<<", new OperatorToken("<<", TokenType.OPERATOR, 7, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));   // Левый сдвиг
        put(">>", new OperatorToken(">>", TokenType.OPERATOR, 7, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));   // Правый сдвиг
        put(">>>", new OperatorToken(">>>", TokenType.OPERATOR, 7, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Беззнаковый правый сдвиг

        put("<", new OperatorToken("<", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Меньше
        put("<=", new OperatorToken("<=", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));   // Меньше или равно
        put(">", new OperatorToken(">", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Больше
        put(">=", new OperatorToken(">=", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));   // Больше или равно
        put("instanceof", new OperatorToken("instanceof", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Проверка на тип

        put("==", new OperatorToken("==", TokenType.OPERATOR, 9, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));   // Равно
        put("!=", new OperatorToken("!=", TokenType.OPERATOR, 9, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));   // Не равно

        put("&", new OperatorToken("&", TokenType.OPERATOR, 10, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));     // Побитовое И
        put("^", new OperatorToken("^", TokenType.OPERATOR, 11, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));    // Побитовое исключающее ИЛИ
        put("|", new OperatorToken("|", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));    // Побитовое ИЛИ

        put("&&", new OperatorToken("&&", TokenType.OPERATOR, 13, OperatorAssociativity.LEFT, OperatorArity.BINARY, true, OperatorTokenPosition.INFIX, OperatorType.AND));  // Логическое И
        put("||", new OperatorToken("||", TokenType.OPERATOR, 14, OperatorAssociativity.LEFT, OperatorArity.BINARY, true, OperatorTokenPosition.INFIX, OperatorType.OR));  // Логическое ИЛИ

        List<OperatorToken> ternary = OperatorToken.makeComplex(15, OperatorArity.TERNARY,
                OperatorAssociativity.RIGHT, true, new String[] {"?", ":"},
                new TokenType[] {TokenType.OPERATOR, TokenType.OPERATOR}
        );
        put("?", ternary.getFirst().setFirstOperandToEvaluation(OperandPosition.LEFT));  // Тернарный оператор
        put(":", ternary.getLast().setFirstOperandToEvaluation(OperandPosition.LEFT));
        get("?").additionalOpType = OperatorType.CONDITIONAL;
        get(":").additionalOpType = OperatorType.CONDITIONAL;

        put("=", new OperatorToken("=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));   // Присваивание
        put("+=", new OperatorToken("+=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Сложение с присваиванием
        put("-=", new OperatorToken("-=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Вычитание с присваиванием
        put("*=", new OperatorToken("*=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Умножение с присваиванием
        put("/=", new OperatorToken("/=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Деление с присваиванием
        put("%=", new OperatorToken("%=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Остаток с присваиванием
        put("<<=", new OperatorToken("<<=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Левый сдвиг с присваиванием
        put(">>=", new OperatorToken(">>=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Правый сдвиг с присваиванием
        put(">>>=", new OperatorToken(">>>=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Беззнаковый правый сдвиг с присваиванием
        put("&=", new OperatorToken("&=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Побитовое И с присваиванием
        put("^=", new OperatorToken("^=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Побитовое исключающее ИЛИ с присваиванием
        put("|=", new OperatorToken("|=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Побитовое ИЛИ с присваиванием
    }};


    public JavaTokenizer(JavaTranslator translator) {
        super(translator, List.of(
                "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
                "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
                "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super",
                "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "_"
        ));
    }

    @Override
    protected List<String> getStopNodes() {
        return stopNodes;
    }

    @Override
    public OperatorToken getOperatorByTokenName(String tokenName) {
        OperatorToken res = operators.getOrDefault(tokenName, null);
        if (res == null) {
            return null;
        }
        return res.clone();
    }

    @Override
    protected OperatorToken getOperator(String tokenValue, TSNode node) {
        if (!node.getParent().isNull() && node.getParent().getType().equals("unary_expression") && List.of("+", "-").contains(tokenValue)) {
            if (tokenValue.equals("+")) {
                return operators.get("+U").clone();
            } else if (tokenValue.equals("-")) {
                return operators.get("-U").clone();
            }
        }

        if (!node.getParent().isNull() && tokenValue.equals("(") && !node.getParent().getParent().isNull() && node.getParent().getParent().getType().equals("method_invocation")) {
            return operators.get("CALL_(").clone();
        }

        if (!node.getParent().isNull() && tokenValue.equals(")") && !node.getParent().getParent().isNull() && node.getParent().getParent().getType().equals("method_invocation")) {
            return operators.get("CALL_)").clone();
        }

        if (!node.getParent().isNull() && node.getParent().getType().equals("update_expression") &&
                TreeSitterUtils.getCodePiece(code, node.getParent()).startsWith("++") && tokenValue.equals("++")) {
            return operators.get("++U").clone();
        }

        if (!node.getParent().isNull() && node.getParent().getType().equals("update_expression") &&
                TreeSitterUtils.getCodePiece(code, node.getParent()).startsWith("--")  && tokenValue.equals("--")) {
            return operators.get("--U").clone();
        }

        if (tokenValue.equals("{") || tokenValue.equals("}")) {
            return null;
        }

        OperatorToken tok = operators.getOrDefault(tokenValue, null);
        return tok == null ? null : tok.clone();
    }

    @Override
    protected Token recognizeToken(TSNode node) {
        String type = node.getType();
        TSNode parent = node.getParent();
        OperatorToken recognizedOperator = getOperator(type, node);

        if (recognizedOperator != null) {
            return recognizedOperator;
        }

        TokenType tokenType;

        if (type.equals("{")) {
            if (parent.getType().equals("array_initializer")) {
                tokenType = TokenType.INITIALIZER_LIST_OPENING_BRACE;
            } else {
                tokenType = TokenType.COMPOUND_OPENING_BRACE;
            }
        } else if (type.equals("}")) {
            if (parent.getType().equals("array_initializer")) {
                tokenType = TokenType.INITIALIZER_LIST_CLOSING_BRACE;
            } else {
                tokenType = TokenType.COMPOUND_CLOSING_BRACE;
            }
        } else if (type.equals("(")) {
            tokenType = TokenType.OPENING_BRACE;
        } else if (type.equals(")")) {
            tokenType = TokenType.CLOSING_BRACE;
        } else if (type.equals(";")) {
            tokenType = TokenType.SEPARATOR;
        } else if (List.of("type_identifier", "identifier",
                "integral_type", "boolean_type").contains(type)) {
            if (!parent.isNull() && parent.getType().equals("method_invocation")) {
                tokenType = TokenType.CALLABLE_IDENTIFIER;
            } else {
                tokenType = TokenType.IDENTIFIER;
            }
        } else if (List.of("decimal_floating_point_literal", "decimal_integer_literal",
                "string_literal", "true", "false", "null_literal", "char_literal"
                ).contains(type)) {
            tokenType = TokenType.CONST;
        } else if (type.equals(",")) {
            tokenType = TokenType.COMMA;
        } else if (type.equals(TreeSitterUtils.getCodePiece(code, node))) {
            tokenType = TokenType.KEYWORD;
        } else if (List.of("line_comment", "block_comment").contains(type)) {
            tokenType = TokenType.COMMENT;
        } else {
            tokenType = TokenType.UNKNOWN;
        }

        var tok = new Token(TreeSitterUtils.getCodePiece(code, node), tokenType);
        tok.setBytePosition(new BytePosition(node.getStartByte(), node.getEndByte() - node.getStartByte()));
        return tok;
    }

    @Override
    protected List<String> getOperatorNodes(OperatorArity arity) {
        return switch (arity) {
            case UNARY -> List.of("unary_expression", "update_expression");
            case BINARY -> List.of("binary_expression", "field_access",
                    "cast_expression", "array_access", "method_invocation",
                    "instanceof_expression", "assignment_expression"
            );
            case TERNARY -> List.of("ternary_expression");
        };
    }

    @Override
    protected String getFieldNameByOperandPos(OperandPosition pos, String operatorNode) {
        if (List.of("unary_expression", "update_expression").contains(operatorNode)) {
            return "operand";
        } else if (List.of("binary_expression", "assignment_expression", "instanceof_expression").contains(operatorNode)) {
            if (pos == OperandPosition.LEFT) {
                return "left";
            } else if (pos == OperandPosition.RIGHT) {
                return "right";
            }
        } else if (operatorNode.equals("field_access")) {
            if (pos == OperandPosition.LEFT) {
                return "object";
            } else if (pos == OperandPosition.RIGHT) {
                return "field";
            }
        } else if (operatorNode.equals("cast_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "type";
            } else if (pos == OperandPosition.RIGHT) {
                return "value";
            }
        } else if (operatorNode.equals("ternary_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "condition";
            } else if (pos == OperandPosition.CENTER) {
                return "consequence";
            } else if (pos == OperandPosition.RIGHT) {
                return "alternative";
            }
        } else if (operatorNode.equals("array_access")) {
            if (pos == OperandPosition.LEFT) {
                return "array";
            } else if (pos == OperandPosition.RIGHT) {
                return "index";
            }
        } else if (operatorNode.equals("method_invocation")) {
            if (pos == OperandPosition.LEFT) {
                return "object";
            } else if (pos == OperandPosition.CENTER) {
                return "arguments";
            }
        } else if (operatorNode.equals("assignment_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "object";
            } else if (pos == OperandPosition.CENTER) {
                return "arguments";
            }
        }
        return null;
    }

    @Override
    public TokenList tokenizeExtended(Node node) {
        TokenList result = new TokenList();
        tokenizeExtended(node, result);
        return result;
    }

    public TokenGroup tokenizeExtended(Node node, TokenList result) {
        if (node instanceof BinaryExpression expr) {
            node = this.viewer.parenFiller.process(expr);
        } else if (node instanceof UnaryExpression expr) {
            node = this.viewer.parenFiller.process(expr);
        } else if (node instanceof IndexExpression expr) {
            node = this.viewer.parenFiller.process(expr);
        } else if (node instanceof CastTypeExpression expr) {
            node = this.viewer.parenFiller.process(expr);
        } else if (node instanceof MemberAccess expr) {
            node = this.viewer.parenFiller.process(expr);
        } else if (node instanceof TernaryOperator expr) {
            node = this.viewer.parenFiller.process(expr);
        } else if (node instanceof QualifiedIdentifier expr) {
            node = this.viewer.parenFiller.process(expr);
        } else if (node instanceof MethodCall call) {
            node = this.viewer.parenFiller.process(call);
        }

        if (node.hasLabel(Label.DUMMY)) {
            return new TokenGroup(0, 0, result);
        }
        int posStart = result.size();
        switch (node) {
            case AssignmentExpression assignment -> {
                OperatorToken opTok = getOperatorByTokenName("=");
                TokenGroup group1 = tokenizeExtended(assignment.getLValue(), result);
                result.add(opTok);
                TokenGroup group2 = tokenizeExtended(assignment.getRValue(), result);
                group1.setMetadata(opTok, OperandPosition.LEFT);
                group2.setMetadata(opTok, OperandPosition.RIGHT);
                if (assignment.getAssignedValueTag() != null) {
                    opTok.assignValue(assignment.getAssignedValueTag());
                    valueSetNodes.add(assignment.getId());
                }
            }
            case BinaryExpression binOp -> tokenizeBinary(binOp, result);
            case PointerPackOp packOp -> tokenizeExtended(packOp.getArgument(), result);
            case PointerUnpackOp unpackOp -> tokenizeExtended(unpackOp.getArgument(), result);
            case UnaryExpression unaryOp -> tokenizeUnary(unaryOp, result);
            case FunctionCall call -> tokenizeCall(call, result);
            case NewExpression newExpr -> tokenizeNew(newExpr, result);
            case CastTypeExpression cast -> tokenizeCast(cast, result);
            case SizeofExpression ignored -> throw new UnsupportedViewingException("Sizeof is disabled in this language");
            case MemberAccess access -> tokenizeFieldOp(access, result);
            case CompoundComparison comparison -> tokenizeCompoundComparison(comparison, result);
            case IndexExpression subscript -> tokenizeSubscript(subscript, result);
            case PlainCollectionLiteral plain -> tokenizeNew(plain.toArrayNew(), result);
            case TernaryOperator ternary -> tokenizeTernary(ternary, result);
            case DefinitionArgument arg -> tokenizeExtended(arg.getInitialExpression(), result);
            case SimpleIdentifier ident -> {
                result.add(new Token(ident.getName(), TokenType.IDENTIFIER));
            }
            case QualifiedIdentifier ident -> {
                tokenizeExtended(ident.getScope(), result);
                result.add(new Token("::", TokenType.IDENTIFIER));
                tokenizeExtended(ident.getMember(), result);
            }
            case ScopedIdentifier ident -> {
                for (SimpleIdentifier simple : ident.getScopeResolution()) {
                    tokenizeExtended(simple, result);
                    result.add(getOperatorByTokenName("."));
                }
                if (!ident.getScopeResolution().isEmpty()) {
                    result.removeLast();
                }
            }
            case ParenthesizedExpression paren -> {
                result.add(new Token("(", TokenType.OPENING_BRACE));
                tokenizeExtended(paren.getExpression(), result);
                result.add(new Token(")", TokenType.CLOSING_BRACE));
            }

            case AssignmentStatement assignment -> {
                OperatorToken opTok = getOperatorByTokenName("=");
                TokenGroup group1 = tokenizeExtended(assignment.getLValue(), result);
                result.add(opTok);
                TokenGroup group2 = tokenizeExtended(assignment.getRValue(), result);
                group1.setMetadata(opTok, OperandPosition.LEFT);
                group2.setMetadata(opTok, OperandPosition.RIGHT);
                result.add(new Token(";", TokenType.SEPARATOR));
                if (assignment.getAssignedValueTag() != null) {
                    opTok.assignValue(assignment.getAssignedValueTag());
                    valueSetNodes.add(assignment.getId());
                }
            }
            case CommaExpression comma -> throw new UnsupportedViewingException("Comma is unsupported in this language");
            case ExpressionSequence sequence -> {
                for (Expression expr : sequence.getExpressions()) {
                    tokenizeExtended(expr, result);
                    result.add(new Token(",", TokenType.COMMA));
                }
                if (!sequence.getExpressions().isEmpty()) result.removeLast();
            }
            case ExpressionStatement exprStmt -> {
                tokenizeExtended(exprStmt.getExpression(), result);
                result.add(new Token(";", TokenType.SEPARATOR));
            }
            default -> {
                String s = viewer.toString(node);
                TokenList tokens = tokenize(s);
                result.addAll(tokens.subList(0, tokens.getLast().type == TokenType.SEPARATOR ? tokens.size() - 1 : tokens.size()));
            }
        }
        int posStop = result.size();
        TokenGroup resultGroup = new TokenGroup(posStart, posStop, result);
        if (!(node instanceof ParenthesizedExpression) && node.getAssignedValueTag() != null && !valueSetNodes.contains(node.getId())) {
            resultGroup.assignValue(node.getAssignedValueTag());
            valueSetNodes.add(node.getId());
        }
        return resultGroup;
    }

    private void tokenizeCast(CastTypeExpression cast, TokenList result) {
        TokenList lst = tokenizeExtended(cast.getCastType());
        String typeName = lst.stream().map((Token t) -> t.value).collect(Collectors.joining(" "));
        OperatorToken op = getOperatorByTokenName("CAST").clone("(" + typeName + ")");
        result.add(op);
        TokenGroup arg = tokenizeExtended(cast.getValue(), result);
        arg.setMetadata(op, OperandPosition.RIGHT);
    }

    private void tokenizeNew(NewExpression newExpr, TokenList result) {
        switch (newExpr) {
            case ObjectNewExpression objNew -> {
                TokenList lst = tokenizeExtended(newExpr.getType());
                String typeName = lst.stream().map((Token t) -> t.value).collect(Collectors.joining(" "));
                OperatorToken newTok = getOperatorByTokenName("new").clone("new " + typeName + "(");
                result.add(newTok);
                for (Expression expr : objNew.getConstructorArguments()) {
                    TokenGroup operand = tokenizeExtended(expr, result);
                    result.add(new Token(",", TokenType.COMMA).setOwner(newTok));
                    operand.setMetadata(newTok, OperandPosition.CENTER);
                }
                if (!objNew.getConstructorArguments().isEmpty()) result.removeLast();
                result.add(new Token(")", TokenType.CLOSING_BRACE));
                if (newExpr.getAssignedValueTag() != null) {
                    newTok.assignValue(newExpr.getAssignedValueTag());
                    valueSetNodes.add(newExpr.getId());
                }
            }
            case ArrayNewExpression arrNew -> {
                TokenList lst = tokenizeExtended(newExpr.getType());
                String typeName = lst.stream().map((Token t) -> t.value).collect(Collectors.joining(" "));
                OperatorToken newTok = getOperatorByTokenName("new").clone("new " + typeName + "[");
                result.add(newTok);
                for (int i = 0; i < arrNew.getShape().getDimensionCount(); i++) {
                    if (arrNew.getShape().getDimension(i) != null) {
                        TokenGroup operand = tokenizeExtended(arrNew.getShape().getDimension(i), result);
                        operand.setMetadata(newTok, OperandPosition.CENTER);
                    }
                    if (i != arrNew.getShape().getDimensionCount() - 1) {
                        result.add(new Token("][", TokenType.SEPARATOR));
                    }
                }
                result.add(new Token("]", TokenType.SEPARATOR));
                if (arrNew.getAssignedValueTag() != null) {
                    newTok.assignValue(arrNew.getAssignedValueTag());
                    valueSetNodes.add(arrNew.getId());
                }
                if (arrNew.getInitializer() != null) {
                    newTok.additionalOpType = OperatorType.NEW_ARRAY;
                    OperatorToken tok = getOperatorByTokenName("{");
                    tok.setMetadata(newTok, OperandPosition.RIGHT);
                    result.add(tok);
                    for (Expression expr : arrNew.getInitializer().getValues()) {
                        TokenGroup grp = tokenizeExtended(expr, result);
                        grp.setMetadata(tok, OperandPosition.CENTER);
                        result.add(new Token(",", TokenType.COMMA).setOwner(tok));
                    }
                    if (!arrNew.getInitializer().getValues().isEmpty()) result.removeLast();
                    result.add(getOperatorByTokenName("}"));
                }
            }
            default -> throw new MeaningTreeException("New expression of unknown type");
        }
    }

    private void tokenizeCall(FunctionCall call, TokenList result) {
        TokenGroup complexName = null;
        OperatorToken tok = getOperatorByTokenName("CALL_(");
        tok.additionalOpType = call instanceof MethodCall ? OperatorType.METHOD_CALL : OperatorType.OTHER;
        if (call instanceof MethodCall method) {
            complexName = tokenizeExtended(method.getObject(), result);
            tok = tok.clone("." + ((SimpleIdentifier)method.getFunctionName()).getName() + "(");
        } else {
            if (!(call.getFunction() instanceof SimpleIdentifier)) {
                throw new UnsupportedParsingException("This language supports only simple identifier as function name");
            }
            result.add(new Token(((SimpleIdentifier)call.getFunctionName()).getName(), TokenType.CALLABLE_IDENTIFIER));
        }
        if (complexName != null) complexName.setMetadata(tok, OperandPosition.LEFT);
        result.add(tok);
        for (Expression expr : call.getArguments()) {
            TokenGroup operand = tokenizeExtended(expr, result);
            result.add(new Token(",", TokenType.COMMA).setOwner(tok));
            operand.setMetadata(tok, OperandPosition.CENTER);
        }
        if (!call.getArguments().isEmpty()) result.removeLast();
        result.add(getOperatorByTokenName("CALL_)"));
        if (call.getAssignedValueTag() != null) {
            tok.assignValue(call.getAssignedValueTag());
            valueSetNodes.add(call.getId());
        }
    }

    private void tokenizeUnary(UnaryExpression unaryOp, TokenList result) {
        String operator = switch (unaryOp) {
            case NotOp op -> "!";
            case InversionOp op -> "~";
            case UnaryMinusOp op -> "-U";
            case UnaryPlusOp op -> "+U";
            case PostfixIncrementOp op -> "++";
            case PrefixIncrementOp op -> "++U";
            case PostfixDecrementOp op -> "--";
            case PrefixDecrementOp op -> "--U";
            default -> null;
        };
        if (operator == null) {
            String s = viewer.toString(unaryOp);
            result.addAll(tokenize(translator.prepareCode(s)));
            return;
        }
        TokenGroup op;
        OperatorToken token;
        if (unaryOp instanceof PostfixDecrementOp || unaryOp instanceof PostfixIncrementOp) {
            op = tokenizeExtended(unaryOp.getArgument(), result);
            token = getOperatorByTokenName(operator);
            result.add(token);
            op.setMetadata(token, OperandPosition.LEFT);
        } else {
            token = getOperatorByTokenName(operator);
            result.add(token);
            op = tokenizeExtended(unaryOp.getArgument(), result);
            op.setMetadata(token, OperandPosition.RIGHT);
        }
        if (unaryOp.getAssignedValueTag() != null) {
            token.assignValue(unaryOp.getAssignedValueTag());
            valueSetNodes.add(unaryOp.getId());
        }
    }

    private void tokenizeBinary(BinaryExpression binOp, TokenList result) {
        String operator = switch (binOp) {
            case AddOp op -> "+";
            case SubOp op -> "-";
            case MulOp op -> "*";
            case DivOp op -> "/";
            case LtOp op -> "<";
            case GtOp op -> ">";
            case NotEqOp op -> "!=";
            case GeOp op -> ">=";
            case LeOp op -> "<=";
            case ShortCircuitAndOp op -> "&&";
            case ShortCircuitOrOp op -> "||";
            case BitwiseAndOp op -> "&";
            case BitwiseOrOp op -> "|";
            case XorOp op -> "^";
            case LeftShiftOp op -> "<<";
            case RightShiftOp op -> ">>";
            case EqOp op -> "==";
            case ReferenceEqOp op -> op.isNegative() ? "!=" : "==";
            case ModOp op -> "%";
            case InstanceOfOp op -> "instanceof";
            default -> null;
        };
        if (binOp instanceof FloorDivOp) {
            tokenizeExtended(new CastTypeExpression(new IntType(64), new DivOp(binOp.getLeft(), binOp.getRight())), result);
            return;
        }
        if (binOp instanceof PowOp) {
            tokenizeExtended(new MethodCall(new SimpleIdentifier("Math"),
                    new SimpleIdentifier("pow"), binOp.getLeft(), binOp.getRight()), result);
            return;
        }
        if (binOp instanceof ContainsOp) {
            tokenizeExtended(new MethodCall(binOp.getRight(), new SimpleIdentifier("contains"), binOp.getLeft()), result);
            return;
        }
        if (operator == null) {
            String s = viewer.toString(binOp);
            result.addAll(tokenize(s));
            return;
        }
        TokenGroup left = tokenizeExtended(binOp.getLeft(), result);
        OperatorToken token = getOperatorByTokenName(operator);
        result.add(token);
        TokenGroup right = tokenizeExtended(binOp.getRight(), result);
        left.setMetadata(token, OperandPosition.LEFT);
        right.setMetadata(token, OperandPosition.RIGHT);
        if (binOp.getAssignedValueTag() != null) {
            token.assignValue(binOp.getAssignedValueTag());
            valueSetNodes.add(binOp.getId());
        }
    }

    private void tokenizeFieldOp(MemberAccess access, TokenList result) {
        TokenGroup base = tokenizeExtended(access.getExpression(), result);
        OperatorToken dot = getOperatorByTokenName(".");
        result.add(dot);
        TokenGroup member = tokenizeExtended(access.getMember(), result);
        base.setMetadata(dot, OperandPosition.LEFT);
        member.setMetadata(dot, OperandPosition.RIGHT);
        if (access.getAssignedValueTag() != null) {
            dot.assignValue(access.getAssignedValueTag());
            valueSetNodes.add(access.getId());
        }
    }


    private void tokenizeTernary(TernaryOperator ternary, TokenList result) {
        TokenGroup cond = tokenizeExtended(ternary.getCondition(), result);
        OperatorToken op1 = getOperatorByTokenName("?");
        cond.setMetadata(op1, OperandPosition.LEFT);
        result.add(op1);
        TokenGroup alt1 = tokenizeExtended(ternary.getThenExpr(), result);
        alt1.setMetadata(op1, OperandPosition.CENTER);
        OperatorToken op2 = getOperatorByTokenName(":");
        result.add(op2);
        TokenGroup alt2 = tokenizeExtended(ternary.getElseExpr(), result);
        alt2.setMetadata(op1, OperandPosition.RIGHT);
        if (ternary.getAssignedValueTag() != null) {
            op1.assignValue(ternary.getAssignedValueTag());
            valueSetNodes.add(ternary.getId());
        }
    }

    private void tokenizeCompoundComparison(CompoundComparison comparison, TokenList result) {
        if (comparison.getComparisons().size() == 1) {
            tokenizeExtended(comparison.getComparisons().getFirst(), result);
            return;
        }
        ShortCircuitAndOp op = new ShortCircuitAndOp(comparison.getComparisons().getFirst(),
                comparison.getComparisons().get(1));
        for (int i = 2; i < comparison.getComparisons().size(); i++) {
            op = new ShortCircuitAndOp(op, comparison.getComparisons().get(i));
        }
        if (comparison.getAssignedValueTag() != null) {
            op.setAssignedValueTag(comparison.getAssignedValueTag());
        }
        tokenizeExtended(op, result);
    }

    private void tokenizeSubscript(IndexExpression subscript, TokenList result) {
        TokenGroup leftOperand = tokenizeExtended(subscript.getExpression(), result);
        OperatorToken open = getOperatorByTokenName("[");
        result.add(open);
        leftOperand.setMetadata(open, OperandPosition.LEFT);
        TokenGroup centerOperand = tokenizeExtended(subscript.getIndex(), result);
        centerOperand.setMetadata(open, OperandPosition.CENTER);
        result.add(getOperatorByTokenName("]"));
        if (subscript.getAssignedValueTag() != null) {
            open.assignValue(subscript.getAssignedValueTag());
            valueSetNodes.add(subscript.getId());
        }
    }
}
