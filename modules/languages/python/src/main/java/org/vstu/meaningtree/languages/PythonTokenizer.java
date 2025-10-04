package org.vstu.meaningtree.languages;

import org.treesitter.TSNode;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.utils.PythonSpecificFeatures;
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
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;
import org.vstu.meaningtree.utils.tokens.*;

import java.util.*;

public class PythonTokenizer extends LanguageTokenizer {
    private static final List<String> stopNodes = List.of("string");
    private Set<Long> valueSetNodes = new HashSet<>();

    protected static final Map<String, OperatorToken> operators = new HashMap<>() {{
        List<OperatorToken> braces = OperatorToken.makeComplex(2,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"(", ")"},
                new TokenType[] {TokenType.CALL_OPENING_BRACE, TokenType.CALL_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("CALL_(", braces.getFirst());
        put("CALL_)", braces.getLast());

        List<OperatorToken> collection = OperatorToken.makeComplex(2,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"[", "]"},
                new TokenType[] {TokenType.INITIALIZER_LIST_OPENING_BRACE, TokenType.INITIALIZER_LIST_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("LIST_OPEN", collection.getFirst());
        put("LIST_CLOSE", collection.getLast());

        List<OperatorToken> subscript = OperatorToken.makeComplex(2,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"[", "]"},
                new TokenType[] {TokenType.SUBSCRIPT_OPENING_BRACE, TokenType.SUBSCRIPT_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("[", subscript.getFirst());
        put("]", subscript.getLast());

        put(".", new OperatorToken(".", TokenType.OPERATOR, 2, OperatorAssociativity.LEFT, OperatorArity.UNARY, false, OperatorTokenPosition.POSTFIX));
        put("await", new OperatorToken("await", TokenType.OPERATOR, 3, OperatorAssociativity.LEFT, OperatorArity.UNARY, false));
        put("**", new OperatorToken("**", TokenType.OPERATOR, 4, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false)); // Возведение в степень
        put("~", new OperatorToken("~", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.UNARY, false)); // Побитовая инверсия
        put("UPLUS", new OperatorToken("+", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.UNARY, false)); // Унарный плюс
        put("UMINUS", new OperatorToken("-", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.UNARY, false)); // Унарный минус
        put("*", new OperatorToken("*", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Умножение
        put("/", new OperatorToken("/", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Деление
        put("//", new OperatorToken("//", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Целочисленное деление
        put("%", new OperatorToken("%", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Остаток от деления
        put("@", new OperatorToken("@", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Матричное умножение
        put("+", new OperatorToken("+", TokenType.OPERATOR, 7, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Сложение
        put("-", new OperatorToken("-", TokenType.OPERATOR, 7, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Вычитание
        put("<<", new OperatorToken("<<", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Левый сдвиг
        put(">>", new OperatorToken(">>", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Правый сдвиг
        put("&", new OperatorToken("&", TokenType.OPERATOR, 9, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Побитовое И
        put("^", new OperatorToken("^", TokenType.OPERATOR, 10, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Побитовое исключающее ИЛИ
        put("|", new OperatorToken("|", TokenType.OPERATOR, 11, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Побитовое ИЛИ
        put("<", new OperatorToken("<", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Меньше
        put("<=", new OperatorToken("<=", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Меньше или равно
        put(">", new OperatorToken(">", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Больше
        put(">=", new OperatorToken(">=", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Больше или равно
        put("==", new OperatorToken("==", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Равно
        put("!=", new OperatorToken("!=", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Не равно
        put("in", new OperatorToken("in", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Оператор in
        put("is", new OperatorToken("is", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Оператор is
        put("not in", new OperatorToken("not in", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Оператор in
        put("is not", new OperatorToken("is not", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Оператор is
        put("not", new OperatorToken("not", TokenType.OPERATOR, 13, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false)); // Логическое НЕ
        put("and", new OperatorToken("and", TokenType.OPERATOR, 14, OperatorAssociativity.LEFT, OperatorArity.BINARY, true, OperatorTokenPosition.INFIX, OperatorType.AND)); // Логическое И
        put("or", new OperatorToken("or", TokenType.OPERATOR, 15, OperatorAssociativity.LEFT, OperatorArity.BINARY, true, OperatorTokenPosition.INFIX, OperatorType.OR)); // Логическое ИЛИ

        List<OperatorToken> ternary = OperatorToken.makeComplex(16, OperatorArity.TERNARY,
                OperatorAssociativity.RIGHT, true, new String[] {"if", "else"},
                new TokenType[] {TokenType.OPERATOR, TokenType.OPERATOR}
        );
        put("if", ternary.getFirst().setFirstOperandToEvaluation(OperandPosition.CENTER)); // Условные выражения
        put("else", ternary.getLast().setFirstOperandToEvaluation(OperandPosition.CENTER)); // Условные выражения
        get("if").additionalOpType = OperatorType.CONDITIONAL;
        get("else").additionalOpType = OperatorType.CONDITIONAL;

        put("lambda", new OperatorToken("lambda", TokenType.KEYWORD, 17, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false)); // Лямбда-выражения
        put(":=", new OperatorToken(":=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // Моржовый оператор
        put("=", new OperatorToken("=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false)); // сделано ради CompPrehension
        put("+=", new OperatorToken("+=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("-=", new OperatorToken("-=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("*=", new OperatorToken("*=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("/=", new OperatorToken("/=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("//=", new OperatorToken("//=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("@=", new OperatorToken("@=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("%=", new OperatorToken("%=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("**=", new OperatorToken("**=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put(">>=", new OperatorToken(">>=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("<<=", new OperatorToken("<<=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("&=", new OperatorToken("&=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("^=", new OperatorToken("^=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("|=", new OperatorToken("|=", TokenType.OPERATOR, 18, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
    }};


    public PythonTokenizer(PythonTranslator translator) {
        super(translator);
    }

    @Override
    protected List<String> getStopNodes() {
        return stopNodes;
    }

    @Override
    protected OperatorToken getOperator(String tokenValue, TSNode node) {
        if (!node.getParent().isNull() && node.getParent().getType().equals("unary_operator") && List.of("+", "-").contains(tokenValue)) {
            if (tokenValue.equals("+")) {
                return operators.get("UPLUS").clone();
            } else if (tokenValue.equals("-")) {
                return operators.get("UMINUS").clone();
            }
        }
        if (!node.getParent().isNull() && tokenValue.equals("(") && !node.getParent().isNull() && node.getParent().getType().equals("call")) {
            return operators.get("CALL_(").clone();
        }

        if (!node.getParent().isNull() && tokenValue.equals(")") && !node.getParent().isNull() && node.getParent().getType().equals("call")) {
            return operators.get("CALL_)").clone();
        }
        OperatorToken tok = operators.getOrDefault(tokenValue, null);
        return tok == null ? null : tok.clone();
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
    protected Token recognizeToken(TSNode node) {
        String type = node.getType();
        TSNode parent = node.getParent();
        OperatorToken recognizedOperator = getOperator(type, node);

        TokenType tokenType;

        if (recognizedOperator != null
                && !((type.equals("[") || type.equals("]")) && parent.getType().equals("list"))) {
            return recognizedOperator;
        }

        if (type.equals("{") && List.of("set", "dictionary").contains(parent.getType())) {
            tokenType = TokenType.INITIALIZER_LIST_OPENING_BRACE;
        } else if (type.equals("}") && List.of("set", "dictionary").contains(parent.getType())) {
            tokenType = TokenType.INITIALIZER_LIST_CLOSING_BRACE;
        } else if (type.equals("(") && parent.getType().equals("tuple")) {
            tokenType = TokenType.INITIALIZER_LIST_OPENING_BRACE;
        } else if (type.equals(")") && parent.getType().equals("tuple")) {
            tokenType = TokenType.INITIALIZER_LIST_CLOSING_BRACE;
        } else if (type.equals("(")) {
            tokenType = TokenType.OPENING_BRACE;
        } else if (type.equals(")")) {
            tokenType = TokenType.CLOSING_BRACE;
        } else if (type.equals("comment")) {
            tokenType = TokenType.COMMENT;
        } else if (List.of(";", ":").contains(type)) {
            tokenType = TokenType.SEPARATOR;
        } else if (List.of("type_identifier", "identifier",
                "integral_type", "boolean_type").contains(type)) {
            if (!parent.isNull() && parent.getType().equals("call")) {
                tokenType = TokenType.CALLABLE_IDENTIFIER;
            } else {
                tokenType = TokenType.IDENTIFIER;
            }
        } else if (List.of("integer", "float",
                "string", "true", "false", "none").contains(type)) {
            tokenType = TokenType.CONST;
        } else if (type.equals(",")) {
            tokenType = TokenType.COMMA;
        } else if (List.of("lambda", "if", "else").contains(type)) {
            tokenType = TokenType.KEYWORD;
        } else if (List.of("=", "+=", "-=", "*=", "/=", "//=", "%=", "&=", "|=", "^=", ">>=", "<<=").contains(type)) {
            tokenType = TokenType.STATEMENT_TOKEN;
        } else if (type.equals("[") && parent.getType().equals("list")) {
            tokenType = TokenType.INITIALIZER_LIST_OPENING_BRACE;
        } else if (type.equals("]") && parent.getType().equals("list")) {
            tokenType = TokenType.INITIALIZER_LIST_CLOSING_BRACE;
        } else if (type.equals(TreeSitterUtils.getCodePiece(code, node))) {
            tokenType = TokenType.KEYWORD;
        } else {
            tokenType = TokenType.UNKNOWN;
        }
        return new Token(TreeSitterUtils.getCodePiece(code, node), tokenType);
    }

    @Override
    protected List<String> getOperatorNodes(OperatorArity arity) {
        return switch (arity) {
            case UNARY -> List.of("unary_operator", "not_operator");
            case BINARY -> List.of("binary_operator", "attribute", "named_expression", "boolean_operator", "subscript", "call");
            case TERNARY -> List.of("conditional_expression");
        };
    }

    @Override
    protected String getFieldNameByOperandPos(OperandPosition pos, String operatorNode) {
        if (List.of("unary_operator", "not_operator").contains(operatorNode)) {
            return "argument";
        } else if (List.of("binary_operator", "boolean_operator").contains(operatorNode)) {
            if (pos == OperandPosition.LEFT) {
                return "left";
            } else if (pos == OperandPosition.RIGHT) {
                return "right";
            }
        } else if (operatorNode.equals("attribute")) {
            if (pos == OperandPosition.LEFT) {
                return "object";
            } else if (pos == OperandPosition.RIGHT) {
                return "attribute";
            }
        } else if (operatorNode.equals("subscript")) {
            if (pos == OperandPosition.LEFT) {
                return "value";
            } else if (pos == OperandPosition.CENTER) {
                return "subscript";
            }
        } else if (operatorNode.equals("call")) {
            if (pos == OperandPosition.LEFT) {
                return "function";
            } else if (pos == OperandPosition.CENTER) {
                return "arguments";
            }
        } else if (operatorNode.equals("conditional_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "_0";
            } else if (pos == OperandPosition.CENTER) {
                return "_1";
            } else if (pos == OperandPosition.RIGHT) {
                return "_2";
            }
        } else if (operatorNode.equals("named_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "name";
            } else if (pos == OperandPosition.CENTER) {
                return "value";
            }
        }
        return null;
    }

    @Override
    public TokenList tokenizeExtended(Node node) {
        TokenList result = new TokenList();
        if (node instanceof AssignmentExpression assign) {
            node = assign.toStatement();
        }
        tokenizeExtended(node, result);
        return result;
    }

    public TokenGroup tokenizeExtended(Node node, TokenList result) {
        if (node == null) {
            throw new MeaningTreeException("Null node passed");
        }
        if (node.hasLabel(Label.DUMMY)) {
            return new TokenGroup(0, 0, result);
        }
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
        } else if (node instanceof FunctionCall expr) {
            node = this.viewer.parenFiller.processForPython(expr);
        }
        int posStart = result.size();
        switch (node) {
            case InstanceOfOp ins -> tokenizeInstanceOf(ins, result);
            case AssignmentExpression assignment -> {
                if (!(assignment.getLValue() instanceof SimpleIdentifier) || assignment.getRValue() instanceof AssignmentExpression) {
                    throw new UnsupportedViewingException("Assignment expression in Python supports only identifiers");
                }
                TokenGroup grp1 = tokenizeExtended(assignment.getLValue(), result);
                OperatorToken tok = getOperatorByTokenName(":=");
                result.add(tok);
                TokenGroup grp2;
                if (assignment.getRValue() instanceof AssignmentExpression assign) {
                    grp2 = tokenizeExtended(assign.toStatement(), result);
                } else {
                    grp2 = tokenizeExtended(assignment.getRValue(), result);
                }
                grp1.setMetadata(tok, OperandPosition.LEFT);
                grp2.setMetadata(tok, OperandPosition.RIGHT);
                if (assignment.getAssignedValueTag() != null) {
                    tok.assignValue(assignment.getAssignedValueTag());
                    valueSetNodes.add(assignment.getId());
                }
            }
            case BinaryExpression binOp -> tokenizeBinary(binOp, result);
            case PointerPackOp packOp -> tokenizeExtended(packOp.getArgument(), result);
            case PointerUnpackOp unpackOp -> tokenizeExtended(unpackOp.getArgument(), result);
            case UnaryExpression unaryOp -> tokenizeUnary(unaryOp, result);
            case SizeofExpression ignored -> throw new UnsupportedViewingException("Sizeof is disabled in this language");
            case FunctionCall call -> tokenizeCall(call, result);
            case ObjectNewExpression newExpr -> tokenizeNewExpr(newExpr, result);
            case MemberAccess access -> tokenizeFieldOp(access, result);
            case CompoundComparison comparison -> tokenizeCompoundComparison(comparison, result);
            case IndexExpression subscript -> tokenizeSubscript(subscript, result);
            case PlainCollectionLiteral plain -> tokenizePlainCollectionLiteral(plain, result);
            case DictionaryLiteral dct -> tokenizeDictCollectionLiteral(dct, result);
            case Range range -> {
                if (range.getStart() != null) tokenizeExtended(range.getStart(), result);
                result.add(new Token(":", TokenType.SEPARATOR));
                if (range.getStop() != null) tokenizeExtended(range.getStop(), result);
                result.add(new Token(":", TokenType.SEPARATOR));
                if (range.getStep() != null) tokenizeExtended(range.getStep(), result);
            }
            case TernaryOperator ternary -> tokenizeTernary(ternary, result);
            case DefinitionArgument arg -> {
                if (arg.isDictUnpacking()) {
                    result.add(new Token("**", TokenType.SEPARATOR));
                    tokenizeExtended(arg.getInitialExpression(), result);
                } else if (arg.isListUnpacking()) {
                    result.add(new Token("*", TokenType.SEPARATOR));
                    tokenizeExtended(arg.getInitialExpression(), result);
                } else if (arg.hasVisibleName()) {
                    tokenizeExtended(arg.getName(), result);
                    result.add(new Token("=", TokenType.SEPARATOR));
                    tokenizeExtended(arg.getInitialExpression(), result);
                } else {
                    tokenizeExtended(arg.getInitialExpression(), result);
                }
            }
            case SimpleIdentifier ident -> {
                result.add(new Token(ident.getName(), TokenType.IDENTIFIER));
            }
            case QualifiedIdentifier ident -> {
                tokenizeExtended(ident.getScope(), result);
                result.add(getOperatorByTokenName("."));
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
                TokenGroup grp1 = tokenizeExtended(assignment.getLValue(), result);
                String token = assignment.getAugmentedOperator().getValue();
                OperatorToken tok = getOperatorByTokenName(token);
                result.add(tok);
                TokenGroup grp2;
                if (assignment.getRValue() instanceof AssignmentExpression assign) {
                    grp2 = tokenizeExtended(assign.toStatement(), result);
                } else {
                    grp2 = tokenizeExtended(assignment.getRValue(), result);
                }
                grp1.setMetadata(tok, OperandPosition.LEFT);
                grp2.setMetadata(tok, OperandPosition.RIGHT);
                if (assignment.getAssignedValueTag() != null) {
                    tok.assignValue(assignment.getAssignedValueTag());
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
            }
            case CastTypeExpression cast -> {
                tokenizeExtended(new ObjectNewExpression(cast.getCastType(), cast.getValue()), result);
            }
            default ->  {
                String s = viewer.toString(node);
                result.addAll(tokenize(s));
            }
        }
        int posStop = result.size();
        TokenGroup resultGroup =  new TokenGroup(posStart, posStop, result);
        if (!(node instanceof ParenthesizedExpression) && node.getAssignedValueTag() != null && !valueSetNodes.contains(node.getId())) {
            resultGroup.assignValue(node.getAssignedValueTag());
            valueSetNodes.add(node.getId());
        }
        return resultGroup;
    }

    private void tokenizePlainCollectionLiteral(PlainCollectionLiteral literal, TokenList result) {
        String[] tokens = switch (literal) {
            case SetLiteral ignored -> new String[] {"{", "}"};
            case UnmodifiableListLiteral ignored -> new String[] {"(", ")"};
            default -> new String[] {"[", "]"};
        };
        OperatorToken tok = getOperatorByTokenName("LIST_OPEN").clone(tokens[0]);
        result.add(tok);
        for (Expression item : literal.getList()) {
            TokenGroup grp = tokenizeExtended(item, result);
            grp.setMetadata(tok, OperandPosition.CENTER);
            result.add(new Token(",", TokenType.COMMA).setOwner(tok));
        }
        if (literal.getList().isEmpty() && literal instanceof UnmodifiableListLiteral) {
            result.add(new Token(",", TokenType.COMMA).setOwner(tok));
        }
        if (!literal.getList().isEmpty() && !(literal instanceof UnmodifiableListLiteral)) result.removeLast();
        result.add(getOperatorByTokenName("LIST_CLOSE").clone(tokens[1]));
        if (literal.getAssignedValueTag() != null) {
            tok.assignValue(literal.getAssignedValueTag());
            valueSetNodes.add(literal.getId());
        }
    }

    private void tokenizeDictCollectionLiteral(DictionaryLiteral literal, TokenList result) {
        OperatorToken tok = getOperatorByTokenName("LIST_OPEN").clone("{");
        for (Map.Entry<Expression, Expression> item : literal.getDictionary().entrySet()) {
            TokenGroup grp = tokenizeExtended(item.getKey(), result);
            grp.setMetadata(tok, OperandPosition.CENTER);
            result.add(new Token(":", TokenType.SEPARATOR).setOwner(tok));
            grp = tokenizeExtended(item.getValue(), result);
            grp.setMetadata(tok, OperandPosition.CENTER);
            result.add(new Token(",", TokenType.COMMA).setOwner(tok));
        }
        if (!literal.getDictionary().isEmpty()) result.removeLast();
        result.add(getOperatorByTokenName("LIST_CLOSE").clone("}"));
        if (literal.getAssignedValueTag() != null) {
            tok.assignValue(literal.getAssignedValueTag());
            valueSetNodes.add(literal.getId());
        }
    }


    private void tokenizeNewExpr(ObjectNewExpression newExpr, TokenList result) {
        TokenGroup complexName = tokenizeExtended(newExpr.getType(), result);
        OperatorToken tok = getOperatorByTokenName("CALL_(");
        if (complexName != null) complexName.setMetadata(tok, OperandPosition.LEFT);
        result.add(tok);
        for (Expression expr : newExpr.getConstructorArguments()) {
            TokenGroup operand = tokenizeExtended(expr, result);
            result.add(new Token(",", TokenType.COMMA).setOwner(tok));
            operand.setMetadata(tok, OperandPosition.CENTER);
        }
        if (!newExpr.getConstructorArguments().isEmpty()) result.removeLast();
        result.add(getOperatorByTokenName("CALL_)"));
        if (newExpr.getAssignedValueTag() != null) {
            tok.assignValue(newExpr.getAssignedValueTag());
            valueSetNodes.add(newExpr.getId());
        }
    }

    private void tokenizeInstanceOf(InstanceOfOp ins, TokenList result) {
        tokenizeCall(new FunctionCall(new SimpleIdentifier("isinstance"), ins.getLeft(), ins.getRight()), result);
    }

    private void tokenizeCall(FunctionCall call, TokenList result) {
        TokenGroup complexName;
        if (call instanceof MethodCall method) {
            complexName = tokenizeExtended(new MemberAccess(method.getObject(), (SimpleIdentifier) method.getFunction()), result);
        } else {
            complexName = tokenizeExtended(PythonSpecificFeatures.getFunctionExpression(call), result);
        }
        OperatorToken tok = getOperatorByTokenName("CALL_(");
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
            case NotOp op -> "not";
            case InversionOp op -> "~";
            case UnaryMinusOp op -> "UMINUS";
            case UnaryPlusOp op -> "UPLUS";
            default -> null;
        };

        switch (unaryOp) {
            case PrefixDecrementOp prefixDecrementOp -> {
                tokenizeExtended(new AssignmentExpression(unaryOp.getArgument(), new SubOp(unaryOp.getArgument(), new IntegerLiteral(1))), result);
                return;
            }
            case PostfixDecrementOp postfixDecrementOp -> {
                tokenizeExtended(new AssignmentExpression(unaryOp.getArgument(), new SubOp(unaryOp.getArgument(), new IntegerLiteral(1))), result);
                return;
            }
            case PrefixIncrementOp prefixIncrementOp -> {
                tokenizeExtended(new AssignmentExpression(unaryOp.getArgument(), new AddOp(unaryOp.getArgument(), new IntegerLiteral(1))), result);
                return;
            }
            case PostfixIncrementOp postfixIncrementOp -> {
                tokenizeExtended(new AssignmentExpression(unaryOp.getArgument(), new AddOp(unaryOp.getArgument(), new IntegerLiteral(1))), result);
                return;
            }
            default -> {
            }
        }

        if (operator == null) {
            String s = viewer.toString(unaryOp);
            result.addAll(tokenize(s));
            return;
        }
        TokenGroup op;
        OperatorToken token = getOperatorByTokenName(operator);
        result.add(token);
        op = tokenizeExtended(unaryOp.getArgument(), result);
        op.setMetadata(token, OperandPosition.RIGHT);
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
            case NotEqOp op -> {
                if (op.getRight() instanceof NullLiteral) {
                    yield "is not";
                }
                yield "!=";
            }
            case GeOp op -> ">=";
            case PowOp op -> "**";
            case LeOp op -> "<=";
            case ShortCircuitAndOp op -> "and";
            case ShortCircuitOrOp op -> "or";
            case ContainsOp op -> op.isNegative() ? "not in" : "in";
            case BitwiseAndOp op -> "&";
            case BitwiseOrOp op -> "|";
            case XorOp op -> "^";
            case LeftShiftOp op -> "<<";
            case RightShiftOp op -> ">>";
            case EqOp op -> {
                if (op.getRight() instanceof NullLiteral) {
                    yield "is";
                }
                yield "==";
            }
            case ModOp op -> "%";
            case MatMulOp op -> "@";
            case ReferenceEqOp op -> op.isNegative() ? "is" : "is not";
            default -> null;
        };
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
        TokenGroup alt1 = tokenizeExtended(ternary.getThenExpr(), result);
        OperatorToken op1 = getOperatorByTokenName("if");
        alt1.setMetadata(op1, OperandPosition.LEFT);
        result.add(op1);
        TokenGroup cond = tokenizeExtended(ternary.getCondition(), result);
        cond.setMetadata(op1, OperandPosition.CENTER);
        OperatorToken op2 = getOperatorByTokenName("else");
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
        for (int i = 0; i < comparison.getComparisons().size(); i++) {
            TokenGroup operand = tokenizeExtended(comparison.getComparisons().get(i).getLeft(), result);
            String operator = switch (comparison.getComparisons().get(i)) {
                case LtOp op -> "<";
                case GtOp op -> ">";
                case NotEqOp op -> "!=";
                case GeOp op -> ">=";
                case EqOp op -> "==";
                case ContainsOp op -> "in";
                case LeOp op -> "<=";
                default -> throw new IllegalStateException("Unexpected value: " + comparison.getComparisons().get(i));
            };
            OperatorToken token = getOperatorByTokenName(operator);
            if (comparison.getAssignedValueTag() != null) {
                token.assignValue(comparison.getAssignedValueTag());
                valueSetNodes.add(comparison.getId());
            }
            result.add(token);
            if (i == 0) {
                operand.setMetadata(token, OperandPosition.LEFT);
            } else {
                operand.setMetadata(token, OperandPosition.CENTER);
            }
            if (comparison.getComparisons().get(i).getAssignedValueTag() != null) {
                token.assignValue(comparison.getComparisons().get(i).getAssignedValueTag());
                valueSetNodes.add(comparison.getComparisons().get(i).getId());
            }
            if (i == comparison.getComparisons().size() - 1) {
                TokenGroup lastOperand = tokenizeExtended(comparison.getComparisons().get(i).getRight(), result);
                lastOperand.setMetadata(token, OperandPosition.RIGHT);
            }
        }
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
