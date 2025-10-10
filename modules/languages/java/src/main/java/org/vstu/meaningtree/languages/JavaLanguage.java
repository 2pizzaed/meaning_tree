package org.vstu.meaningtree.languages;

import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.treesitter.*;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.languages.configs.params.EnforceEntryPoint;
import org.vstu.meaningtree.languages.configs.params.ExpressionMode;
import org.vstu.meaningtree.languages.configs.params.SkipErrors;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.definitions.ObjectConstructorDefinition;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
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
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.io.PrintCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;
import org.vstu.meaningtree.nodes.modules.*;
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
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.ArrayType;
import org.vstu.meaningtree.nodes.types.containers.DictionaryType;
import org.vstu.meaningtree.nodes.types.containers.ListType;
import org.vstu.meaningtree.nodes.types.containers.SetType;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.nodes.types.user.Class;
import org.vstu.meaningtree.nodes.types.user.GenericClass;
import org.vstu.meaningtree.utils.TreeSitterUtils;

import java.util.*;

public class JavaLanguage extends LanguageParser {
    private TSLanguage _language;
    private TSParser _parser;
    private final Map<String, UserType> _userTypes;

    public JavaLanguage() {
        _userTypes = new HashMap<>();
    }

    private void _initBackend() {
        if (_language == null) {
            _language = new TreeSitterJava();
            _parser = new TSParser();
            _parser.setLanguage(_language);
        }
    }

    @Override
    public TSTree getTSTree() {
        _initBackend();
        TSTree tree = _parser.parseString(null, _code);
        /*
        TODO: only for test
        try {
            tree.printDotGraphs(new File("TSTree.dot"));
        } catch (IOException e) { }
        */
        return tree;
    }

    public synchronized MeaningTree getMeaningTree(String code) {
        _code = code;
        TSNode rootNode = getRootNode();
        List<String> errors = lookupErrors(rootNode);
        if (!errors.isEmpty() && !getConfigParameter(SkipErrors.class).orElse(false)) {
            throw new UnsupportedParsingException(String.format("Given code has syntax errors: %s", errors));
        }

        Node node = parseTSNode(rootNode);
        if (node instanceof AssignmentExpression expr) {
            node = expr.toStatement();
        }

        return new MeaningTree(node);
    }

    @Override
    public TSNode getRootNode() {
        TSNode result = super.getRootNode();

        Optional<Boolean> maybeExpressionMode = _config.get(ExpressionMode.class);

        if (maybeExpressionMode.orElse(false)) {
            // В режиме выражений в код перед парсингом подставляется заглушка в виде точки входа
            TSNode cls = result.getNamedChild(0);

            if (!cls.getType().equals("class_declaration")) {
                throw new UnsupportedParsingException("Entry point class wasn't found");
            }

            TSNode clsbody = cls.getChildByFieldName("body");

            if (cls.getNamedChildCount() == 0) {
                throw new UnsupportedParsingException("Entry point class is empty");
            }

            TSNode func = clsbody.getNamedChild(0);

            if (!getCodePiece(func.getChildByFieldName("name")).equals("main")) {
                throw new UnsupportedParsingException("Entry point method wasn't found");
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

    @Override
    public MeaningTree getMeaningTree(TSNode node, String code) {
        _code = code;
        return new MeaningTree(parseTSNode(node));
    }

    protected Node fromTSNode(TSNode node) {
        Objects.requireNonNull(node);

        String nodeType = node.getType();
        Node createdNode = switch (nodeType) {
            case "ERROR" -> fromTSNode(node.getChild(0));
            case "program" -> fromProgramTSNode(node);
            case "block" -> fromBlockTSNode(node);
            case "statement" -> fromStatementTSNode(node);
            case "if_statement" -> fromIfStatementTSNode(node);
            case "condition" -> fromConditionTSNode(node);
            case "expression_statement" -> fromExpressionStatementTSNode(node);
            case "parenthesized_expression" -> fromParenthesizedExpressionTSNode(node);
            case "binary_expression" -> fromBinaryExpressionTSNode(node);
            case "unary_expression" -> fromUnaryExpressionTSNode(node);
            case "decimal_integer_literal", "binary_integer_literal", "hex_integer_literal" -> fromIntegerLiteralTSNode(node);
            case "decimal_floating_point_literal" -> fromFloatLiteralTSNode(node);
            case "local_variable_declaration" -> fromVariableDeclarationTSNode(node);
            case "for_statement" -> fromForStatementTSNode(node);
            case "assignment_expression" -> fromAssignmentExpressionTSNode(node);
            case "identifier" -> fromIdentifierTSNode(node);
            case "while_statement" -> fromWhileTSNode(node);
            case "update_expression" -> fromUpdateExpressionTSNode(node);
            case "package_declaration" -> fromPackageDeclarationTSNode(node);
            case "scoped_identifier" -> fromScopedIdentifierTSNode(node);
            case "class_declaration" -> fromClassDeclarationTSNode(node);
            case "field_declaration" -> fromFieldDeclarationTSNode(node);
            case "string_literal" -> fromStringLiteralTSNode(node);
            case "method_declaration" -> fromMethodDeclarationTSNode(node);
            case "switch_expression" -> fromSwitchExpressionTSNode(node);
            case "break_statement" -> fromBreakStatementTSNode(node);
            case "continue_statement" -> fromContinueStatementTSNode(node);
            case "null_literal" -> fromNullLiteralTSNode(node);
            case "import_declaration" -> fromImportDeclarationTSNode(node);
            case "method_invocation" -> fromMethodInvocation(node);
            case "object_creation_expression" -> fromObjectCreationExpressionTSNode(node);
            case "integral_type", "floating_point_type", "array_type", "generic_type" -> fromTypeTSNode(node);
            case "true", "false" -> fromBooleanValueTSNode(node);
            case "field_access" -> fromFieldAccessTSNode(node);
            case "array_creation_expression" -> fromArrayCreationExpressionTSNode(node);
            case "array_initializer" -> fromArrayInitializer(node);
            case "return_statement" -> fromReturnStatementTSNode(node);
            case "void_type", "type_identifier" -> fromTypeTSNode(node);
            case "line_comment", "block_comment" -> fromCommentTSNode(node);
            case "cast_expression" -> fromCastExpressionTSNode(node);
            case "array_access" -> fromArrayAccessTSNode(node);
            case "ternary_expression" -> fromTernaryExpressionTSNode(node);
            case "constructor_declaration" -> fromConstructorDeclarationTSNode(node);
            case "this" -> fromThisTSNode(node);
            case "character_literal" -> fromCharacterLiteralTSNode(node);
            case "do_statement" -> fromDoStatementTSNode(node);
            case "instanceof_expression" -> fromInstanceOfTSNode(node);
            case "class_literal" -> fromClassLiteralTSNode(node);
            case "enhanced_for_statement" -> fromEnhancedForStatementTSNode(node);
            default -> throw new UnsupportedParsingException(String.format("Can't parse %s this code:\n%s", node.getType(), getCodePiece(node)));
        };
        assignValue(node, createdNode);

        return createdNode;
    }

    private Node fromEnhancedForStatementTSNode(TSNode node) {
        Type type = (Type) parseTSNode(node.getChildByFieldName("type"));
        SimpleIdentifier iterVarId = (SimpleIdentifier) parseTSNode(node.getChildByFieldName("name"));
        Expression iterable = (Expression) parseTSNode(node.getChildByFieldName("value"));
        Statement body = (Statement) parseTSNode(node.getChildByFieldName("body"));

        VariableDeclaration varDecl = new VariableDeclaration(type, iterVarId);

        return new ForEachLoop(varDecl, iterable, body);
    }

    private Node fromClassLiteralTSNode(TSNode node) {
        return new MemberAccess(fromTypeTSNode(node.getNamedChild(0)), new SimpleIdentifier("class"));
    }

    private Node fromInstanceOfTSNode(TSNode node) {
        return new InstanceOfOp((Expression) parseTSNode(node.getChildByFieldName("left")), fromTypeTSNode(node.getChildByFieldName("right")));
    }

    private Node fromDoStatementTSNode(TSNode node) {
        Statement body = (Statement) parseTSNode(node.getChildByFieldName("body"));
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition"));
        if (condition instanceof ParenthesizedExpression parenthesizedExpression) {
            condition = parenthesizedExpression.getExpression();
        }
        return new DoWhileLoop(condition, body);
    }

    private CharacterLiteral fromCharacterLiteralTSNode(TSNode node) {
        String representation = getCodePiece(node);
        String withoutQuotes = representation.substring(1, representation.length() - 1);
        int value = StringEscapeUtils.unescapeJava(withoutQuotes).codePointAt(0);
        return new CharacterLiteral(value);
    }

    private SelfReference fromThisTSNode(TSNode node) {
        return new SelfReference("this");
    }

    private Node fromConstructorDeclarationTSNode(TSNode node) {
        List<DeclarationModifier> modifiers;
        List<Annotation> annotations = new ArrayList<>();
        if (node.getNamedChild(0).getType().equals("modifiers"))
            { modifiers = fromModifiers(annotations, node.getNamedChild(0)); }
        else
            { modifiers = List.of(); }
        Identifier name = fromIdentifierTSNode(node.getChildByFieldName("name"));
        List<DeclarationArgument> parameters = fromMethodParameters(node.getChildByFieldName("parameters"));
        CompoundStatement body = fromBlockTSNode(node.getChildByFieldName("body"));
        // TODO: определение класса, к которому принадлежит метод и считывание аннотаций
        var result = new ObjectConstructorDefinition(null, name, List.of(), modifiers, parameters, body);
        result.getDeclaration().setAnnotations(annotations);
        return result;
    }

    private Node fromTernaryExpressionTSNode(TSNode node) {
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition"));
        Expression consequence = (Expression) parseTSNode(node.getChildByFieldName("consequence"));
        Expression alternative = (Expression) parseTSNode(node.getChildByFieldName("alternative"));
        return new TernaryOperator(condition, consequence, alternative);
    }

    private List<Node> fromClassBody(TSNode node) {
        if (node.getNamedChild(0).getType().equals("block")) {
            node = node.getNamedChild(0);
        }
        ArrayList<Node> nodes = new ArrayList<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            nodes.add(parseTSNode(node.getNamedChild(i)));
        }
        return nodes;
    }

    private Node fromArrayAccessTSNode(TSNode node) {
        Identifier arrayName = fromIdentifierTSNode(node.getChildByFieldName("array"));
        Expression index = (Expression) parseTSNode(node.getChildByFieldName("index"));
        return new IndexExpression(arrayName, index);
    }

    private Node fromCastExpressionTSNode(TSNode node) {
        Type castType = fromTypeTSNode(node.getChildByFieldName("type"));
        Expression value = (Expression) parseTSNode(node.getChildByFieldName("value"));
        if (castType instanceof IntType && value instanceof ParenthesizedExpression p
                && p.getExpression() instanceof DivOp div) {
            return new FloorDivOp(div.getLeft(), div.getRight());
        }
        return new CastTypeExpression(castType, value);
    }

    private Comment fromCommentTSNode(TSNode node) {
        String comment = getCodePiece(node);
        return switch (node.getType()) {
            case "line_comment" -> Comment.fromUnescaped(comment.substring(2));
            case "block_comment" -> Comment.fromUnescaped(comment.substring(2, comment.length() - 2));
            default -> throw new IllegalArgumentException();
        };
    }

    private Node fromReturnStatementTSNode(TSNode node) {
        if (node.getNamedChildCount() == 0) {
            return new ReturnStatement();
        }

        Expression expression = (Expression) parseTSNode(node.getNamedChild(0));
        return new ReturnStatement(expression);
    }

    private int countArrayDimensions(TSNode dimensionsNode) {
        String dimensions = getCodePiece(dimensionsNode);
        return dimensions.split("\\u005b\\u005d", -1).length - 1;
    }

    private Shape getArrayShape(TSNode arrayCreationNode) {
        int dimensionsCount = 0;
        Map<Integer, Expression> dimensions = new HashMap<>();

        // Начинаем с первого ребенка, т.к. нужно пропустить type-ребенка
        LOOP: for (int i = 1; i < arrayCreationNode.getNamedChildCount(); i++) {
            TSNode dimension = arrayCreationNode.getNamedChild(i);

            switch (dimension.getType()) {
                case "dimensions_expr" -> {
                    Expression dimensionExpr = (Expression) parseTSNode(dimension.getNamedChild(0));
                    dimensions.put(dimensionsCount, dimensionExpr);
                    dimensionsCount += 1;
                }
                case "dimensions" -> {
                    dimensionsCount += countArrayDimensions(dimension);
                }
                case "array_initializer" -> {
                    break LOOP;
                }
                default -> throw new IllegalStateException("Unexpected value: " + dimension.getType());
            }
        }

        List<Expression> realDimensions = new ArrayList<>(dimensionsCount);
        for (int i = 0; i < dimensionsCount; i++) {
            realDimensions.add(i, dimensions.getOrDefault(i, null));
        }

        return new Shape(dimensionsCount, realDimensions);
    }

    private ArrayInitializer fromArrayInitializer(TSNode arrayInitializerNode) {
        List<Expression> values = new ArrayList<>();
        for (int i = 0; i < arrayInitializerNode.getNamedChildCount(); i++) {
            Expression value = (Expression) parseTSNode(arrayInitializerNode.getNamedChild(i));
            values.add(value);
        }
        return new ArrayInitializer(values);
    }

    private ArrayNewExpression fromArrayCreationExpressionTSNode(TSNode arrayCreationNode) {
        Type arrayType = fromTypeTSNode(arrayCreationNode.getChildByFieldName("type"));
        Shape arrayShape = getArrayShape(arrayCreationNode);
        ArrayInitializer initializer = null;
        if (!arrayCreationNode.getChildByFieldName("value").isNull()) {
            initializer = fromArrayInitializer(arrayCreationNode.getChildByFieldName("value"));
        }
        return new ArrayNewExpression(arrayType, arrayShape, initializer);
    }

    private MemberAccess fromFieldAccessTSNode(TSNode fieldAccess) {
        Expression object = (Expression) parseTSNode(fieldAccess.getChildByFieldName("object"));
        SimpleIdentifier member = (SimpleIdentifier) fromIdentifierTSNode(fieldAccess.getChildByFieldName("field"));
        return new MemberAccess(object, member);
    }

    private BoolLiteral fromBooleanValueTSNode(TSNode node) {
        String value = getCodePiece(node);
        return switch (value) {
            case "true" -> new BoolLiteral(true);
            case "false" -> new BoolLiteral(false);
            default -> throw new IllegalStateException("Unexpected value " + value);
        };
    }

    private Node fromObjectCreationExpressionTSNode(TSNode objectCreationNode) {
        Type type = fromTypeTSNode(objectCreationNode.getChildByFieldName("type"));
        TSNode body = objectCreationNode.getNamedChild(objectCreationNode.getNamedChildCount() - 1);

        List<Expression> arguments = new ArrayList<>();
        TSNode tsArguments = objectCreationNode.getChildByFieldName("arguments");
        for (int i = 0; i < tsArguments.getNamedChildCount(); i++) {
            TSNode tsArgument = tsArguments.getNamedChild(i);
            Expression argument = (Expression) parseTSNode(tsArgument);
            arguments.add(argument);
        }

        // Список
        if (type instanceof ListType && arguments.getFirst() instanceof MethodCall call
            && call.getFunctionName().equalsIdentifier("of")
        ) {
            return new ListLiteral(call.getArguments());
        }

        // Список в качестве тела класса
        if (type instanceof ListType && !body.isNull()) {
            ArrayList<Expression> list = new ArrayList<>();
            for (Node node : fromClassBody(body)) {
                if (node instanceof ExpressionStatement expr) {
                    node = expr.getExpression();
                }
                if (node instanceof FunctionCall call && call.getFunction().equalsIdentifier("add") && !call.getArguments().isEmpty()) {
                    list.add(call.getArguments().getFirst());
                } else if (node instanceof MethodCall call
                        && call.getObject() instanceof SelfReference
                        && call.getFunctionName().equalsIdentifier("add") && !call.getArguments().isEmpty()) {
                    list.add(call.getArguments().getFirst());
                }
            }
            return new ListLiteral(list);
        }

        // Словарь
        if (type instanceof DictionaryType && !body.isNull()) {
            LinkedHashMap<Expression, Expression> map = new LinkedHashMap<>();
            for (Node node : fromClassBody(body)) {
                if (node instanceof ExpressionStatement expr) {
                    node = expr.getExpression();
                }
                if (node instanceof FunctionCall call && call.getFunction().equalsIdentifier("put") && call.getArguments().size() >= 2) {
                    map.put(call.getArguments().getFirst(), call.getArguments().get(1));
                } else if (node instanceof MethodCall call
                        && call.getObject() instanceof SelfReference
                        && call.getFunctionName().equalsIdentifier("add") && call.getArguments().size() >= 2) {
                    map.put(call.getArguments().getFirst(), call.getArguments().get(1));
                }
            }
            return new DictionaryLiteral(map);
        }

        // Множества
        if (type instanceof SetType && !body.isNull()) {
            List<Expression> list = new ArrayList<>();
            for (Node node : fromClassBody(body)) {
                if (node instanceof ExpressionStatement expr) {
                    node = expr.getExpression();
                }
                if (node instanceof FunctionCall call && call.getFunction().equalsIdentifier("add") && !call.getArguments().isEmpty()) {
                    list.add(call.getArguments().getFirst());
                } else if (node instanceof MethodCall call
                        && call.getObject() instanceof SelfReference
                        && call.getFunctionName().equalsIdentifier("add") && !call.getArguments().isEmpty()) {
                    list.add(call.getArguments().getFirst());
                }
            }
            return new SetLiteral(list);
        }

        return new ObjectNewExpression(type, arguments);
    }

    @NotNull
    private PrintCommand makePrintCall(String outObjectMethodName, TSNode tsNodeArguments) {
        List<Expression> arguments = new ArrayList<>();
        for (int i = 0; i < tsNodeArguments.getNamedChildCount(); i++) {
            TSNode tsArgument = tsNodeArguments.getNamedChild(i);
            Expression argument = (Expression) parseTSNode(tsArgument);
            arguments.add(argument);
        }

        return switch (outObjectMethodName) {
            case "println" -> {
                if (arguments.size() > 1) {
                    throw new IllegalArgumentException("\"println\" cannot have more than one argument");
                }

                yield new PrintValues
                        .PrintValuesBuilder()
                        .endWithNewline()
                        .setValues(arguments)
                        .build();
            }
            case "print" -> {
                if (arguments.size() != 1) {
                    throw new IllegalArgumentException("\"println\" can have only one argument");
                }

                yield new PrintValues
                        .PrintValuesBuilder()
                        .setValues(arguments)
                        .build();
            }

            default -> throw new IllegalStateException("Unexpected out object method: " + outObjectMethodName);
        };
    }

    @NotNull
    private Node fromMethodInvocation(@NotNull TSNode methodInvocation) {
        TSNode objectNode = methodInvocation.getChildByFieldName("object");
        TSNode nameNode = methodInvocation.getChildByFieldName("name");
        TSNode argumentsNode = methodInvocation.getChildByFieldName("arguments");

        String objectMethodName = getCodePiece(nameNode);
        if (!objectNode.isNull()) {
            String objectName = getCodePiece(objectNode);
            if (objectName.equals("System.out")
                    && (objectMethodName.equals("println") || objectMethodName.equals("print"))) {
                return makePrintCall(objectMethodName, argumentsNode);
            }
            if (objectName.equals("Math") && objectMethodName.equals("pow") && argumentsNode.getNamedChildCount() == 2) {
                return new PowOp(
                        (Expression) parseTSNode(argumentsNode.getNamedChild(0)),
                        (Expression) parseTSNode(argumentsNode.getNamedChild(1))
                );
            }
        }

        if (objectNode.isNull() && objectMethodName.equals("pow") && argumentsNode.getNamedChildCount() == 2) {
            return new PowOp(
                    (Expression) parseTSNode(argumentsNode.getNamedChild(0)),
                    (Expression) parseTSNode(argumentsNode.getNamedChild(1))
            );
        }

        Identifier methodName = fromIdentifierTSNode(nameNode);
        if (!(methodName instanceof SimpleIdentifier)) {
            throw new UnsupportedParsingException("methodName must be simple identifier");
        }

        List<Expression> arguments = new ArrayList<>();
        for (int i = 0; i < argumentsNode.getNamedChildCount(); i++) {
            TSNode tsArgument = argumentsNode.getNamedChild(i);
            Expression argument = (Expression) parseTSNode(tsArgument);
            arguments.add(argument);
        }

        if (objectNode.isNull()) {
            return new FunctionCall(methodName, arguments);
        }

        Expression object = (Expression) parseTSNode(objectNode);
        return new MethodCall(object, (SimpleIdentifier) methodName, arguments);
    }

    private boolean isStaticImport(TSNode importDeclaration) {
        for (int i = 0; i < importDeclaration.getChildCount(); i++) {
            if (importDeclaration.getChild(i).getType().equals("static")) {
                return true;
            }
        }
        return false;
    }

    private boolean isWildcardImport(TSNode importDeclaration) {
        for (int i = 0; i < importDeclaration.getChildCount(); i++) {
            if (importDeclaration.getChild(i).getType().equals("asterisk")) {
                return true;
            }
        }
        return false;
    }

    private Node fromImportDeclarationTSNode(TSNode importDeclaration) {
        TSNode scopeNode = importDeclaration.getNamedChild(0);

        if (isStaticImport(importDeclaration)) {
            if (isWildcardImport(importDeclaration)) {
                Identifier scope = fromIdentifierTSNode(scopeNode);
                return new StaticImportAll(scope);
            }
            else {
                Identifier scope = fromIdentifierTSNode(scopeNode.getChildByFieldName("scope"));
                Identifier member = fromIdentifierTSNode(scopeNode.getChildByFieldName("name"));
                return new StaticImportMembersFromModule(scope, member);
            }
        }
        else if (isWildcardImport(importDeclaration)) {
            Identifier scope = fromIdentifierTSNode(scopeNode);
            return new ImportAllFromModule(scope);
        }
        else {
            Identifier scope = fromIdentifierTSNode(scopeNode.getChildByFieldName("scope"));
            Identifier member = fromIdentifierTSNode(scopeNode.getChildByFieldName("name"));
            return new ImportMembersFromModule(scope, member);
        }
    }

    private Node fromNullLiteralTSNode(TSNode nullLiteral) {
        return new NullLiteral();
    }

    private Node fromContinueStatementTSNode(TSNode continueNode) {
        return new ContinueStatement();
    }

    private Node fromBreakStatementTSNode(TSNode breakNode) {
        return new BreakStatement();
    }

    private CaseBlock fromSwitchGroupTSNode(TSNode switchGroup) {
        Expression matchValue =
                (Expression) parseTSNode(switchGroup.getNamedChild(0).getNamedChild(0));

        var statements = new ArrayList<Node>();
        for (int i = 1; i < switchGroup.getNamedChildCount(); i++) {
            statements.add(parseTSNode(switchGroup.getNamedChild(i)));
        }

        CaseBlock caseBlock;
        if (!statements.isEmpty() && statements.getLast() instanceof BreakStatement) {
            caseBlock = new BasicCaseBlock(
                    matchValue,
                    new CompoundStatement(statements.subList(0, statements.size() - 1))
            );
        }
        else {
            caseBlock = new FallthroughCaseBlock(
                    matchValue,
                    new CompoundStatement(statements)
            );
        }

        return caseBlock;
    }

    private Node fromSwitchExpressionTSNode(TSNode switchNode) {
        Expression matchValue =
                (Expression) parseTSNode(switchNode.getChildByFieldName("condition").getNamedChild(0));

        DefaultCaseBlock defaultCaseBlock = null;
        List<CaseBlock> cases = new ArrayList<>();

        TSNode switchBlock = switchNode.getChildByFieldName("body");
        for (int i = 0; i < switchBlock.getNamedChildCount(); i++) {
            TSNode switchGroup = switchBlock.getNamedChild(i);

            String labelName = getCodePiece(switchGroup.getNamedChild(0));
            if (labelName.equals("default")) {
                var statements = new ArrayList<Node>();

                for (int j = 0; j < switchGroup.getNamedChildCount(); j++) {
                    if (switchGroup.getNamedChild(j).getType().equals("switch_label")) {
                        continue;
                    }

                    statements.add(parseTSNode(switchGroup.getNamedChild(j)));
                }

                if (!statements.isEmpty() && statements.getLast() instanceof BreakStatement) {
                    statements.removeLast();
                }

                defaultCaseBlock = new DefaultCaseBlock(new CompoundStatement(statements));
            }
            else {
                CaseBlock caseBlock = fromSwitchGroupTSNode(switchGroup);
                cases.add(caseBlock);
            }
        }

        return new SwitchStatement(matchValue, cases, defaultCaseBlock);
    }

    private Definition fromMethodDeclarationTSNode(TSNode node) {
        List<DeclarationModifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        if (node.getChild(0).getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, node.getChild(0)));
        }

        Type returnType = fromTypeTSNode(node.getChildByFieldName("type"));
        Identifier identifier = fromScopedIdentifierTSNode(node.getChildByFieldName("name"));
        List<DeclarationArgument> parameters = fromMethodParameters(node.getChildByFieldName("parameters"));

        CompoundStatement body = fromBlockTSNode(node.getChildByFieldName("body"));
        Definition definition;

        if (modifiers.size() == 2
                && modifiers.contains(DeclarationModifier.STATIC)
                && modifiers.contains(DeclarationModifier.PUBLIC)
        ) {
            var functionDeclaration = new FunctionDeclaration(
                    identifier,
                    returnType,
                    annotations,
                    parameters
            );
            definition = new FunctionDefinition(functionDeclaration, body);
        }
        else {
            // TODO: Пока не реализован механизм нахождения класса, к которому принадлежит метод, и определение аннотаций
            var methodDeclaration = new MethodDeclaration(
                    null,
                    identifier,
                    returnType,
                    annotations,
                    modifiers,
                    parameters
            );
            definition = new MethodDefinition(methodDeclaration, body);
        }

        return definition;
    }

    private List<DeclarationArgument> fromMethodParameters(TSNode node) {
        List<DeclarationArgument> parameters = new ArrayList<>();

        for (int i = 0; i < node.getChildCount(); i++) {
            // TODO: может быть можно как-то более эффективно извлекать извлечь параметры...
            // Такие сложности из-за того, что в детях также будет скобки, запятые и другие
            // синтаксические артефакты.
            TSNode child = node.getChild(i);
            if (!child.getType().equals("formal_parameter")) {
                continue;
            }

            DeclarationArgument parameter = fromFormalParameter(child);
            parameters.add(parameter);
        }

        return parameters;
    }

    private DeclarationArgument fromFormalParameter(TSNode node) {
        if (node.getType().equals("spread_parameter")) {
            Type type = fromTypeTSNode(node.getNamedChild(0));
            SimpleIdentifier name = (SimpleIdentifier) fromIdentifierTSNode(node.getNamedChild(1)
                    .getChildByFieldName("name"));
            return DeclarationArgument.listUnpacking(type, name);
        }
        Type type = fromTypeTSNode(node.getChildByFieldName("type"));
        SimpleIdentifier name = (SimpleIdentifier) fromIdentifierTSNode(node.getChildByFieldName("name"));
        return new DeclarationArgument(type, name,null);
    }

    private StringLiteral fromStringLiteralTSNode(TSNode node) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode child = node.getNamedChild(i);
            builder.append(getCodePiece(child));
        }

        return StringLiteral.fromEscaped(builder.toString(), StringLiteral.Type.NONE);
    }

    private FieldDeclaration fromFieldDeclarationTSNode(TSNode node) {
        int currentChildIndex = 0;

        List<Annotation> annotations = new ArrayList<>();
        List<DeclarationModifier> modifiers = new ArrayList<>();
        if (node.getChild(currentChildIndex).getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, node.getChild(currentChildIndex)));
        }

        VariableDeclaration declaration = fromVariableDeclarationTSNode(node);
        var result = new FieldDeclaration(declaration.getType(), modifiers, declaration.getDeclarators());
        result.setAnnotations(annotations);
        return result;
    }

    private List<DeclarationModifier> fromModifiers(List<Annotation> annotations, TSNode node) {
        // Внутри происходит считывание лишь модификаторов области видимости,
        // причем допускается всего лишь 1 или 0 идентификаторов (несмотря на список).
        // Должно ли так быть - неизвестно, нужно разобраться...
        List<DeclarationModifier> modifiers = new ArrayList<>();

        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChild(i).getType().equals("marker_annotation")) {
                annotations.add(new Annotation(
                        StringLiteral.fromUnescaped(TreeSitterUtils.getCodePiece(_code, node.getChild(i)), StringLiteral.Type.NONE))
                );
                continue;
            }

            modifiers.add(
                    switch (node.getChild(i).getType()) {
                        case "public" -> DeclarationModifier.PUBLIC;
                        case "private" -> DeclarationModifier.PRIVATE;
                        case "protected" -> DeclarationModifier.PROTECTED;
                        case "abstract" -> DeclarationModifier.ABSTRACT;
                        case "final" -> DeclarationModifier.CONST;
                        case "static" -> DeclarationModifier.STATIC;
                        default -> throw new IllegalArgumentException("Unknown identifier: %s".formatted(node.getChild(i).getType()));
                    }
            );
        }

        return modifiers;
    }

    private ClassDefinition fromClassDeclarationTSNode(TSNode node) {
        int currentChildIndex = 0;

        List<DeclarationModifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        if (node.getChild(currentChildIndex).getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, node.getChild(currentChildIndex)));
            currentChildIndex++;
        }

        // Скипаем слово "class"
        currentChildIndex++;

        Identifier className = fromIdentifierTSNode(node.getChild(currentChildIndex));
        currentChildIndex++;

        // Парсим тело класса как блочное выражение... Правильно ли? Кто знает...
        CompoundStatement classBody = fromBlockTSNode(node.getChild(currentChildIndex));
        currentChildIndex++;

        ClassDeclaration decl = new ClassDeclaration(modifiers, className);
        // TODO: нужно поменять getNodes() у CompoundStatement, чтобы он не массив возвращал
        ClassDefinition def = new ClassDefinition(decl, classBody);
        def.getDeclaration().setAnnotations(annotations);
        return def;
    }

    private ScopedIdentifier fromScopedIdentifierTSNode(TSNode node) {
        TSNode scope = node;

        List<SimpleIdentifier> scopes = new ArrayList<>();
        while (scope.getType().equals("scoped_identifier")) {
            scopes.add((SimpleIdentifier) fromIdentifierTSNode(scope.getChildByFieldName("name")));
            scope = scope.getChildByFieldName("scope");
        }
        scopes.add((SimpleIdentifier) fromIdentifierTSNode(scope));

        return new ScopedIdentifier(scopes.reversed());
    }

    private PackageDeclaration fromPackageDeclarationTSNode(TSNode node) {
        TSNode packageName = node.getChild(1);
        return new PackageDeclaration(fromIdentifierTSNode(packageName));
    }

    @NotNull
    private UnaryExpression fromUpdateExpressionTSNode(@NotNull TSNode node) {
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

    private Identifier fromIdentifierTSNode(TSNode node) {
        if (node.getType().equals("scoped_identifier")) {
            return fromScopedIdentifierTSNode(node);
        }
        String variableName = getCodePiece(node);
        return new SimpleIdentifier(variableName);
    }

    private AssignmentExpression fromAssignmentExpressionTSNode(TSNode node) {
        String variableName = getCodePiece(node.getChildByFieldName("left"));
        SimpleIdentifier identifier = new SimpleIdentifier(variableName);
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
            case "<<=", "<<<=" -> AugmentedAssignmentOperator.BITWISE_SHIFT_LEFT;
            case ">>=", ">>>=" -> AugmentedAssignmentOperator.BITWISE_SHIFT_RIGHT;
            case "%=" -> AugmentedAssignmentOperator.MOD;
            default -> throw new IllegalStateException("Unexpected augmented assignment type: " + operatorType);
        };

        return new AssignmentExpression(identifier, right, augmentedAssignmentOperator);
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

        if (update != null) {
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
        }

        if (start != null && stop != null && step != null && loopVariable != null) {
            Range.Type type = Range.Type.UNKNOWN;
            if (step instanceof IntegerLiteral integer) {
                if (integer.getLongValue() > 0) {
                    type = Range.Type.UP;
                } else {
                    type = Range.Type.DOWN;
                }
            } else if (step instanceof UnaryPlusOp) {
                type = Range.Type.UP;
            } else if (step instanceof UnaryMinusOp) {
                type = Range.Type.DOWN;
            }
            Range range = new Range(start, stop, step, false, isExcludingEnd, type);
            return new RangeForLoop(range, loopVariable, body);
        }

        return null;
    }

    private Loop fromForStatementTSNode(TSNode node) {
        HasInitialization init = null;
        Expression condition = null;
        Expression update = null;

        if (!node.getChildByFieldName("init").isNull()) {
            List<TSNode> assignments = getChildrenByFieldName(node, "init");

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

            if (condition instanceof ParenthesizedExpression parenthesizedExpression) {
                condition = parenthesizedExpression.getExpression();
            }
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

    private LoopType getLoopType(TSNode node) {
        return switch (node.getType()) {
            case "enhanced_for_statement", "for_statement" -> LoopType.FOR;
            case "while_statement" -> LoopType.WHILE;
            case "do_statement" -> LoopType.DO_WHILE;
            default -> throw new UnsupportedParsingException(String.format("Can't parse %s this code:\n%s", node.getType(), getCodePiece(node)));
        };
    }

    private VariableDeclarator fromVariableDeclarator(TSNode node, Type type) {
        String name = getCodePiece(node.getChildByFieldName("name"));
        SimpleIdentifier ident = new SimpleIdentifier(name);

        if (!node.getChildByFieldName("value").isNull()) {
            Expression value = (Expression) parseTSNode(node.getChildByFieldName("value"));
            if (value instanceof PlainCollectionLiteral col) {
                col.setTypeHint(type);
            }
            return new VariableDeclarator(ident, value);
        }
        else {
            return new VariableDeclarator(ident);
        }
    }

    private Type fromTypeTSNode(TSNode node) {
        String type = node.getType();
        String typeName = getCodePiece(node);
        Type parsedType = null;

        switch (type) {
            case "integral_type":
                parsedType = switch(typeName) {
                    case "char" -> new CharacterType();
                    case "int", "short", "long", "byte" -> new IntType();
                    default -> throw new IllegalStateException("Unexpected value: " + typeName);
                };
                break;
            case "floating_point_type":
                parsedType = new FloatType(typeName.equals("double") ? 64 : 32);
                break;
            case "boolean_type":
                parsedType = new BooleanType();
                break;
            case "array_type":
                Type baseType = fromTypeTSNode(node.getChildByFieldName("element"));
                TSNode dimensions = node.getChildByFieldName("dimensions");
                int dimensionsCount = countArrayDimensions(dimensions);
                parsedType = new ArrayType(baseType, dimensionsCount);
                break;
            case "void_type":
                parsedType = new NoReturn();
                break;
            case "type_identifier":
                switch (typeName) {
                    case "String" -> parsedType = new StringType();
                    case "Object" -> parsedType = new UnknownType();
                    case "Integer" -> parsedType = new IntType(32);
                    case "Byte" -> parsedType = new IntType(8);
                    case "Short" -> parsedType = new IntType(16);
                    case "Long" -> parsedType =  new IntType(64);
                    case "Float" -> parsedType = new FloatType(32);
                    case "Double" -> parsedType = new FloatType(64);
                    case "Boolean" -> parsedType = new BooleanType();
                    case "Character" -> parsedType = new CharacterType();
                    default -> {
                        if (!_userTypes.containsKey(typeName)) {
                            _userTypes.put(typeName, new Class(new SimpleIdentifier(typeName)));
                        }
                        parsedType = _userTypes.get(typeName);
                    }
                }
                break;
            case "generic_type":
                TSNode typeNode = node.getNamedChild(0);
                TSNode arguments = node.getNamedChild(1);

                ArrayList<Type> subTypes = new ArrayList<>();
                for (int i = 0; i < arguments.getNamedChildCount(); i++) {
                    subTypes.add(fromTypeTSNode(arguments.getNamedChild(i)));
                }

                Type subType = fromTypeTSNode(typeNode);
                if (subType instanceof ListType) {
                    parsedType = new ListType(!subTypes.isEmpty() ? subTypes.getFirst() : new UnknownType());
                } else if (subType instanceof DictionaryType) {
                    parsedType = new DictionaryType(
                            !subTypes.isEmpty() ? subTypes.getFirst() : new UnknownType(),
                            subTypes.size() > 1 ? subTypes.get(1) : new UnknownType()
                    );
                } else if (subType instanceof SetType) {
                    parsedType = new SetType(!subTypes.isEmpty() ? subTypes.getFirst() : new UnknownType());
                } else if (subType instanceof Class cls) {
                    parsedType = new GenericClass(cls.getQualifiedName(), subTypes.toArray(new Type[0]));
                }
                break;
            case "scoped_type_identifier":
                ScopedIdentifier idents = fromScopedTypeIdentifier(node);
                parsedType = switch (idents.getScopeResolution().getLast().toString()) {
                    case "ArrayList", "List" -> new ListType(new UnknownType());
                    case "TreeMap", "HashMap", "Map", "OrderedMap" -> new DictionaryType(new UnknownType(), new UnknownType());
                    case "Set", "HashSet" -> new SetType(new UnknownType());
                    case "Integer" -> new IntType(32);
                    case "Byte" -> new IntType(8);
                    case "Short" -> new IntType(16);
                    case "Long" -> new IntType(64);
                    case "Float" -> new FloatType(32);
                    case "Double" -> new FloatType(64);
                    case "Boolean" -> new BooleanType();
                    default -> {
                        UserType t = new Class(idents);
                        if (!_userTypes.containsKey(typeName)) {
                            _userTypes.put(typeName, t);
                        }
                        yield t;
                    }
                };
                break;
            default:
                throw new IllegalStateException("Unexpected type: " + typeName);
        }

        return parsedType;
    }

    private ScopedIdentifier fromScopedTypeIdentifier(TSNode node) {
        ArrayList<SimpleIdentifier> idents = new ArrayList<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            if (node.getNamedChild(i).getType().equals("scoped_type_identifier")) {
                idents.addAll(fromScopedTypeIdentifier(node.getNamedChild(i)).getScopeResolution());
            } else if (node.getNamedChild(i).getType().equals("type_identifier")){
                idents.add(new SimpleIdentifier(getCodePiece(node.getNamedChild(i))));
            } else {
                idents.add((SimpleIdentifier) parseTSNode(node.getNamedChild(i)));
            }
        }
        return new ScopedIdentifier(idents);
    }

    private VariableDeclaration fromVariableDeclarationTSNode(TSNode node) {
        List<DeclarationModifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        TSNode possibleModifiers = node.getChild(0);
        if (possibleModifiers.getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, possibleModifiers));
        }

        Type type = fromTypeTSNode(node.getChildByFieldName("type"));

        if (modifiers.contains(DeclarationModifier.CONST)) {
            type.setConst(true);
        }

        List<VariableDeclarator> declarators = new ArrayList<>();

        TSQuery all_declarators = new TSQuery(_language, "(variable_declarator) @decls");
        TSQueryCursor cursor = new TSQueryCursor();
        cursor.exec(all_declarators, node);
        TSQueryMatch match = new TSQueryMatch();
        while (cursor.nextMatch(match)) {
            TSQueryCapture capture = match.getCaptures()[0];
            VariableDeclarator decl = fromVariableDeclarator(capture.getNode(), type);
            declarators.add(decl);
        }
        //while (cursor.nextMatch(match)) {
        //    System.out.println(match.getCaptures());
        //}

        if (!modifiers.isEmpty()) {
            var result = new FieldDeclaration(type, modifiers, declarators);
            result.setAnnotations(annotations);
            return result;
        }

        var decl = new VariableDeclaration(type, declarators);
        decl.setAnnotations(annotations);
        return decl;
    }

    private Node fromProgramTSNode(TSNode node) {
        var statements = new ArrayList<Node>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            statements.add(parseTSNode(node.getNamedChild(i)));
        }

        ClassDefinition mainClass = null;
        MethodDefinition mainMethod = null;
        for (Node n : statements) {
            // Только один класс в файле может иметь модификатор public,
            // поэтому он и является главным классом
            if (n instanceof ClassDefinition classDefinition
                    && classDefinition.getModifiers().contains(DeclarationModifier.PUBLIC)) {
                mainClass = classDefinition;

                MethodDefinition m = mainClass.findMethod("main");
                if (m != null) {
                    mainMethod = m;
                }
            }
        }

        /*
        Node[] nodes = builder.getCurrentNodes();
        if (
                (nodes.length > 1 && getConfigParameter("expressionMode").getBooleanValue())
                        || (nodes.length > 0 && !(nodes[0] instanceof ExpressionStatement) &&
                        !(nodes[0] instanceof AssignmentStatement) &&
                        !(nodes[0] instanceof Expression) && getConfigParameter("expressionMode").getBooleanValue()
                        )
        ) {
            throw new UnsupportedParsingException("Cannot parse the code as expression in expression mode");
        }

        */

        List<Node> body = new ArrayList<>();

        if (mainMethod != null) {
            body = Arrays.asList(mainMethod.getBody().getNodes());
        } else if (getConfigParameter(EnforceEntryPoint.class).orElse(false)) {
            body = statements;
        }

        return new ProgramEntryPoint(body, mainClass, mainMethod);
    }

    private Loop fromWhileTSNode(TSNode node) {
        TSNode tsCond = node.getChildByFieldName("condition");
        Expression mtCond = (Expression) parseTSNode(tsCond);
        if (mtCond instanceof ParenthesizedExpression parenthesizedExpression) {
            mtCond = parenthesizedExpression.getExpression();
        }

        TSNode tsBody = node.getChildByFieldName("body");
        Statement mtBody = (Statement) parseTSNode(tsBody);

        if (mtCond instanceof BoolLiteral boolLiteral && boolLiteral.getValue()) {
            return new InfiniteLoop(mtBody, getLoopType(node));
        }

        return new WhileLoop(mtCond, mtBody);
    }

    private CompoundStatement fromBlockTSNode(TSNode node) {
        var statements = new ArrayList<Node>();
        for (int i = 1; i < node.getChildCount() - 1; i++) {
            statements.add(parseTSNode(node.getChild(i)));
        }
        return new CompoundStatement(statements);
    }

    private Node fromStatementTSNode(TSNode node) {
        return parseTSNode(node.getChild(0));
    }

    private IfStatement fromIfStatementTSNode(TSNode node) {
        // Берем ребенка под индексом 1, чтобы избежать захвата скобок, а значит
        // неправильного парсинга (получаем выражение в скобках в качестве условия, а не просто выражение)
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition").getChild(1));
        Statement consequence = (Statement) parseTSNode(node.getChildByFieldName("consequence"));

        TSNode alternativeNode = node.getChildByFieldName("alternative");
        if (alternativeNode.isNull()) {
            return new IfStatement(condition, consequence);
        }

        Statement alternative = (Statement) parseTSNode(alternativeNode);
        return new IfStatement(condition, consequence, alternative);
    }

    private Node fromConditionTSNode(TSNode node) {
        // TODO: Что-то сделать с этим...
        // У condition дети: '(', 'binary_expression', ')'
        // По имени binary_expression почему-то получить не удалось
        return parseTSNode(node.getChild(1));
    }

    private Statement fromExpressionStatementTSNode(TSNode node) {
        Expression expr = (Expression) parseTSNode(node.getChild(0));
        if (expr instanceof AssignmentExpression assignmentExpression) {
            return assignmentExpression.toStatement();
        }

        return new ExpressionStatement(expr);
    }

    private ParenthesizedExpression fromParenthesizedExpressionTSNode(TSNode node) {
        Expression expr = (Expression) parseTSNode(node.getChild(1));
        return new ParenthesizedExpression(expr);
    }

    private IntegerLiteral fromIntegerLiteralTSNode(TSNode node) {
        String value = getCodePiece(node);
        return new IntegerLiteral(value, false, false);
    }

    private FloatLiteral fromFloatLiteralTSNode(TSNode node) {
        String value = getCodePiece(node);
        return new FloatLiteral(value);
    }

    private UnaryExpression fromUnaryExpressionTSNode(TSNode node) {
        Expression argument = (Expression) parseTSNode(node.getChildByFieldName("operand"));
        TSNode operation = node.getChildByFieldName("operator");
        return switch (getCodePiece(operation)) {
            case "!" -> new NotOp(argument);
            case "~" -> new InversionOp(argument);
            case "-" -> new UnaryMinusOp(argument);
            case "+" -> new UnaryPlusOp(argument);
            default -> throw new UnsupportedOperationException();
        };
    }

    private BinaryExpression fromBinaryExpressionTSNode(TSNode node) {
        Expression left = (Expression) parseTSNode(node.getChildByFieldName("left"));
        Expression right = (Expression) parseTSNode(node.getChildByFieldName("right"));
        TSNode operator = node.getChildByFieldName("operator");

        return switch (getCodePiece(operator)) {
            case "+" -> new AddOp(left, right);
            case "-" -> new SubOp(left, right);
            case "*" -> new MulOp(left, right);
            case "/" -> new DivOp(left, right);
            case "%" -> new ModOp(left, right);
            case "<" -> new LtOp(left, right);
            case ">" -> new GtOp(left, right);
            case "==" -> new EqOp(left, right);
            case "!=" -> new NotEqOp(left, right);
            case ">=" -> new GeOp(left, right);
            case "<=" -> new LeOp(left, right);
            case "&&" -> new ShortCircuitAndOp(left, right);
            case "||" -> new ShortCircuitOrOp(left, right);
            case "&" -> new BitwiseAndOp(left, right);
            case "|" -> new BitwiseOrOp(left, right);
            case "^" -> new XorOp(left, right);
            case "<<", "<<<" -> new LeftShiftOp(left, right);
            case ">>", ">>>" -> new RightShiftOp(left, right);
            default -> throw new UnsupportedOperationException(String.format("Can't parse operator %s", getCodePiece(operator)));
        };
    }

}
