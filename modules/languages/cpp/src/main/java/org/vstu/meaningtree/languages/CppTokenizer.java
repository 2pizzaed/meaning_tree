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
import org.vstu.meaningtree.nodes.expressions.literals.DictionaryLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.PlainCollectionLiteral;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.NewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerMemberAccess;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.io.FormatInput;
import org.vstu.meaningtree.nodes.io.FormatPrint;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintCommand;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;
import org.vstu.meaningtree.utils.tokens.*;

import java.util.*;
import java.util.stream.Collectors;

public class CppTokenizer extends LanguageTokenizer {
    private static final List<String> stopNodes = List.of("user_defined_literal", "char_literal", "string_literal");
    private Set<Long> valueSetNodes = new HashSet<>();

    protected static final Map<String, OperatorToken> operators = new HashMap<>() {{
        put("::", new OperatorToken("::", TokenType.OPERATOR, 1, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        List<OperatorToken> braces = OperatorToken.makeComplex(2,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"(", ")"},
                new TokenType[] {TokenType.CALL_OPENING_BRACE, TokenType.CALL_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("CALL_(", braces.getFirst());
        put("CALL_)", braces.getLast());

        List<OperatorToken> collection = OperatorToken.makeComplex(2,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"{", "}"},
                new TokenType[] {TokenType.INITIALIZER_LIST_OPENING_BRACE, TokenType.INITIALIZER_LIST_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("{", collection.getFirst());
        put("}", collection.getLast());

        List<OperatorToken> subscript = OperatorToken.makeComplex(2,
                OperatorArity.BINARY, OperatorAssociativity.LEFT, false,
                new String[] {"[", "]"},
                new TokenType[] {TokenType.SUBSCRIPT_OPENING_BRACE, TokenType.SUBSCRIPT_CLOSING_BRACE},
                new OperatorTokenPosition[]{OperatorTokenPosition.AROUND, OperatorTokenPosition.AROUND});

        put("[", subscript.getFirst());
        put("]", subscript.getLast());

        put("->", new OperatorToken("->", TokenType.OPERATOR, 2, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put(".", new OperatorToken(".", TokenType.OPERATOR, 2, OperatorAssociativity.LEFT, OperatorArity.UNARY, false, OperatorTokenPosition.POSTFIX));
        put("++", new OperatorToken("++", TokenType.OPERATOR, 2, OperatorAssociativity.LEFT, OperatorArity.UNARY, false).setFirstOperandToEvaluation(OperandPosition.LEFT));
        put("--", new OperatorToken("--", TokenType.OPERATOR, 2, OperatorAssociativity.LEFT, OperatorArity.UNARY, false).setFirstOperandToEvaluation(OperandPosition.LEFT));
        put("CAST", new OperatorToken("CAST", TokenType.CAST, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("++U", new OperatorToken("++", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("--U", new OperatorToken("--", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("UMINUS", new OperatorToken("-", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("UPLUS", new OperatorToken("+", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("POINTER_&", new OperatorToken("&", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("POINTER_*", new OperatorToken("*", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("~", new OperatorToken("~", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("!", new OperatorToken("!", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("sizeof", new OperatorToken("sizeof", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        put("new", new OperatorToken("new", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));
        get("new").additionalOpType = OperatorType.NEW;
        put("delete", new OperatorToken("delete", TokenType.OPERATOR, 3, OperatorAssociativity.RIGHT, OperatorArity.UNARY, false));

        put("*", new OperatorToken("*", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("/", new OperatorToken("/", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("%", new OperatorToken("%", TokenType.OPERATOR, 5, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("+", new OperatorToken("+", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("-", new OperatorToken("-", TokenType.OPERATOR, 6, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("<<", new OperatorToken("<<", TokenType.OPERATOR, 7, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put(">>", new OperatorToken(">>", TokenType.OPERATOR, 7, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("<=>", new OperatorToken("<=>", TokenType.OPERATOR, 8, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("<", new OperatorToken("<", TokenType.OPERATOR, 9, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("<=", new OperatorToken("<=", TokenType.OPERATOR, 9, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put(">", new OperatorToken(">", TokenType.OPERATOR, 9, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put(">=", new OperatorToken(">=", TokenType.OPERATOR, 9, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("==", new OperatorToken("==", TokenType.OPERATOR, 10, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));
        put("!=", new OperatorToken("!=", TokenType.OPERATOR, 10, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("&", new OperatorToken("&", TokenType.OPERATOR, 11, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("^", new OperatorToken("^", TokenType.OPERATOR, 12, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("|", new OperatorToken("|", TokenType.OPERATOR, 13, OperatorAssociativity.LEFT, OperatorArity.BINARY, false));

        put("&&", new OperatorToken("&&", TokenType.OPERATOR, 14, OperatorAssociativity.LEFT, OperatorArity.BINARY, true, OperatorTokenPosition.INFIX, OperatorType.AND));

        put("||", new OperatorToken("||", TokenType.OPERATOR, 15, OperatorAssociativity.LEFT, OperatorArity.BINARY, true, OperatorTokenPosition.INFIX, OperatorType.OR));

        List<OperatorToken> ternary = OperatorToken.makeComplex(16, OperatorArity.TERNARY,
                OperatorAssociativity.RIGHT, true, new String[] {"?", ":"},
                new TokenType[] {TokenType.OPERATOR, TokenType.OPERATOR}
        );
        put("?", ternary.getFirst().setFirstOperandToEvaluation(OperandPosition.LEFT));  // Тернарный оператор
        put(":", ternary.getLast().setFirstOperandToEvaluation(OperandPosition.LEFT));
        get("?").additionalOpType = OperatorType.CONDITIONAL;
        get(":").additionalOpType = OperatorType.CONDITIONAL;

        put("=", new OperatorToken("=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("+=", new OperatorToken("+=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("-=", new OperatorToken("-=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("*=", new OperatorToken("*=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("/=", new OperatorToken("/=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("%=", new OperatorToken("%=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("<<=", new OperatorToken("<<=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put(">>=", new OperatorToken(">>=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("&=", new OperatorToken("&=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("^=", new OperatorToken("^=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));
        put("|=", new OperatorToken("|=", TokenType.OPERATOR, 16, OperatorAssociativity.RIGHT, OperatorArity.BINARY, false));

        put(",", new OperatorToken(",", TokenType.COMMA, 17, OperatorAssociativity.LEFT, OperatorArity.BINARY, true));
    }};


    public CppTokenizer(CppTranslator translator) {
        super(translator, List.of(
                "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor", "bool", "break",
                "case", "catch", "char", "char8_t", "char16_t", "char32_t", "class", "compl", "concept", "const",
                "consteval", "constexpr", "constinit", "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype", "default",
                "delete", "do", "double", "dynamic_cast", "else", "enum", "explicit", "export", "extern", "false",
                "float", "for", "friend", "goto", "if", "inline", "int", "long", "mutable", "namespace",
                "new", "noexcept", "not", "not_eq", "nullptr", "operator", "or", "or_eq", "private", "protected",
                "public", "register", "reinterpret_cast", "requires", "return", "short", "signed", "sizeof", "static", "static_assert",
                "static_cast", "struct", "switch", "template", "this", "thread_local", "throw", "true", "try", "typedef",
                "typeid", "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while",
                "xor", "xor_eq"
        ));
    }

    @Override
    protected List<String> getStopNodes() {
        return stopNodes;
    }

    @Override
    protected List<String> getOperatorNodes(OperatorArity arity) {
        return switch (arity) {
            case UNARY -> List.of(
                    "unary_expression", "update_expression", "pointer_expression", "assignment_expression"
            );
            case BINARY -> List.of("binary_expression", "field_expression", "cast_expression", "call_expression", "qualified_identifier");
            case TERNARY -> List.of("conditional_expression");
        };
    }

    @Override
    protected String getFieldNameByOperandPos(OperandPosition pos, String operatorNode) {
        if (List.of("unary_expression", "update_expression", "pointer_expression", "assignment_expression").contains(operatorNode)) {
            return "argument";
        } else if (operatorNode.equals("binary_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "left";
            } else if (pos == OperandPosition.RIGHT) {
                return "right";
            }
        } else if (operatorNode.equals("field_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "argument";
            } else if (pos == OperandPosition.RIGHT) {
                return "field";
            }
        } else if (operatorNode.equals("cast_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "type";
            } else if (pos == OperandPosition.RIGHT) {
                return "value";
            }
        } else if (operatorNode.equals("conditional_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "condition";
            } else if (pos == OperandPosition.CENTER) {
                return "consequence";
            } else if (pos == OperandPosition.RIGHT) {
                return "alternative";
            }
        } else if (operatorNode.equals("call_expression")) {
            if (pos == OperandPosition.LEFT) {
                return "function.argument";
            } else if (pos == OperandPosition.CENTER) {
                return "arguments";
            }
        } else if (operatorNode.equals("qualified_identifier")) {
            if (pos == OperandPosition.LEFT) {
                return "scope";
            } else if (pos == OperandPosition.RIGHT) {
                return "name";
            }
        }
        return null;
    }

    protected OperatorToken getOperator(String tokenValue, TSNode node) {
        if (!node.getParent().isNull() && node.getParent().getType().equals("unary_expression") && List.of("+", "-").contains(tokenValue)) {
            if (tokenValue.equals("+")) {
                return operators.get("UPLUS").clone();
            } else if (tokenValue.equals("-")) {
                return operators.get("UMINUS").clone();
            }
        }

        if (!node.getParent().isNull() && tokenValue.equals("(") && !node.getParent().getParent().isNull() && node.getParent().getParent().getType().equals("call_expression")) {
            return operators.get("CALL_(").clone();
        }

        if (!node.getParent().isNull() && tokenValue.equals(")") && !node.getParent().getParent().isNull() && node.getParent().getParent().getType().equals("call_expression")) {
            return operators.get("CALL_)").clone();
        }

        if (!node.getParent().isNull() && node.getParent().getType().equals("update_expression") &&
                TreeSitterUtils.getCodePiece(code, node.getParent()).startsWith("++") && tokenValue.equals("++")) {
            return operators.get("++U").clone();
        }

        if (!node.getParent().isNull() && node.getParent().getType().equals("update_expression") &&
                TreeSitterUtils.getCodePiece(code, node.getParent()).startsWith("--") && tokenValue.equals("--")) {
            return operators.get("--U").clone();
        }

        if (!node.getParent().isNull() && node.getParent().getType().equals("pointer_expression") && List.of("*", "&").contains(tokenValue)) {
            if (tokenValue.equals("*")) {
                return operators.get("POINTER_*").clone();
            } else if (tokenValue.equals("&")) {
                return operators.get("POINTER_&").clone();
            }
        }

        if (tokenValue.equals("{") || tokenValue.equals("}")) {
            return null;
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

        if (recognizedOperator != null) {
            return recognizedOperator;
        }

        TokenType tokenType;

        if (type.equals("{")) {
            if (parent.getType().equals("initializer_list")) {
                tokenType = TokenType.INITIALIZER_LIST_OPENING_BRACE;
            } else {
                tokenType = TokenType.COMPOUND_OPENING_BRACE;
            }
        } else if (type.equals("}")) {
            if (parent.getType().equals("initializer_list")) {
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
        } else if (type.equals(",")) {
            tokenType = TokenType.COMMA;
        } else if (List.of("identifier", "namespace_identifier", "type_identifier", "field_identifier").contains(type)) {
            if (!parent.isNull() && parent.getType().equals("call_expression")) {
                tokenType = TokenType.CALLABLE_IDENTIFIER;
            } else {
                tokenType = TokenType.IDENTIFIER;
            }
        } else if (List.of("number_literal", "char_literal", "string_literal", "true", "false", "null", "user_defined_literal").contains(type)) {
            tokenType = TokenType.CONST;
        } else if (type.equals("comment")) {
            tokenType = TokenType.COMMENT;
        } else if (type.equals(TreeSitterUtils.getCodePiece(code, node))) {
            tokenType = TokenType.KEYWORD;
        } else {
            tokenType = TokenType.UNKNOWN;
        }
        var tok = new Token(TreeSitterUtils.getCodePiece(code, node), tokenType);
        return tok;
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
        }  else if (node instanceof IndexExpression expr) {
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
            case UnaryExpression unaryOp -> tokenizeUnary(unaryOp, result);
            case FunctionCall call -> tokenizeCall(call, result);
            case SizeofExpression sizeOf -> {
                OperatorToken op = getOperatorByTokenName("sizeof");
                result.add(op);
                TokenGroup grp = tokenizeExtended(new ParenthesizedExpression(sizeOf.getExpression()), result);
                grp.setMetadata(op, OperandPosition.RIGHT);
            }
            case DefinitionArgument arg -> tokenizeExtended(arg.getInitialExpression(), result);
            case MemberAccess access -> tokenizeFieldOp(access, result);
            case CompoundComparison comparison -> tokenizeCompoundComparison(comparison, result);
            case IndexExpression subscript -> tokenizeSubscript(subscript, result);
            case TernaryOperator ternary -> tokenizeTernary(ternary, result);
            case NewExpression newExpr -> tokenizeNew(newExpr, result);
            case CastTypeExpression castType -> tokenizeCast(castType, result);
            case PlainCollectionLiteral plain -> tokenizePlainCollectionLiteral(plain, result);
            case PointerType type -> {
                tokenizeExtended(type.getTargetType(), result);
                result.add(new Token("*", TokenType.KEYWORD));
            }
            case DictionaryLiteral dict -> tokenizeDictCollectionLiteral(dict, result);
            case SimpleIdentifier ident -> {
                result.add(new Token(ident.getName(), TokenType.IDENTIFIER));
            }
            case QualifiedIdentifier ident -> {
                OperatorToken op = getOperatorByTokenName("::");
                TokenGroup op1 = tokenizeExtended(ident.getScope(), result);
                op1.setMetadata(op, OperandPosition.LEFT);
                result.add(op);
                TokenGroup op2 = tokenizeExtended(ident.getMember(), result);
                op2.setMetadata(op, OperandPosition.RIGHT);
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
            case ExpressionSequence sequence -> {
                List<Expression> exprs = sequence.getExpressions();
                if (!sequence.getExpressions().isEmpty()) {
                    if (sequence.getExpressions().size() == 1) {
                        tokenizeExtended(sequence.getExpressions().getFirst(), result);
                    } else {
                        int i;
                        OperatorToken op;
                        TokenGroup op1 = tokenizeExtended(exprs.getFirst(), result);
                        op = getOperatorByTokenName(",");
                        if (sequence.getAssignedValueTag() != null) {
                            op.assignValue(sequence.getAssignedValueTag());
                            valueSetNodes.add(sequence.getId());
                        }
                        result.add(op);
                        TokenGroup op2 = tokenizeExtended(exprs.get(1), result);
                        op1.setMetadata(op, OperandPosition.LEFT);
                        op2.setMetadata(op, OperandPosition.RIGHT);
                        for (i = 2; i < exprs.size(); i++) {
                            OperatorToken newOp = getOperatorByTokenName(",");
                            result.add(newOp);
                            op2 = tokenizeExtended(exprs.get(i), result);
                            op2.setMetadata(newOp, OperandPosition.RIGHT);
                            op.setMetadata(newOp, OperandPosition.LEFT);
                            op = newOp;
                        }
                    }
                } else {
                    throw new UnsupportedViewingException("Unsupported expression sequence in index");
                }
            }
            case ExpressionStatement exprStmt -> {
                tokenizeExtended(exprStmt.getExpression(), result);
                result.add(new Token(";", TokenType.SEPARATOR));
            }
            default ->  {
                String s = viewer.toString(node);
                TokenList tokens = tokenize(s);
                result.addAll(tokens.subList(0, tokens.getLast().type == TokenType.SEPARATOR ? tokens.size() - 1 : tokens.size()));
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

    private void tokenizeCast(CastTypeExpression cast, TokenList result) {
        TokenList lst = tokenizeExtended(cast.getCastType());
        String typeName = lst.stream().map((Token t) -> t.value).collect(Collectors.joining(" "));
        OperatorToken op = getOperatorByTokenName("CAST").clone("(" + typeName + ")");
        result.add(op);
        TokenGroup arg = tokenizeExtended(cast.getValue(), result);
        arg.setMetadata(op, OperandPosition.RIGHT);
        if (cast.getAssignedValueTag() != null) {
            arg.assignValue(cast.getAssignedValueTag());
            valueSetNodes.add(cast.getId());
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

    private void tokenizeCall(FunctionCall call, TokenList result) {
        TokenGroup complexName = null;
        OperatorToken tok = getOperatorByTokenName("CALL_(");
        tok.additionalOpType = call instanceof MethodCall ? OperatorType.METHOD_CALL : OperatorType.OTHER;
        if (call instanceof MethodCall method) {
            complexName = tokenizeExtended(method.getObject(), result);
            tok = tok.clone("." + ((SimpleIdentifier)method.getFunctionName()).getName() + "(");
        } else {
            Expression finalExpr = detectIOCommand(call);
            if (finalExpr.uniquenessEquals(call) && !(call.getFunction() instanceof SimpleIdentifier)) {
                throw new UnsupportedParsingException("This language supports only simple identifier as function name");
            } else if (!finalExpr.uniquenessEquals(call)) {
                tokenizeExtended(finalExpr, result);
                return;
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

    private Expression detectIOCommand(FunctionCall call) {
        if (call instanceof FormatPrint fmt) {
            if (fmt.getArguments().isEmpty()) {
                return new FunctionCall(new SimpleIdentifier("printf"), fmt.getFormatString());
            }
            var list = new ArrayList<Expression>();
            list.add(fmt.getFormatString());
            list.addAll(fmt.getArguments());
            return new FunctionCall(new SimpleIdentifier("printf"), list.toArray(new Expression[0]));
        } else if (call instanceof FormatInput fmt) {
            if (fmt.getArguments().isEmpty()) {
                return new FunctionCall(new SimpleIdentifier("scanf"), fmt.getFormatString());
            }
            var list = new ArrayList<Expression>();
            list.add(fmt.getFormatString());
            list.addAll(fmt.getArguments());
            return new FunctionCall(new SimpleIdentifier("scanf"), list.toArray(new Expression[0]));
        } else if (call instanceof PrintCommand || call instanceof InputCommand) {
            ArrayList<Expression> args = new ArrayList<>();
            if (call instanceof InputCommand) {
                args.add(new QualifiedIdentifier(new SimpleIdentifier("std"), new SimpleIdentifier("cin")));
            } else {
                args.add(new QualifiedIdentifier(new SimpleIdentifier("std"), new SimpleIdentifier("cout")));
            }
            args.addAll(call.getArguments());
            return LeftShiftOp.fromManyOperands(args.toArray(new Expression[0]), 0, LeftShiftOp.class);
        } else if (call instanceof MemoryAllocationCall m) {
            return new FunctionCall(m.isClearAllocation() ? new SimpleIdentifier("calloc") : new SimpleIdentifier("malloc"), m.getArguments());
        } else if (call instanceof MemoryFreeCall m) {
            return new FunctionCall(new SimpleIdentifier("free"), m.getArguments());
        }
        return call;
    }

    private void tokenizePlainCollectionLiteral(PlainCollectionLiteral literal, TokenList result) {
        OperatorToken tok = getOperatorByTokenName("{");
        result.add(tok);
        for (Expression item : literal.getList()) {
            TokenGroup grp = tokenizeExtended(item, result);
            grp.setMetadata(tok, OperandPosition.CENTER);
            result.add(new Token(",", TokenType.COMMA).setOwner(tok));
        }
        if (!literal.getList().isEmpty()) result.removeLast();
        result.add(getOperatorByTokenName("}"));
        if (literal.getAssignedValueTag() != null) {
            tok.assignValue(literal.getAssignedValueTag());
            valueSetNodes.add(literal.getId());
        }
    }

    private void tokenizeDictCollectionLiteral(DictionaryLiteral literal, TokenList result) {
        OperatorToken tok = getOperatorByTokenName("{");
        for (Expression item : literal.asPairsListLiteral()) {
            tokenizeExtended(item, result);
            result.add(new Token(",", TokenType.COMMA).setOwner(tok));
        }
        if (!literal.getDictionary().isEmpty()) result.removeLast();
        result.add(getOperatorByTokenName("}"));
        if (literal.getAssignedValueTag() != null) {
            tok.assignValue(literal.getAssignedValueTag());
            valueSetNodes.add(literal.getId());
        }
    }

    private void tokenizeUnary(UnaryExpression unaryOp, TokenList result) {
        String operator = switch (unaryOp) {
            case NotOp op -> "!";
            case InversionOp op -> "~";
            case UnaryMinusOp op -> "UMINUS";
            case UnaryPlusOp op -> "UPLUS";
            case PostfixIncrementOp op -> "++";
            case PrefixIncrementOp op -> "++U";
            case PostfixDecrementOp op -> "--";
            case PrefixDecrementOp op -> "--U";
            case PointerPackOp op -> "POINTER_&";
            case PointerUnpackOp op -> "POINTER_*";
            default -> null;
        };
        if (operator == null) {
            String s = viewer.toString(unaryOp);
            result.addAll(tokenize(s));
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
                if (arrNew.getInitializer() == null) {
                    TokenList lst = tokenizeExtended(newExpr.getType());
                    String typeName = lst.stream().map((Token t) -> t.value).collect(Collectors.joining(" "));
                    OperatorToken newTok = getOperatorByTokenName("new").clone("new " + typeName + "[");
                    if (arrNew.getAssignedValueTag() != null) {
                        newTok.assignValue(arrNew.getAssignedValueTag());
                        valueSetNodes.add(arrNew.getId());
                    }
                    result.add(newTok);
                    for (int i = 0; i < arrNew.getShape().getDimensionCount(); i++) {
                        if (arrNew.getShape().getDimension(i) != null) {
                            TokenGroup operand = tokenizeExtended(arrNew.getShape().getDimension(i), result);
                            operand.setMetadata(newTok, OperandPosition.CENTER);
                        }
                        result.add(new Token("][", TokenType.SEPARATOR));
                    }
                    result.add(new Token("]", TokenType.SUBSCRIPT_CLOSING_BRACE));
                }
                if (arrNew.getInitializer() != null) {
                    OperatorToken tok = getOperatorByTokenName("{");
                    result.add(tok);
                    for (Expression expr : arrNew.getInitializer().getValues()) {
                        tokenizeExtended(expr, result);
                        result.add(new Token(",", TokenType.COMMA).setOwner(tok));
                    }
                    if (!arrNew.getInitializer().getValues().isEmpty()) result.removeLast();
                    result.add(getOperatorByTokenName("}"));
                }
            }
            default -> throw new MeaningTreeException("New expression of unknown type");
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
            case ThreeWayComparisonOp op -> "<=>";
            default -> null;
        };
        if (binOp instanceof FloorDivOp) {
            tokenizeExtended(new CastTypeExpression(new IntType(64), new DivOp(binOp.getLeft(), binOp.getRight())), result);
            return;
        }
        if (binOp instanceof PowOp) {
            tokenizeExtended(new FunctionCall(
                    new SimpleIdentifier("pow"), binOp.getLeft(), binOp.getRight()), result);
            return;
        }
        if (binOp instanceof ContainsOp) {
            tokenizeExtended(new MethodCall(binOp.getRight(), new SimpleIdentifier("contains"), binOp.getLeft()), result);
            return;
        }
        if (binOp instanceof InstanceOfOp ins) {
            result.add(new Token("dynamic_cast", TokenType.CALLABLE_IDENTIFIER));
            result.add(new Token("<", TokenType.SEPARATOR));
            TokenGroup ops = tokenizeExtended(ins.getType(), result);
            result.add(new Token(">", TokenType.SEPARATOR));
            OperatorToken op = getOperatorByTokenName("CALL_(");
            ops.setMetadata(op, OperandPosition.LEFT);
            result.add(op);
            TokenGroup grp = tokenizeExtended(ins.getLeft(), result);
            grp.setMetadata(op, OperandPosition.CENTER);
            result.add(getOperatorByTokenName("CALL_)"));
            OperatorToken eq = getOperatorByTokenName("!=");
            op.setMetadata(eq, OperandPosition.LEFT);
            OperandToken nullptr = new OperandToken("nullptr", TokenType.IDENTIFIER);
            nullptr.setMetadata(eq, OperandPosition.RIGHT);
            result.add(eq);
            result.add(nullptr);
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
        OperatorToken dot;
        if (access instanceof PointerMemberAccess) {
            dot = getOperatorByTokenName("->");
        } else {
            dot = getOperatorByTokenName(".");
        }
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
}
