package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.treesitter.*;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.languages.configs.params.ExpressionMode;
import org.vstu.meaningtree.languages.configs.params.SkipErrors;
import org.vstu.meaningtree.languages.configs.params.TranslationUnitMode;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.*;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SelfReference;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.PlacementNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerMemberAccess;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.io.*;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.modules.Include;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.BasicCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.DefaultCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.FallthroughCaseBlock;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.GenericUserType;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.nodes.types.user.Class;
import org.vstu.meaningtree.nodes.types.user.GenericClass;
import org.vstu.meaningtree.utils.TreeSitterUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CppLanguage extends LanguageParser {
    private TSLanguage _language;
    private TSParser _parser;

    public CppLanguage(LanguageTranslator translator) {
        super(translator);
    }

    private void _initBackend() {
        if (_language == null) {
            _language = new TreeSitterCpp();
            _parser = new TSParser();
            _parser.setLanguage(_language);
        }
    }

    @Override
    public TSTree getTSTree() {
        _initBackend();
        return _parser.parseString(null, _code);
    }

    @NotNull
    public synchronized MeaningTree getMeaningTree(String code) {
        ctx.set("binaryRecursive", -1);
        _code = code;
        TSNode rootNode = getRootNode();
        List<String> errors = lookupErrors(rootNode);
        if (!errors.isEmpty()) {
            getConfigParameter(SkipErrors.class)
                    .filter(Boolean::booleanValue)
                    .orElseThrow(() -> new UnsupportedParsingException(String.format("Given code has syntax errors: %s", errors)));
        }

        Node node = parseTSNode(rootNode);
        if (node instanceof AssignmentExpression expr) {
            node = expr.toStatement();
        }

        // Оборачиваем функцию main в узел ProgramEntryPoint
        if (node instanceof FunctionDefinition functionDefinition
                && functionDefinition.getName().toString().equals("main")) {
            node = new ProgramEntryPoint(List.of(functionDefinition.getBody().getNodes()), node);
        }

        return new MeaningTree(node);
    }

    @Override
    public MeaningTree getMeaningTree(TSNode node, String code) {
        _code = code;
        return new MeaningTree(parseTSNode(node));
    }

    @Override
    public TSNode getRootNode() {
        TSNode result = super.getRootNode();

        boolean expressionMode = getConfigParameter(ExpressionMode.class).orElse(false);

        if (expressionMode) {
            // В режиме выражений в код перед парсингом подставляется заглушка в виде точки входа
            TSNode func = result.getNamedChild(0);
            if (!func.getType().equals("function_definition") || !getCodePiece(func.getChildByFieldName("declarator")
                    .getChildByFieldName("declarator")).equals("main")) {
                throw new UnsupportedParsingException("Syntax parsing of entry point has failed");
            }
            TSNode body = func.getChildByFieldName("body");

            if (body.getNamedChildCount() > 1 && !body.getNamedChild(0).isError()) {
                throw new UnsupportedParsingException("Many expressions in given code (you're using expression mode)");
            }

            if (body.getNamedChildCount() < 1) {
                throw new UnsupportedParsingException("Main expression was not found in expression mode");
            }

            result = body.getNamedChild(0);

            if (result.getType().equals("expression_statement")) {
                result = result.getNamedChild(0);
            }
        }
        return result;
    }

    @NotNull
    protected Node fromTSNode(@NotNull TSNode node) {
        Objects.requireNonNull(node);

        if (node.isNull()) {
            throw new UnsupportedParsingException("NULL Tree sitter node");
        }

        Node createdNode = switch (node.getType()) {
            case "ERROR", "parameter_pack_expansion" -> fromTSNode(node.getNamedChild(0));
            case "translation_unit" -> fromTranslationUnit(node);
            case "function_definition" -> fromFunction(node);
            case "expression_statement"-> fromExpressionStatement(node);
            case "binary_expression" -> fromBinaryExpression(node);
            case "unary_expression" -> fromUnaryExpression(node);
            case "parenthesized_expression" -> fromParenthesizedExpression(node);
            case "update_expression" -> fromUpdateExpression(node);
            case "call_expression" -> fromCallExpression(node);
            case "conditional_expression" -> fromConditionalExpression(node);
            case "comma_expression" -> fromCommaExpression(node);
            case "subscript_expression" -> fromSubscriptExpression(node);
            case "assignment_expression" -> fromAssignmentExpression(node);
            case "compound_literal_expression" -> fromTSNode(node.getChildByFieldName("value"));
            case "declaration" -> fromDeclaration(node);
            case "identifier", "qualified_identifier", "field_expression", "namespace_identifier", "type_identifier", "field_identifier" -> fromIdentifier(node);
            case "number_literal" -> fromNumberLiteral(node);
            case "char_literal" -> fromCharLiteral(node);
            case "string_literal" -> fromStringLiteral(node);
            case "user_defined_literal" -> fromUserDefinedLiteral(node);
            case "null" -> new NullLiteral();
            case "true" -> new BoolLiteral(true);
            case "concatenated_string" -> fromConcatenatedString(node);
            case "false" -> new BoolLiteral(false);
            case "system_lib_string" -> StringLiteral.fromEscaped(
                    TreeSitterUtils.getCodePiece( _code, node), StringLiteral.Type.NONE
            );
            case "initializer_list" -> fromInitializerList(node);
            case "primitive_type", "template_function", "placeholder_type_specifier", "sized_type_specifier", "type_descriptor" -> fromType(node);
            case "sizeof_expression" -> fromSizeOf(node);
            case "compound_statement" -> fromBlock(node);
            case "new_expression" -> fromNewExpression(node);
            case "delete_expression" -> fromDeleteExpression(node);
            case "cast_expression" -> fromCastExpression(node);
            case "pointer_expression" -> fromPointerExpression(node);
            case "this" -> new SelfReference("this");
            case "offsetof_expression" -> fromOffsetOf(node);
            case "preproc_defined" -> new FunctionCall(new SimpleIdentifier("defined"), (Expression)
                    fromTSNode(node.getNamedChild(0)));
            case "preproc_include" -> fromPreprocInclude(node);
            case "comment" -> fromComment(node);
            case "if_statement" -> fromIfStatement(node);
            case "for_statement" -> fromForStatement(node);
            case "while_statement" -> fromWhile(node);
            case "break_statement" -> fromBreakStatement(node);
            case "continue_statement" -> fromContinueStatement(node);
            case "switch_statement" -> fromSwitchStatement(node);
            case "return_statement" -> fromReturn(node);
            case "for_range_loop" -> fromForRangeLoop(node);
            default -> throw new UnsupportedParsingException(String.format("Can't parse %s this code:\n%s", node.getType(), getCodePiece(node)));
        };
        assignValue(node, createdNode);
        return createdNode;
    }

    private Node fromPreprocInclude(@NotNull TSNode node) {
        TSNode path = node.getChildByFieldName("path");
        Include.IncludeType type = Include.IncludeType.QUOTED_FORM;
        if (path.getType().equals("system_lib_string")) {
            type = Include.IncludeType.POINTY_BRACKETS_FORM;
        }
        return new Include((StringLiteral) fromTSNode(path), type);
    }

    private ForEachLoop fromForRangeLoop(TSNode node) {
        Type type = (Type) parseTSNode(node.getChildByFieldName("type"));
        SimpleIdentifier iterVarId = (SimpleIdentifier) parseTSNode(node.getChildByFieldName("declarator"));
        Expression iterable = (Expression) parseTSNode(node.getChildByFieldName("right"));
        Statement body = (Statement) parseTSNode(node.getChildByFieldName("body"));
        VariableDeclaration varDecl = new VariableDeclaration(type, iterVarId);
        return new ForEachLoop(varDecl, iterable, body);
    }

    private ReturnStatement fromReturn(TSNode node) {
        if (node.getChildCount() == 0)
            return new ReturnStatement();
        return new ReturnStatement((Expression) parseTSNode(node.getNamedChild(0)));
    }

    private FunctionDefinition fromFunction(TSNode node) {
        // TODO: по-хорошему надо отдельную функцию для определения всех модификаторов
        var modifiers = new ArrayList<DeclarationModifier>();
        if (node.getChild(0).getType().equals("storage_class_specifier")
                && getCodePiece(node.getChild(0)).equals("static")) {
            // Статик обозначает приватность функции (по отношению к файлу, где она определена)
            modifiers.add(DeclarationModifier.PRIVATE);
            modifiers.add(DeclarationModifier.STATIC);
        }
        else {
            modifiers.add(DeclarationModifier.PUBLIC);
        }

        Type returnType = fromType(node.getChildByFieldName("type"));
        Identifier identifier = (Identifier) fromIdentifier(
                node.getChildByFieldName("declarator").getChildByFieldName("declarator")
        );
        List<DeclarationArgument> parameters = fromFunctionParameters(
                node.getChildByFieldName("declarator").getChildByFieldName("parameters")
        );

        var declaration = new FunctionDeclaration(
                identifier,
                returnType,
                List.of(),
                parameters
        );
        declaration.setModifiers(modifiers);

        CompoundStatement body = fromBlock(node.getChildByFieldName("body"));

        return new FunctionDefinition(declaration, body);
    }

    private List<DeclarationArgument> fromFunctionParameters(TSNode node) {
        List<DeclarationArgument> parameters = new ArrayList<>();

        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (child.getType().equals("...")) {
                var decl = DeclarationArgument.listUnpacking(new UnknownType(), new SimpleIdentifier("arguments"));
                parameters.add(decl); // bad support
            }
            if (!List.of("parameter_declaration", "variadic_parameter_declaration").contains(child.getType())) {
                continue;
            }
            DeclarationArgument parameter = fromFormalParameter(child);
            parameters.add(parameter);
        }
        return parameters;
    }

    private DeclarationArgument fromFormalParameter(TSNode node) {
        Type type = fromType(node.getChildByFieldName("type"));

        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode child = node.getChild(i);
            if (child.getType().equals("type_qualifier") && getCodePiece(child).equals("const")) {
                type.setConst(true);
            }
        }

        var declaration = fromDeclarator(node.getChildByFieldName("declarator"), type);
        SimpleIdentifier name = declaration.getFirstDeclarator().getIdentifier();
        type = declaration.getType();

        var defValueNode = node.getChildByFieldName("default_value");
        Expression defaultValue = defValueNode != null && !defValueNode.isNull() ?
                (Expression) parseTSNode(defValueNode) : null;

        // Не поддерживается распаковка списков (как в Python) и значения по умолчанию
        return new DeclarationArgument(type,  name, defaultValue);
    }

    private CompoundStatement fromBlock(TSNode node) {
        var statements = ctx.createNodeBody(true);
        for (int i = 1; i < node.getChildCount() - 1; i++) {
            statements.add(parseTSNode(node.getChild(i)));
        }
        return statements.build();
    }

    private CaseBlock fromSwitchGroup(TSNode switchGroup) {
        Expression matchValue =
                (Expression) parseTSNode(switchGroup.getNamedChild(0));

        var statements = new ArrayList<Node>();

        for (int i = 1; i < switchGroup.getNamedChildCount(); i++) {
            statements.add(parseTSNode(switchGroup.getNamedChild(i)));
        }

        CaseBlock caseBlock;
        if (!statements.isEmpty() && statements.getLast() instanceof BreakStatement) {
            statements.removeLast();
            caseBlock = new BasicCaseBlock(matchValue, new CompoundStatement(statements));
        }
        else {
            caseBlock = new FallthroughCaseBlock(matchValue, new CompoundStatement(statements));
        }

        return caseBlock;
    }

    private Node fromSwitchStatement(TSNode switchNode) {
        Expression matchValue =
                (Expression) parseTSNode(switchNode.getChildByFieldName("condition").getNamedChild(0));

        DefaultCaseBlock defaultCaseBlock = null;
        List<CaseBlock> cases = new ArrayList<>();

        TSNode switchBlock = switchNode.getChildByFieldName("body");
        for (int i = 0; i < switchBlock.getNamedChildCount(); i++) {
            TSNode switchGroup = switchBlock.getNamedChild(i);

            String labelName = getCodePiece(switchGroup.getChild(0));
            if (labelName.equals("default")) {
                var statements = new ArrayList<Node>();

                for (int j = 0; j < switchGroup.getNamedChildCount(); j++) {
                    statements.add(parseTSNode(switchGroup.getNamedChild(j)));
                }

                if (!statements.isEmpty() && statements.getLast() instanceof BreakStatement) {
                    statements.removeLast();
                }
                defaultCaseBlock = new DefaultCaseBlock(new CompoundStatement(statements));
            }
            else {
                CaseBlock caseBlock = fromSwitchGroup(switchGroup);
                cases.add(caseBlock);
            }
        }

        return new SwitchStatement(matchValue, cases, defaultCaseBlock);
    }

    private Node fromContinueStatement(TSNode continueNode) {
        return new ContinueStatement();
    }

    private Node fromBreakStatement(TSNode breakNode) {
        return new BreakStatement();
    }

    private Loop fromWhile(TSNode node) {
        TSNode tsCond = node.getChildByFieldName("condition").getChild(1);
        Expression mtCond = (Expression) parseTSNode(tsCond);

        TSNode tsBody = node.getChildByFieldName("body");
        Statement mtBody = (Statement) parseTSNode(tsBody);

        if (mtCond instanceof BoolLiteral boolLiteral && boolLiteral.getValue()) {
            return new InfiniteLoop(mtBody, getLoopType(node));
        }

        return new WhileLoop(mtCond, mtBody);
    }

    private LoopType getLoopType(TSNode node) {
        return switch (node.getType()) {
            case "enhanced_for_statement", "for_statement" -> LoopType.FOR;
            case "while_statement" -> LoopType.WHILE;
            case "do_statement" -> LoopType.DO_WHILE;
            default -> throw new UnsupportedParsingException(String.format("Can't parse %s this code:\n%s", node.getType(), getCodePiece(node)));
        };
    }

    private Loop fromForStatement(TSNode node) {
        HasInitialization init = null;
        Expression condition = null;
        Expression update = null;

        if (!node.getChildByFieldName("initializer").isNull()) {
            List<TSNode> assignments = getChildrenByFieldName(node, "initializer");

            if (assignments.size() == 1) {
                init = (HasInitialization) parseTSNode(assignments.getFirst());
            }
            else if (assignments.size() > 1) {
                List<AssignmentStatement> assignmentStatements =
                        assignments.stream().map(
                                tsNode ->
                                        assignmentExpressionToStatement((AssignmentExpression) parseTSNode(tsNode))
                        ).toList();
                init = new MultipleAssignmentStatement(assignmentStatements);
            }
            else {
                throw new IllegalStateException("This should never occur");
            }
        }

        if (!node.getChildByFieldName("condition").isNull()) {
            condition = (Expression) parseTSNode(node.getChildByFieldName("condition"));
        }

        if (!node.getChildByFieldName("update").isNull()) {
            List<TSNode> updates = getChildrenByFieldName(node, "update");

            if (updates.size() == 1) {
                update = (Expression) parseTSNode(updates.getFirst());
            }
            else if (updates.size() > 1) {
                List<Expression> updateExpressions =
                        updates.stream().map(tsNode -> (Expression) parseTSNode(tsNode)).toList();
                update = new ExpressionSequence(updateExpressions);
            }
            else {
                throw new IllegalStateException("This should never occur");
            }
        }

        Statement body = (Statement) parseTSNode(node.getChildByFieldName("body"));

        if (init == null && condition == null && update == null) {
            return new InfiniteLoop(body, getLoopType(node));
        }

        RangeForLoop rangeFor = tryMakeRangeForLoop(init, condition, update, body);
        if (rangeFor != null) {
            return rangeFor;
        }

        return new GeneralForLoop(init, condition, update, body);
    }

    private AssignmentStatement assignmentExpressionToStatement(AssignmentExpression expression) {
        return new AssignmentStatement(expression.getLValue(), expression.getRValue());
    }

    @Nullable
    private RangeForLoop tryMakeRangeForLoop(HasInitialization init,
                                             Expression condition,
                                             Expression update,
                                             Statement body) {
        SimpleIdentifier loopVariable = null;
        Expression start = null;
        Expression stop = null;
        Expression step = null;
        boolean isExcludingEnd = false;

        if (init instanceof AssignmentExpression assignmentExpression
                && assignmentExpression.getLValue() instanceof SimpleIdentifier loopVariable_) {
            loopVariable = loopVariable_;
            start = assignmentExpression.getRValue();
        }
        // TODO: этот ужас нужно когда-нибудь переписать нормально
        else if (init instanceof VariableDeclaration variableDeclaration) {
            List<VariableDeclarator> declarators = List.of(variableDeclaration.getDeclarators());

            if (declarators.size() == 1) {
                VariableDeclarator declarator = declarators.getFirst();
                loopVariable = declarator.getIdentifier();

                Expression wrappedExpression = declarator.getRValue();
                if (wrappedExpression != null) {
                    if (wrappedExpression instanceof IntegerLiteral start_) {
                        start = start_;
                    }
                }
            }
        }

        if (condition instanceof BinaryComparison binaryComparison
                && (binaryComparison.getLeft().equals(loopVariable) || binaryComparison.getRight().equals(loopVariable))) {

            boolean isLoopVarLeft = binaryComparison.getLeft().equals(loopVariable);
            if (isLoopVarLeft) {
                stop = binaryComparison.getRight();
            } else {
                stop = binaryComparison.getLeft();
            }

            if (binaryComparison instanceof LtOp || binaryComparison instanceof GtOp) {
                isExcludingEnd = true;
            }
            else if (binaryComparison instanceof LeOp || binaryComparison instanceof GeOp) {
                isExcludingEnd = false;
            }
            else {
                // Т.к. binaryComparison может быть не только операцией больше/меньше, а еще
                // равно/не равно, то во втором случае нельзя организовать диапазон
                stop = null;
            }
        }

        step = switch (update) {
            case PostfixDecrementOp postfixDecrementOp -> new IntegerLiteral("-1");
            case PostfixIncrementOp postfixIncrementOp -> new IntegerLiteral("1");
            case PrefixDecrementOp prefixDecrementOp -> new IntegerLiteral("-1");
            case PrefixIncrementOp prefixIncrementOp -> new IntegerLiteral("1");
            case AssignmentExpression assignment -> {
                if (assignment.getAugmentedOperator() == AugmentedAssignmentOperator.ADD) {
                    yield assignment.getRValue();
                } else if (assignment.getAugmentedOperator() == AugmentedAssignmentOperator.SUB) {
                    yield new UnaryMinusOp(assignment.getRValue());
                } else {
                    yield null;
                }
            }
            default -> null;
        };

        if (start != null && stop != null && step != null && loopVariable != null) {
            Range range = new Range(start, stop, step, false, isExcludingEnd, Range.Type.UNKNOWN);
            return new RangeForLoop(range, loopVariable, body);
        }

        return null;
    }

    private List<TSNode> getChildrenByFieldName(TSNode node, String fieldName) {
        List<TSNode> nodes = new ArrayList<>();

        for (int i = 0; i < node.getChildCount(); i++) {
            String currentNodeFieldName = node.getFieldNameForChild(i);
            if (currentNodeFieldName != null && currentNodeFieldName.equals(fieldName)) {
                nodes.add(node.getChild(i));
            }
        }

        return nodes;
    }

    private Node fromIfStatement(TSNode node) {
        // Берем ребенка под индексом 1, чтобы избежать захвата скобок, а значит
        // неправильного парсинга (получаем выражение в скобках в качестве условия, а не просто выражение)
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition").getChild(1));
        Statement consequence = (Statement) parseTSNode(node.getChildByFieldName("consequence"));

        TSNode alternativeNode = node.getChildByFieldName("alternative");
        if (alternativeNode.isNull()) {
            return new IfStatement(condition, consequence);
        }

        Statement alternative = (Statement) parseTSNode(alternativeNode.getChild(1));
        return new IfStatement(condition, consequence, alternative);
    }

    private Node fromConcatenatedString(TSNode node) {
        List<StringLiteral> literals = new ArrayList<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            literals.add(fromStringLiteral(node.getNamedChild(i)));
        }
        StringBuilder val = new StringBuilder();
        for (StringLiteral s : literals) {
            val.append(s.getUnescapedValue());
        }
        return StringLiteral.fromUnescaped(val.toString(), StringLiteral.Type.NONE);
    }

    private Comment fromComment(TSNode node) {
        return Comment.fromUnescaped(getCodePiece(node).replaceFirst("/\\*", "")
                .replaceFirst("//", "").replace("*/", ""));
    }

    private Node fromOffsetOf(TSNode node) {
        return new FunctionCall(new SimpleIdentifier("offsetof"), (Expression) parseTSNode(node.getChildByFieldName("type").getChildByFieldName("type")),
                (Expression) parseTSNode(node.getChildByFieldName("member")));
    }

    private Node fromCharLiteral(TSNode node) {
        return new CharacterLiteral(getCodePiece(node.getNamedChild(0)).charAt(0));
    }

    private Node fromInitializerList(TSNode node) {
        List<Expression> expressions = new ArrayList<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            expressions.add((Expression) parseTSNode(node.getNamedChild(i)));
        }
        return new ArrayLiteral(expressions);
    }

    private Node fromPointerExpression(TSNode node) {
        String op = getCodePiece(node);
        Expression argument = (Expression) parseTSNode(node.getChildByFieldName("argument"));
        if (op.startsWith("&")) {
            if (argument instanceof AddOp binOp) {
                Expression leftmost = binOp.getLeft();
                List<Expression> args = new ArrayList<>();
                args.add(binOp.getRight());
                while (leftmost instanceof AddOp leftmostOp) {
                    leftmost = leftmostOp.getLeft();
                    args.add(leftmostOp.getRight());
                }
                return new IndexExpression(leftmost, BinaryExpression.
                        fromManyOperands(args.reversed().toArray(new Expression[0]), 0, AddOp.class), true);
            }
            return new PointerPackOp(argument);
        } else if (op.startsWith("*")) {
            return new PointerUnpackOp(argument);
        } else {
            throw new UnsupportedParsingException("Unknown pointer expression: ".concat(op));
        }
    }

    private Node fromCastExpression(TSNode node) {
        Type type = fromType(node.getChildByFieldName("type"));
        Expression value = (Expression) parseTSNode(node.getChildByFieldName("value"));
        if (value instanceof ParenthesizedExpression p && p.getExpression() instanceof DivOp div && type instanceof IntType) {
            return new FloorDivOp(div.getLeft(), div.getRight());
        }
        return new CastTypeExpression(type, value);
    }

    private Node fromDeleteExpression(TSNode node) {
        String line = getCodePiece(node);
        return new DeleteExpression((Expression) parseTSNode(node.getNamedChild(0)), line.contains("[") && line.contains("]"));
    }

    private Node fromNewExpression(TSNode node) {
        Type type = fromType(node.getChildByFieldName("type"));

        TSNode placement = node.getChildByFieldName("placement");
        TSNode declarator = node.getChildByFieldName("declarator");
        TSNode arguments = node.getChildByFieldName("arguments");

        List<Expression> args = new ArrayList<>();
        TSNode childSource;
        if (!placement.isNull()) {
            childSource = placement;
        } else if (!arguments.isNull()) {
            childSource = arguments;
        } else if (!declarator.isNull()) {
            List<Expression> initList = new ArrayList<>();
            if (!arguments.isNull()) {
                for (int i = 0; i < arguments.getNamedChildCount(); i++) {
                    initList.add((Expression) parseTSNode(arguments.getNamedChild(i)));
                }
            }
            List<Expression> dimensions = new ArrayList<>();
            dimensions.add((Expression) parseTSNode(declarator.getNamedChild(0)));
            while (!declarator.getNamedChild(1).isNull()
                    && declarator.getNamedChild(1).getType().equals("new_declarator")) {
                declarator = declarator.getNamedChild(1);
                dimensions.add((Expression) parseTSNode(declarator.getNamedChild(0)));
            }
            ArrayInitializer initializer = !initList.isEmpty() ? new ArrayInitializer(initList) : null;
            return new ArrayNewExpression(type, new Shape(dimensions.size(), dimensions.toArray(new Expression[0])), initializer);
        } else {
            throw new UnsupportedParsingException("No arguments for new expression");
        }
        for (int i = 0; i < childSource.getNamedChildCount(); i++) {
            args.add((Expression) parseTSNode(childSource.getNamedChild(i)));
        }
        if (childSource == placement) {
            return new PlacementNewExpression(type, args);
        } else {
            return new ObjectNewExpression(type, args);
        }
    }

    private Node fromSizeOf(TSNode node) {
        TSNode inner = node.getChildByFieldName("value");
        if (inner.isNull()) {
            inner = node.getChildByFieldName("type");
        }
        return new SizeofExpression((Expression) parseTSNode(inner));
    }

    private Type fromTypeByString(String type) {
        return switch (type) {
            case "int" -> new IntType();
            case "int8_t" -> new IntType(8);
            case "uint8_t" -> new IntType(8, true);
            case "size_t", "uint64_t" -> new IntType(64, true);
            case "int16_t" -> new IntType(16);
            case "int32_t", "time32_t" -> new IntType(32);
            case "int64_t", "time64_t" -> new IntType(64);
            case "uint16_t" -> new IntType(16, true);
            case "uint32_t" -> new IntType(32, true);
            case "double" -> new FloatType(64);
            case "float" -> new FloatType(32);
            case "char" -> new CharacterType(8);
            case "wchar_t", "char16_t" -> new CharacterType(16);
            case "bool" -> new BooleanType();
            case "void" -> new NoReturn();
            case "string" -> new StringType(8);
            case "wstring", "u16string" -> new StringType(16);
            case "u32string" -> new StringType(32);
            // TODO: add support for symbol table
            default -> new Class(new SimpleIdentifier(type));
        };
    }

    private String reprQualifiedIdentifier(QualifiedIdentifier ident) {
        if (ident.getScope() instanceof QualifiedIdentifier leftQualified) {
            return String.format("%s::%s", reprQualifiedIdentifier(leftQualified), ident.getMember().toString());
        } else if (ident.getScope() instanceof ScopedIdentifier scoped) {
            return String.format("%s::%s", String.join(".",
                    scoped.getScopeResolution().stream().map(Expression::toString).toList()), ident.getMember().toString());
        }
        return String.format("%s::%s", ident.getScope().toString(), ident.getMember().toString());
    }

    private Type fromType(TSNode node) {
        String type = getCodePiece(node);
        if (node.getType().equals("type_identifier") || node.getType().equals("primitive_type")) {
            return fromTypeByString(type);
        }
        else if (node.getType().equals("sized_type_specifier")) {
            return parseSizedTypeSpecifier(node);
        }
        else if (node.getType().equals("type_descriptor")) {
            Type inner;
            if (node.getChildByFieldName("type").getType().equals("sized_type_specifier")) {
                inner = parseSizedTypeSpecifier(node.getChildByFieldName("type"));
            } else {
                inner = fromType(node.getChildByFieldName("type"));
            }
            if (!node.getChildByFieldName("declarator").isNull()
                    && node.getChildByFieldName("declarator").getType().equals("abstract_pointer_declarator")) {
                if (inner instanceof NoReturn) {
                    return new PointerType(new UnknownType());
                }
                return new PointerType(inner);
            } else if (!node.getChildByFieldName("declarator").isNull()
                    && node.getChildByFieldName("declarator").getType().equals("abstract_reference_declarator")) {
                return new ReferenceType(inner);
            }
            return inner;
        } else if (node.getType().equals("template_function")) {
            // TODO: add support for symbol table
            Identifier ident = (Identifier) fromIdentifier(node.getChildByFieldName("name"));
            List<Type> subTypes = new ArrayList<>();
            TSNode arguments = node.getChildByFieldName("arguments");
            for (int i = 0; i < arguments.getNamedChildCount(); i++) {
                subTypes.add(fromType(arguments.getNamedChild(i)));
            }
            return new GenericClass(ident, subTypes.toArray(new Type[0]));
        } else if (node.getType().equals("qualified_identifier")) {
            QualifiedIdentifier q;
            List<Type> generic = new ArrayList<>();
            if (node.getChildByFieldName("name").getType().equals("template_type")) {
                TSNode template = node.getChildByFieldName("name");
                SimpleIdentifier s = new SimpleIdentifier(getCodePiece(template.getChildByFieldName("name")));
                TSNode arguments = template.getChildByFieldName("arguments");
                for (int i = 0; i < arguments.getNamedChildCount(); i++) {
                    generic.add(fromType(arguments.getNamedChild(i)));
                }
                q = new QualifiedIdentifier((Identifier) fromIdentifier(node.getChildByFieldName("scope")), s);
            } else {
                q = (QualifiedIdentifier) fromIdentifier(node);
            }
            Type type1 = !generic.isEmpty() ? generic.getFirst() : new UnknownType();
            Type type2 = generic.size() > 1 ? generic.get(1) : new UnknownType();
            return switch (reprQualifiedIdentifier(q)) {
                case "std::map" -> new UnorderedDictionaryType(type1, type2);
                case "std::list", "std::vector", "std::array" -> new ListType(type1);
                case "std::set" -> new SetType(type1);
                case "std::string", "std::wstring" -> new StringType(8);
                case "std::u16string" -> new StringType(16);
                case "std::u32string" -> new StringType(32);
                default -> {
                    // TODO: add support for symbol table
                    if (generic.isEmpty()) {
                        yield new Class(q);
                    }
                    yield new GenericClass(q, generic.toArray(new Type[0]));
                }
            };

        } else {
            return new UnknownType();
        }
    }

    private Type parseSizedTypeSpecifier(TSNode node) {
        String type = getCodePiece(node);
        String subType = node.getChildByFieldName("type").isNull() ? "int" : getCodePiece(node.getChildByFieldName("type"));

        if (type.matches(".*(long|int|short|unsigned|signed).*")) {
            boolean isUnsigned = false;
            int size = 32;
            if (type.contains("unsigned")) {
                isUnsigned = true;
            }
            if (type.contains("long")) {
                size *= (int) Math.pow(2, StringUtils.countMatches(type, "long"));
            } else if (type.contains("short")) {
                size = 16;
            }
            if (size > 64) {
                size = 64;
            }
            if (subType.equals("int") || subType.equals("short") || subType.equals("long")) {
                return new IntType(size, isUnsigned);
            } else if (subType.equals("char")) {
                return new CharacterType();
            } else {
                return new FloatType(size);
            }
        } else {
            throw new UnsupportedOperationException(String.format("Can't parse sized type %s this code:\n%s", node.getType(), getCodePiece(node)));
        }
    }

    private StringLiteral fromStringLiteral(TSNode node) {
        String strLiteral = getCodePiece(node);
        boolean isWide = strLiteral.toLowerCase().startsWith("l");
        strLiteral = strLiteral.substring(1, strLiteral.length() - 1);
        StringLiteral literal = StringLiteral.fromEscaped(strLiteral, StringLiteral.Type.NONE);
        if (isWide) {
            literal.setTypeCharSize(32);
        }
        return literal;
    }

    @NotNull
    private Literal fromUserDefinedLiteral(@NotNull TSNode node) {
        if (node.getChildByFieldName("number_literal").isNull()) {
            throw new UnsupportedParsingException("Only number literals are supported");
        }
        String value = getCodePiece(node.getChildByFieldName("number_literal"));
        String literalSuffix = getCodePiece(node.getChildByFieldName("literal_suffix"));

        if (literalSuffix.equals("f") || literalSuffix.equals("F")) {
            return new FloatLiteral(value, false);
        }

        throw new IllegalArgumentException(
                "Can't parse user defined literal with \"%s\" value and \"%s\" literal suffix".formatted(
                        value,
                        literalSuffix
                )
        );
    }

    @NotNull
    private ExpressionSequence fromSubscriptArgumentList(@NotNull TSNode node) {
        var arguments = new ArrayList<Expression>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode tsArgument = node.getNamedChild(i);
            Expression argument = (Expression) parseTSNode(tsArgument);
            arguments.add(argument);
        }
        return new ExpressionSequence(arguments);
    }

    @NotNull
    private IndexExpression fromSubscriptExpression(@NotNull TSNode node) {
        Expression argument = (Expression) parseTSNode(node.getChildByFieldName("argument"));
        ExpressionSequence indices = fromSubscriptArgumentList(node.getChildByFieldName("indices"));
        return new IndexExpression(argument, indices);
    }

    @NotNull
    private ExpressionSequence fromCommaExpression(@NotNull TSNode node) {
        var expressions = new ArrayList<Expression>();

        TSNode tsLeft = node.getChildByFieldName("left");
        expressions.add((Expression) parseTSNode(tsLeft));

        TSNode tsRight = node.getChildByFieldName("right");
        while (tsRight.getType().equals("comma_expression")) {
            tsLeft = tsRight.getChildByFieldName("left");
            expressions.add((Expression) parseTSNode(tsLeft));

            tsRight = tsRight.getChildByFieldName("right");
        }
        expressions.add((Expression) parseTSNode(tsRight));

        return new CommaExpression(expressions);
    }

    @NotNull
    private TernaryOperator fromConditionalExpression(@NotNull TSNode node) {
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition"));
        Expression consequence = (Expression) parseTSNode(node.getChildByFieldName("consequence"));
        Expression alternative = (Expression) parseTSNode(node.getChildByFieldName("alternative"));
        return new TernaryOperator(condition, consequence, alternative);
    }

    public Expression sanitizeFromStd(Expression expr) {
        if (expr instanceof QualifiedIdentifier qual && qual.getScope().equalsIdentifier("std")) {
            return qual.getMember();
        }
        return expr;
    }

    @NotNull
    private Node fromCallExpression(@NotNull TSNode node) {
        Expression functionName = (Expression) parseTSNode(node.getChildByFieldName("function"));
        Expression clearFunctionName = sanitizeFromStd(functionName);

        TSNode tsArguments = node.getChildByFieldName("arguments");
        List<Expression> arguments = new ArrayList<>();
        for (int i = 0; i < tsArguments.getNamedChildCount(); i++) {
            TSNode tsArgument = tsArguments.getNamedChild(i);
            Expression argument = (Expression) parseTSNode(tsArgument);
            arguments.add(argument);
        }

        if (functionName instanceof ParenthesizedExpression p
                && p.getExpression() instanceof SimpleIdentifier ident
                && arguments.size() == 1) {
            return new CastTypeExpression(new Class(ident), arguments.getFirst());
        }

        if (clearFunctionName.toString().equals("pow") && arguments.size() == 2) {
            return new PowOp(arguments.getFirst(), arguments.getLast());
        }

        if (functionName instanceof ScopedIdentifier scoped && scoped.getScopeResolution().size() > 1) {
            List<SimpleIdentifier> object = scoped.getScopeResolution()
                    .subList(0, scoped.getScopeResolution().size() - 1);
            return new MethodCall(object.size() == 1 ? object.getFirst() : new ScopedIdentifier(object)
                    , scoped.getScopeResolution().getLast(), arguments);
        }
        if (functionName instanceof MemberAccess memAccess) {
            return new MethodCall(memAccess.getExpression(), memAccess.getMember(), arguments);
        }

        if (clearFunctionName.toString().equals("printf")) {
            return new FormatPrint(arguments.getFirst(), arguments.subList(1, arguments.size()).toArray(new Expression[0]));
        }

        if (functionName.toString().equals("scanf") || clearFunctionName.toString().equals("scanf_s")) {
            return new FormatInput(arguments.getFirst(), arguments.subList(1, arguments.size()).toArray(new Expression[0]));
        }

        if ((clearFunctionName.toString().equals("puts") || clearFunctionName.toString().equals("puts_s")) && arguments.size() == 1) {
            return new PrintValues(arguments,
                    StringLiteral.fromUnescaped("", StringLiteral.Type.NONE),
                    StringLiteral.fromUnescaped("", StringLiteral.Type.NONE));
        }

        if (clearFunctionName.toString().equals("gets") || clearFunctionName.toString().equals("gets_s")) {
            return new PointerInputCommand(arguments.getFirst(), arguments.subList(1, arguments.size()));
        }

        if ((clearFunctionName.toString().equals("malloc") || clearFunctionName.toString().equals("сalloc") ||
                clearFunctionName.toString().equals("_malloc") || clearFunctionName.toString().equals("_сalloc"))
                && arguments.size() == 1) {
            Type foundType = null;
            Expression count = new IntegerLiteral(1);
            for (Expression arg : arguments) {
                if (arg instanceof SizeofExpression sizeOf && sizeOf.getExpression() instanceof Type type) {
                    foundType = type;
                } else if (arg instanceof MulOp mulOp) {
                    if (mulOp.getLeft() instanceof SizeofExpression sizeOf && sizeOf.getExpression() instanceof Type type) {
                        foundType = type;
                    }
                    if (!(mulOp.getRight() instanceof SizeofExpression)) {
                        count = mulOp.getRight();
                    }

                    if (mulOp.getRight() instanceof SizeofExpression sizeOf && sizeOf.getExpression() instanceof Type type) {
                        foundType = type;
                    }
                    if (!(mulOp.getLeft() instanceof SizeofExpression)) {
                        count = mulOp.getLeft();
                    }
                }
            }
            if (foundType != null) {
                return new MemoryAllocationCall(foundType, count, functionName.toString().equals("сalloc"));
            }
        }

        if (functionName.toString().equals("free") && arguments.size() == 1) {
            return new MemoryFreeCall(arguments.getFirst());
        }

        return new FunctionCall(functionName, arguments);
    }

    @NotNull
    private ParenthesizedExpression fromParenthesizedExpression(@NotNull TSNode node) {
        Expression expr = (Expression) parseTSNode(node.getChild(1));
        return new ParenthesizedExpression(expr);
    }

    @NotNull
    private UnaryExpression fromUnaryExpression(@NotNull TSNode node) {
        Expression argument = (Expression) parseTSNode(node.getChildByFieldName("argument"));
        return switch (getCodePiece(node.getChild(0))) {
            case "!", "not" -> new NotOp(argument);
            case "~" -> new InversionOp(argument);
            case "-" -> new UnaryMinusOp(argument);
            case "+" -> new UnaryPlusOp(argument);
            default -> throw new UnsupportedOperationException();
        };
    }

    @NotNull
    private UnaryExpression fromUpdateExpression(@NotNull TSNode node) {
        String code = getCodePiece(node);

        if (code.endsWith("++")) {
            return new PostfixIncrementOp((Expression) parseTSNode(node.getChild(0)));
        }
        else if (code.startsWith("++")) {
            return new PrefixIncrementOp((Expression) parseTSNode(node.getChild(1)));
        }
        else if (code.endsWith("--")) {
            return new PostfixDecrementOp((Expression) parseTSNode(node.getChild(0)));
        }
        else if (code.startsWith("--")) {
            return new PrefixDecrementOp((Expression) parseTSNode(node.getChild(1)));
        }

        throw new IllegalArgumentException();
    }

    @NotNull
    private Node fromBinaryExpression(@NotNull TSNode node) {
        if (ctx.check("binaryRecursive", -1)) {
            ctx.set("binaryRecursive", node.getEndByte());
        }
        Expression left = (Expression) parseTSNode(node.getChildByFieldName("left"));
        Expression right = (Expression) parseTSNode(node.getChildByFieldName("right"));
        TSNode operator = node.getChildByFieldName("operator");
        if (ctx.check("binaryRecursive", node.getEndByte())) {
            ctx.set("binaryRecursive", -1);
        }

        return switch (getCodePiece(operator)) {
            case "+" -> new AddOp(left, right);
            case "-" -> new SubOp(left, right);
            case "*" -> new MulOp(left, right);
            case "/" -> new DivOp(left, right);
            case "%" -> new ModOp(left, right);
            case "<" -> new LtOp(left, right);
            case ">" -> new GtOp(left, right);
            case "==" -> {
                EqOp eq = new EqOp(left, right);
                if (eq.getLeft() instanceof FunctionCall call
                        && call.getArguments().size() == 1
                        && call.getFunction() instanceof GenericUserType type
                        && type.getTypeParameters().length == 1
                        && type.getName().toString().equals("dynamic_cast")
                        && right instanceof NullLiteral
                ) {
                    yield new NotOp(new ParenthesizedExpression(new InstanceOfOp(call.getArguments().getFirst(), type.getTypeParameters()[0])));
                }

                if (eq.getLeft() instanceof PointerPackOp leftPtr && eq.getRight() instanceof PointerPackOp rightPtr ) {
                    yield new ReferenceEqOp(leftPtr.getArgument(), rightPtr.getArgument(), false);
                }
                // TODO: add type checking

                yield eq;
            }
            case "!=" -> {
                NotEqOp neq = new NotEqOp(left, right);
                if (neq.getLeft() instanceof FunctionCall call
                        && call.getArguments().size() == 1
                        && call.getFunction() instanceof GenericUserType type
                        && type.getTypeParameters().length == 1
                        && type.getName().toString().equals("dynamic_cast")
                        && right instanceof NullLiteral
                ) {
                    yield new InstanceOfOp(call.getArguments().getFirst(), type.getTypeParameters()[0]);
                }
                if (neq.getLeft() instanceof PointerPackOp leftPtr && neq.getRight() instanceof PointerPackOp rightPtr) {
                    yield new ReferenceEqOp(leftPtr.getArgument(), rightPtr.getArgument(), false);
                }
                yield neq;
            }
            case ">=" -> new GeOp(left, right);
            case "<=" -> new LeOp(left, right);
            case "&&", "and" -> new ShortCircuitAndOp(left, right);
            case "||", "or" -> new ShortCircuitOrOp(left, right);
            case "&" -> new BitwiseAndOp(left, right);
            case "|" -> new BitwiseOrOp(left, right);
            case "^" -> new XorOp(left, right);
            case "<<" -> {
                LeftShiftOp lshift = new LeftShiftOp(left, right);
                if (ctx.check("binaryRecursive", -1)) {
                    Expression fName = lshift.getLeftmost();
                    List<Expression> exprs = lshift.getRecursivePlainOperands();
                    boolean isEndl = sanitizeFromStd(exprs.getLast()).equalsIdentifier("endl");
                    if (sanitizeFromStd(fName).equalsIdentifier("cout")) {
                        yield new PrintValues(exprs.subList(1, exprs.size() - (isEndl ? 1 : 0)),
                                StringLiteral.fromUnescaped("", StringLiteral.Type.NONE),
                                StringLiteral.fromUnescaped(isEndl ? "\n" : "", StringLiteral.Type.NONE)
                        );
                    } else if (sanitizeFromStd(fName).equalsIdentifier("cin")) {
                        yield new InputCommand(exprs.subList(1, exprs.size()));
                    }
                }
                yield lshift;
            }
            case ">>" -> {
                var rshift = new RightShiftOp(left, right);
                if (ctx.check("binaryRecursive", -1)) {
                    Expression fName = rshift.getLeftmost();
                    List<Expression> exprs = rshift.getRecursivePlainOperands();
                    if (sanitizeFromStd(fName).equalsIdentifier("cin")) {
                        yield new InputCommand(exprs.subList(1, exprs.size()));
                    }
                }
                yield rshift;
            }
            case "<=>" -> new ThreeWayComparisonOp(left, right);
            default -> throw new UnsupportedOperationException(String.format("Can't parse operator %s", getCodePiece(operator)));
        };
    }

    @NotNull
    private Declaration fromDeclaration(@NotNull TSNode node) {
        TSNode typeNode = node.getChildByFieldName("type");
        Type mainType = fromType(typeNode);

        int i = 0;

        while (!"type".equals(node.getFieldNameForChild(i))) {
            TSNode currentNode = node.getNamedChild(i);
            if (currentNode.getType().equals("type_qualifier") && getCodePiece(currentNode).equals("const")) {
                mainType.setConst(true);
            }
            i++;
        }

        var declarators = new ArrayList<VariableDeclaration>();
        for (i += 1; i < node.getNamedChildCount(); i++) {
            TSNode tsDeclarator = node.getNamedChild(i);
            var decl = fromDeclarator(tsDeclarator, mainType);
            if (decl != null) {
                declarators.add(decl);
            }
        }

        SeparatedVariableDeclaration sepDecl = new SeparatedVariableDeclaration(declarators);
        if (sepDecl.canBeReduced()) {
            return sepDecl.reduce();
        }
        return sepDecl;
    }

    //has size effects
    private VariableDeclaration fromDeclarator(@NotNull TSNode tsDeclarator, Type mainType) {
        if (tsDeclarator.getType().equals("type_qualifier") && getCodePiece(tsDeclarator).equals("const")) {
            mainType.setConst(true);
        } else if (tsDeclarator.getType().equals("array_declarator")) {
            List<Expression> dimensions = new ArrayList<>();
            TSNode arrayDimension = tsDeclarator;
            while (!arrayDimension.isNull() && arrayDimension.getType().equals("array_declarator")) {
                if (!arrayDimension.getChildByFieldName("value").isNull()) {
                    dimensions.add((Expression) parseTSNode(arrayDimension.getChildByFieldName("value")));
                } else {
                    dimensions.add(null);
                }
                arrayDimension = arrayDimension.getChildByFieldName("declarator");
            }
            mainType = new ArrayType(mainType, dimensions.size(), dimensions);
            return new VariableDeclaration(mainType, new VariableDeclarator((SimpleIdentifier) parseTSNode(arrayDimension)));
        } else if (tsDeclarator.getType().equals("init_declarator")) {
            TSNode tsVariableName = tsDeclarator.getChildByFieldName("declarator");
            Type type = mainType;

            if (tsVariableName.getType().equals("pointer_declarator")) {
                type = new PointerType(mainType);
                if (mainType instanceof NoReturn) {
                    type = new PointerType(new UnknownType());
                }
                if ((tsVariableName.getNamedChild(0).getType().equals("type_qualifier") &&
                        getCodePiece(tsVariableName.getNamedChild(0)).equals("const"))
                ) {
                    type.setConst(true);
                }
                tsVariableName = tsVariableName.getChildByFieldName("declarator");
            } else if (tsVariableName.getType().equals("reference_declarator")) {
                type = new ReferenceType(mainType);
                tsVariableName = tsVariableName.getNamedChild(0);
            } else if (tsVariableName.getType().equals("array_declarator")) {
                List<Expression> dimensions = new ArrayList<>();
                TSNode arrayDimension = tsVariableName;
                while (!arrayDimension.isNull() && arrayDimension.getType().equals("array_declarator")) {
                    if (!arrayDimension.getChildByFieldName("value").isNull()) {
                        dimensions.add((Expression) parseTSNode(arrayDimension.getChildByFieldName("value")));
                    } else {
                        dimensions.add(null);
                    }
                    arrayDimension = arrayDimension.getChildByFieldName("declarator");
                }
                type = new ArrayType(mainType, dimensions.size(), dimensions);
                tsVariableName = arrayDimension;
            }
            TSNode tsValue = tsDeclarator.getChildByFieldName("value");

            SimpleIdentifier variableName = (SimpleIdentifier) parseTSNode(tsVariableName);
            Expression value = (Expression) parseTSNode(tsValue);
            if (value instanceof PlainCollectionLiteral col) {
                if (mainType instanceof PlainCollectionType arrayType) {
                    col.setTypeHint(arrayType.getItemType());
                } else {
                    col.setTypeHint(mainType);
                }
            }

            VariableDeclarator declarator = new VariableDeclarator(variableName, value);
            return new VariableDeclaration(type, declarator);
        } else {
            Type type = mainType;

            if (tsDeclarator.getType().equals("pointer_declaration")) {
                type = new PointerType(mainType);
                if (mainType instanceof NoReturn) {
                    type = new PointerType(new UnknownType());
                }
            } else if (tsDeclarator.getType().equals("reference_declaration")) {
                type = new ReferenceType(mainType);
            }
            return new VariableDeclaration(type, new VariableDeclarator((SimpleIdentifier) fromIdentifier(tsDeclarator)));
        }
        return null;
    }

    private QualifiedIdentifier rightToLeftQualified(Identifier left, Identifier right) {
        if (right instanceof QualifiedIdentifier rightQualified) {
            SimpleIdentifier ident = (SimpleIdentifier) rightQualified.getScope();
            QualifiedIdentifier newLeft = new QualifiedIdentifier(left, ident);
            return rightToLeftQualified(newLeft, rightQualified.getMember());
        }
        return new QualifiedIdentifier(left, (SimpleIdentifier) right);
    }

    @NotNull
    private Node fromIdentifier(@NotNull TSNode node) {
        if (node.getType().equals("identifier") || node.getType().equals("field_identifier") || node.getType().equals("namespace_identifier") || node.getType().equals("type_identifier")) {
            return new SimpleIdentifier(getCodePiece(node));
        } else if (node.getType().equals("qualified_identifier")) {
            Identifier right = (Identifier) fromIdentifier(node.getChildByFieldName("name"));
            SimpleIdentifier left = (SimpleIdentifier) fromIdentifier(node.getChildByFieldName("scope"));
            return rightToLeftQualified(left, right);
        } else if (node.getType().equals("field_expression")) {
            Node treeNode = parseTSNode(node.getChildByFieldName("argument"));
            boolean isPointer = node.getChild(1).getType().equals("->");
            if (treeNode instanceof SimpleIdentifier ident && !isPointer) {
                return new MemberAccess(ident, (SimpleIdentifier) fromIdentifier(node.getChildByFieldName("field")));
            } else if (treeNode instanceof ScopedIdentifier ident && !isPointer) {
                List<SimpleIdentifier> identList = new ArrayList<>(ident.getScopeResolution());
                Identifier fieldIdent = (Identifier) fromIdentifier(node.getChildByFieldName("field"));
                if (fieldIdent instanceof SimpleIdentifier sIdent) {
                    identList.add(sIdent);
                } else if (fieldIdent instanceof ScopedIdentifier scopedIdent) {
                    identList.addAll(scopedIdent.getScopeResolution());
                } else if (fieldIdent instanceof QualifiedIdentifier) {
                    throw new UnsupportedParsingException("Unsupported scoped and qualified identifier combination");
                }
                return new ScopedIdentifier(identList).toMemberAccess();
            } else {
                if (isPointer) {
                    return new PointerMemberAccess((Expression) parseTSNode(node.getChildByFieldName("argument")), (SimpleIdentifier) parseTSNode(node.getChildByFieldName("field")));
                }
                return new MemberAccess((Expression) parseTSNode(node.getChildByFieldName("argument")), (SimpleIdentifier) parseTSNode(node.getChildByFieldName("field")));
            }
        } else {
            throw new UnsupportedParsingException("Unknown identifier: " + node.getType());
        }
    }

    @NotNull
    private Expression fromAssignmentExpression(@NotNull TSNode node) {
        Expression left = (Expression) parseTSNode(node.getChildByFieldName("left"));
        Expression right = (Expression) parseTSNode(node.getChildByFieldName("right"));

        String operatorType = node.getChildByFieldName("operator").getType();
        AugmentedAssignmentOperator augmentedAssignmentOperator = switch (operatorType) {
            case "=" -> AugmentedAssignmentOperator.NONE;
            case "+=" -> AugmentedAssignmentOperator.ADD;
            case "-=" -> AugmentedAssignmentOperator.SUB;
            case "*=" -> AugmentedAssignmentOperator.MUL;
            // Возможно тип AugmentedAssignmentOperator надо определять исходя из типа аргументов
            case "/=" -> AugmentedAssignmentOperator.DIV;
            case "&=" -> AugmentedAssignmentOperator.BITWISE_AND;
            case "|=" -> AugmentedAssignmentOperator.BITWISE_OR;
            case "^=" -> AugmentedAssignmentOperator.BITWISE_XOR;
            case "<<=" -> AugmentedAssignmentOperator.BITWISE_SHIFT_LEFT;
            case ">>=" -> AugmentedAssignmentOperator.BITWISE_SHIFT_RIGHT;
            case "%=" -> AugmentedAssignmentOperator.MOD;
            default -> throw new IllegalStateException("Unexpected augmented assignment type: " + operatorType);
        };

        if (left instanceof PointerUnpackOp ptrOp) {
            if (ptrOp.getArgument() instanceof ParenthesizedExpression p && p.getExpression() instanceof AddOp binOp) {
                Expression leftmost = binOp.getLeft();
                List<Expression> args = new ArrayList<>();
                args.add(binOp.getRight());
                while (leftmost instanceof AddOp op) {
                    leftmost = op.getLeft();
                    args.add(op.getRight());
                }
                left = new IndexExpression(leftmost, BinaryExpression.
                        fromManyOperands(args.reversed().toArray(new Expression[0]), 0, AddOp.class), true);
            } else if (getConfigParameter(ExpressionMode.class).orElse(false)) {
                return right;
            }
        }

        return new AssignmentExpression(left, right, augmentedAssignmentOperator);
    }

    @NotNull
    private NumericLiteral fromNumberLiteral(@NotNull TSNode node) {
        String value = getCodePiece(node);
        if (value.contains(".")) {
            return new FloatLiteral(value);
        }
        return new IntegerLiteral(value);
    }

    @NotNull
    private Node fromTranslationUnit(@NotNull TSNode node) {
        List<Node> nodes = new ArrayList<>();
        Node entryPoint = null;
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode currNode = node.getNamedChild(i);
            Node n = parseTSNode(currNode);
            nodes.add(n);
            if (n instanceof FunctionDefinition functionDefinition
                    && functionDefinition.getName().toString().equals("main")
            ) {
                entryPoint = n;
                if (!getConfigParameter(TranslationUnitMode.class).orElse(true)) {
                    n = new ProgramEntryPoint(List.of(functionDefinition.getBody().getNodes()), n);
                    return n;
                }
            }
        }
        return new ProgramEntryPoint(nodes, entryPoint);
    }

    @NotNull
    private Node fromExpressionStatement(@NotNull TSNode node) {
        if (node.getNamedChild(0).isNull()) {
            return new ExpressionStatement(null);
        }
        Expression expr = (Expression) parseTSNode(node.getNamedChild(0));
        if (expr instanceof AssignmentExpression assignmentExpression) {
            return assignmentExpression.toStatement();
        }
        return new ExpressionStatement(expr);
    }
}
