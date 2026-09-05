package org.vstu.meaningtree.languages;

import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.treesitter.*;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.*;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;
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
import org.vstu.meaningtree.nodes.interfaces.HasVariableDeclaration;
import org.vstu.meaningtree.nodes.io.PrintCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ResourceContextStatement;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.BasicCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.DefaultCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.FallthroughCaseBlock;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.nodes.types.user.Class;
import org.vstu.meaningtree.nodes.types.user.GenericClass;
import org.vstu.meaningtree.nodes.types.user.Interface;
import org.vstu.meaningtree.utils.analysis.imports.ImportResolver;
import org.vstu.meaningtree.utils.analysis.imports.JavaImportResolver;
import org.vstu.meaningtree.utils.analysis.imports.JavaLibraryImportRegistry;
import org.vstu.meaningtree.utils.analysis.types.JavaTypeConversionSemantics;
import org.vstu.meaningtree.utils.modules.ImportPathConverter;
import org.vstu.meaningtree.utils.scopes.ScopeLookupMode;

import java.util.*;

public class JavaParser extends LanguageParser {
    private final Map<String, UserType> _userTypes;

    public JavaParser(LanguageTranslator translator) {
        super(translator, new TreeSitterJava());
        _userTypes = new HashMap<>();
        configureTsNodeHandlers();
    }

    private static final LanguageBehavior BEHAVIOR = LanguageBehavior.defaults()
            .withTypeConversionSemantics(new JavaTypeConversionSemantics());

    /** От умолчаний язык отходит только в правилах преобразования примитивных типов. */
    @Override
    protected LanguageBehavior languageBehavior() {
        return BEHAVIOR;
    }

    private void configureTsNodeHandlers() {
        registerTSNodeHandler("ERROR", Node.class, node -> parseTSNode(node.getChild(0)));
        registerTSNodeHandler("program", ProgramEntryPoint.class, this::fromProgramTSNode);
        registerTSNodeHandler("block", CompoundStatement.class, this::fromBlockTSNode);
        registerTSNodeHandler("statement", Node.class, this::fromStatementTSNode);
        registerTSNodeHandler("if_statement", IfStatement.class, this::fromIfStatementTSNode);
        registerTSNodeHandler("condition", Node.class, this::fromConditionTSNode);
        registerTSNodeHandler("expression_statement", Statement.class, this::fromExpressionStatementTSNode);
        registerTSNodeHandler("parenthesized_expression", ParenthesizedExpression.class, this::fromParenthesizedExpressionTSNode);
        registerTSNodeHandler("binary_expression", BinaryExpression.class, this::fromBinaryExpressionTSNode);
        registerTSNodeHandler("unary_expression", UnaryExpression.class, this::fromUnaryExpressionTSNode);
        registerTSNodeHandler(List.of("decimal_integer_literal", "binary_integer_literal", "hex_integer_literal"), IntegerLiteral.class, this::fromIntegerLiteralTSNode);
        registerTSNodeHandler("decimal_floating_point_literal", FloatLiteral.class, this::fromFloatLiteralTSNode);
        registerTSNodeHandler("local_variable_declaration", VariableDeclaration.class, this::fromVariableDeclarationTSNode);
        registerTSNodeHandler("for_statement", Loop.class, this::fromForStatementTSNode);
        registerTSNodeHandler("assignment_expression", AssignmentExpression.class, this::fromAssignmentExpressionTSNode);
        registerTSNodeHandler("identifier", Identifier.class, this::fromIdentifierTSNode);
        registerTSNodeHandler("while_statement", Loop.class, this::fromWhileTSNode);
        registerTSNodeHandler("update_expression", UnaryExpression.class, this::fromUpdateExpressionTSNode);
        registerTSNodeHandler("package_declaration", PackageDeclaration.class, this::fromPackageDeclarationTSNode);
        registerTSNodeHandler("scoped_identifier", ScopedIdentifier.class, this::fromScopedIdentifierTSNode);
        registerTSNodeHandler("class_declaration", ClassDefinition.class, this::fromClassDeclarationTSNode);
        registerTSNodeHandler("interface_declaration", InterfaceDefinition.class, this::fromInterfaceDeclarationTSNode);
        registerTSNodeHandler("enum_declaration", EnumDeclaration.class, this::fromEnumDeclarationTSNode);
        registerTSNodeHandler("field_declaration", FieldDeclaration.class, this::fromFieldDeclarationTSNode);
        registerTSNodeHandler("string_literal", StringLiteral.class, this::fromStringLiteralTSNode);
        registerTSNodeHandler("method_declaration", Node.class, this::fromMethodDeclarationTSNode);
        registerTSNodeHandler("switch_expression", SwitchStatement.class, this::fromSwitchExpressionTSNode);
        registerTSNodeHandler("break_statement", BreakStatement.class, this::fromBreakStatementTSNode);
        registerTSNodeHandler("continue_statement", ContinueStatement.class, this::fromContinueStatementTSNode);
        registerTSNodeHandler("null_literal", NullLiteral.class, this::fromNullLiteralTSNode);
        registerTSNodeHandler("import_declaration", ImportModule.class, this::fromImportDeclarationTSNode);
        registerTSNodeHandler("method_invocation", Expression.class, this::fromMethodInvocation);
        registerTSNodeHandler("object_creation_expression", Expression.class, this::fromObjectCreationExpressionTSNode);
        registerTSNodeHandler(List.of("integral_type", "floating_point_type", "array_type", "generic_type"), Type.class, this::fromTypeTSNode);
        registerTSNodeHandler(List.of("true", "false"), BoolLiteral.class, this::fromBooleanValueTSNode);
        registerTSNodeHandler("field_access", MemberAccess.class, this::fromFieldAccessTSNode);
        registerTSNodeHandler("array_creation_expression", ArrayNewExpression.class, this::fromArrayCreationExpressionTSNode);
        registerTSNodeHandler("array_initializer", ArrayInitializer.class, this::fromArrayInitializer);
        registerTSNodeHandler("return_statement", ReturnStatement.class, this::fromReturnStatementTSNode);
        registerTSNodeHandler(List.of("void_type", "type_identifier"), Type.class, this::fromTypeTSNode);
        registerTSNodeHandler(List.of("line_comment", "block_comment"), Comment.class, this::fromCommentTSNode);
        registerTSNodeHandler("cast_expression", Expression.class, this::fromCastExpressionTSNode);
        registerTSNodeHandler("array_access", IndexExpression.class, this::fromArrayAccessTSNode);
        registerTSNodeHandler("ternary_expression", TernaryOperator.class, this::fromTernaryExpressionTSNode);
        registerTSNodeHandler("constructor_declaration", ObjectConstructorDefinition.class, this::fromConstructorDeclarationTSNode);
        registerTSNodeHandler("constructor_body", CompoundStatement.class, this::fromBlockTSNode);
        registerTSNodeHandler("explicit_constructor_invocation", ExpressionStatement.class, this::fromExplicitConstructorInvocationTSNode);
        registerTSNodeHandler("this", SelfReference.class, this::fromThisTSNode);
        registerTSNodeHandler("character_literal", CharacterLiteral.class, this::fromCharacterLiteralTSNode);
        registerTSNodeHandler("do_statement", DoWhileLoop.class, this::fromDoStatementTSNode);
        registerTSNodeHandler("instanceof_expression", InstanceOfOp.class, this::fromInstanceOfTSNode);
        registerTSNodeHandler("labeled_statement", Node.class, this::fromLabeledStmtNode);
        registerTSNodeHandler("class_literal", MemberAccess.class, this::fromClassLiteralTSNode);
        registerTSNodeHandler("enhanced_for_statement", ForEachLoop.class, this::fromEnhancedForStatementTSNode);
        registerTSNodeHandler("try_statement", ExceptionCatchStatement.class, this::fromTryStatementTSNode);
        registerTSNodeHandler("try_with_resources_statement", Statement.class, this::fromTryStatementTSNode);
        registerTSNodeHandler("throw_statement", RaiseExceptionStatement.class, this::fromThrowStatementTSNode);
    }

    public synchronized MeaningTree getMeaningTree(String code) {
        setCode(code);
        TSNode rootNode = getRootNode();
        List<String> errors = lookupErrors(rootNode);
        if (!errors.isEmpty() && !getConfigParameter("skipErrors").asBoolean()) {
            throw new UnsupportedParsingException(String.format("Given code has syntax errors: %s", errors));
        }

        Node node = parseTSNode(rootNode);
        if (isExpressionMode() && !(node instanceof Expression)
                && !(node instanceof ExpressionStatement)
                && !(node instanceof AssignmentStatement)) {
            throw new UnsupportedParsingException("Cannot parse the code as expression in expression mode");
        }
        if (node instanceof AssignmentExpression expr) {
            node = expr.toStatement();
        }

        return new MeaningTree(node);
    }

    @Override
    public TSNode getRootNode() {
        TSNode result = super.getRootNode();

        if (isExpressionMode()) {
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
        setCode(code);
        return new MeaningTree(parseTSNode(node));
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

    private ExpressionStatement fromExplicitConstructorInvocationTSNode(TSNode node) {
        List<Expression> arguments = new ArrayList<>();
        TSNode argumentsNode = node.getChildByFieldName("arguments");
        for (int i = 0; i < argumentsNode.getNamedChildCount(); i++) {
            arguments.add((Expression) parseTSNode(argumentsNode.getNamedChild(i)));
        }
        boolean isBaseClassCall = getCodePiece(node.getChildByFieldName("constructor")).equals("super");
        return new ExpressionStatement(new ConstructorCall(new UnknownType(), isBaseClassCall, arguments));
    }

    private Node fromLabeledStmtNode(TSNode node) {
        Node inner = parseTSNode(node.getNamedChild(1));
        if (inner instanceof Statement stmt) {
            stmt.setJumpLabel(new JumpLabel(getCodePiece(node.getNamedChild(0))));
        }
        return inner;
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

    private Node fromTryStatementTSNode(TSNode node) {
        // Ресурсы разбираются до тела: объявленные в них переменные тело уже видит
        List<Node> resources = fromResourceSpecificationTSNode(node.getChildByFieldName("resources"));
        Statement body = (Statement) parseTSNode(node.getChildByFieldName("body"));

        List<CatchClause> catchClauses = new ArrayList<>();
        Statement finallyBranch = null;
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode child = node.getNamedChild(i);
            switch (child.getType()) {
                case "catch_clause" -> catchClauses.add(fromCatchClauseTSNode(child));
                // finally_clause не имеет именованного поля для блока, поэтому берём его как
                // единственного именованного ребёнка
                case "finally_clause" -> finallyBranch = (Statement) parseTSNode(child.getNamedChild(0));
                default -> { }
            }
        }

        // Без ветвей try-with-resources — это только владение ресурсами, и обработки
        // исключений в нём нет; с ветвями ресурсы остаются полем той же конструкции,
        // потому что ошибку захвата ресурса ловят те же ветви
        if (!resources.isEmpty() && catchClauses.isEmpty() && finallyBranch == null) {
            return new ResourceContextStatement(resources, body);
        }
        return new ExceptionCatchStatement(body, resources, catchClauses, null, finallyBranch);
    }

    /**
     * Список ресурсов try-with-resources. Ресурс с именем — это объявление переменной,
     * ресурс без имени (`try (existing)`) — уже существующая переменная, то есть выражение.
     */
    private List<Node> fromResourceSpecificationTSNode(TSNode specification) {
        if (specification.isNull()) {
            return List.of();
        }

        List<Node> resources = new ArrayList<>();
        for (int i = 0; i < specification.getNamedChildCount(); i++) {
            TSNode resource = specification.getNamedChild(i);
            if (!resource.getType().equals("resource")) {
                continue;
            }

            TSNode name = resource.getChildByFieldName("name");
            if (name.isNull()) {
                resources.add(parseTSNode(resource.getNamedChild(0)));
                continue;
            }

            VariableDeclaration declaration = new VariableDeclaration(
                    fromTypeTSNode(resource.getChildByFieldName("type")),
                    (SimpleIdentifier) parseTSNode(name),
                    (Expression) parseTSNode(resource.getChildByFieldName("value"))
            );
            // Обычный путь наполнения области (BodyConstructor) видит только узлы тела,
            // а ресурс лежит в заголовке конструкции — регистрируем его отдельно
            ctx.getScopeTable().registerVariable(declaration);
            resources.add(declaration);
        }
        return resources;
    }

    private CatchClause fromCatchClauseTSNode(TSNode node) {
        TSNode parameter = node.getNamedChild(0);
        // У catch_formal_parameter перед catch_type может стоять modifiers (например, final),
        // поэтому ищем по типу узла, а не по индексу
        TSNode catchType = findNamedChild(parameter, "catch_type");

        List<Type> exceptionTypes = new ArrayList<>();
        for (int i = 0; i < catchType.getNamedChildCount(); i++) {
            exceptionTypes.add(fromTypeTSNode(catchType.getNamedChild(i)));
        }
        SimpleIdentifier name = (SimpleIdentifier) parseTSNode(parameter.getChildByFieldName("name"));

        // Тип переменной исключения нужен ещё при разборе тела ветви, поэтому регистрируется
        // до него; узла-объявления у неё нет, и обычный путь через BodyConstructor не подходит
        ctx.getScopeTable().registerCatchVariable(name, exceptionTypes);

        Statement body = (Statement) parseTSNode(node.getChildByFieldName("body"));
        return new CatchClause(exceptionTypes, name, body);
    }

    private static TSNode findNamedChild(TSNode node, String type) {
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            if (node.getNamedChild(i).getType().equals(type)) {
                return node.getNamedChild(i);
            }
        }
        throw new UnsupportedParsingException("Node " + node.getType() + " has no child of type " + type);
    }

    private Node fromThrowStatementTSNode(TSNode node) {
        return new RaiseExceptionStatement((Expression) parseTSNode(node.getNamedChild(0)));
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
            return tagIfLibraryImport(new ImportAllFromModule(scope), scope);
        }
        else {
            Identifier scope = fromIdentifierTSNode(scopeNode.getChildByFieldName("scope"));
            Identifier member = fromIdentifierTSNode(scopeNode.getChildByFieldName("name"));
            return tagIfLibraryImport(new ImportMembersFromModule(scope, member), scope);
        }
    }

    /**
     * Отмечает библиотечный импорт сразу при разборе, а не только после
     * {@link ImportResolver#resolve}: тот требует контекст проекта
     * ({@code LanguageTranslator.withSourceContext}), которого при переводе одиночного файла
     * обычно нет, — а без него другой язык не отличит {@code java.util.ArrayList} от локального
     * файла проекта и печатает вместо заголовка/импорта мусорный путь. Принадлежность
     * стандартной библиотеке видна по одному имени пакета, без обращения к файловой системе,
     * поэтому её можно проставить сразу; полноценный резолвинг проекта, если он всё же
     * запустится, эту метку просто перезапишет.
     */
    private <T extends Import> T tagIfLibraryImport(T importNode, Identifier scope) {
        if (JavaLibraryImportRegistry.isLibraryModule(ImportPathConverter.dottedName(scope))) {
            importNode.setResolverMetadata(ImportResolverMetadata.library());
        }
        return importNode;
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

    private Node fromMethodDeclarationTSNode(TSNode node) {
        List<DeclarationModifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        if (node.getChild(0).getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, node.getChild(0)));
        }

        Type returnType = fromTypeTSNode(node.getChildByFieldName("type"));
        Identifier identifier = fromScopedIdentifierTSNode(node.getChildByFieldName("name"));
        List<DeclarationArgument> parameters = fromMethodParameters(node.getChildByFieldName("parameters"));

        if (!modifiers.contains(DeclarationModifier.STATIC)) {
            modifiers.add(DeclarationModifier.VIRTUAL);
        }

        var methodDeclaration = new MethodDeclaration(
                null,
                identifier,
                returnType,
                annotations,
                modifiers,
                parameters
        );

        TSNode bodyNode = node.getChildByFieldName("body");
        if (bodyNode.isNull()) {
            return methodDeclaration;
        }

        CompoundStatement body = fromBlockTSNode(bodyNode);
        if (modifiers.size() == 2
                && modifiers.contains(DeclarationModifier.STATIC)
                && modifiers.contains(DeclarationModifier.PUBLIC)) {
            return new FunctionDefinition(new FunctionDeclaration(
                    identifier,
                    returnType,
                    annotations,
                    parameters
            ), body);
        }
        return new MethodDefinition(methodDeclaration, body);
    }

    private List<DeclarationArgument> fromMethodParameters(TSNode node) {
        List<DeclarationArgument> parameters = new ArrayList<>();

        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode child = node.getNamedChild(i);
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
                        (SimpleIdentifier) parseTSNode(node.getChild(i).getChildByFieldName("name")))
                );
                continue;
            }
            if (node.getChild(i).getType().equals("default")) {
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

    /**
     * Разбирает {@code enum Name { A, B }}. Константы с аргументами конструктора или собственным
     * телом, а также методы и поля перечисления не поддерживаются: это требует конструкторов,
     * которых у {@link EnumDeclaration} нет.
     */
    private EnumDeclaration fromEnumDeclarationTSNode(TSNode node) {
        List<DeclarationModifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        if (node.getChild(0).getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, node.getChild(0)));
        }

        Identifier enumName = fromIdentifierTSNode(node.getChildByFieldName("name"));
        TSNode body = node.getChildByFieldName("body");

        LinkedHashMap<Identifier, Expression> constants = new LinkedHashMap<>();
        for (int i = 0; i < body.getNamedChildCount(); i++) {
            TSNode child = body.getNamedChild(i);
            if (!child.getType().equals("enum_constant")) {
                throw new UnsupportedParsingException(
                        "Java enum with members is not supported: " + child.getType());
            }
            if (!child.getChildByFieldName("arguments").isNull() || !child.getChildByFieldName("body").isNull()) {
                throw new UnsupportedParsingException("Java enum constant with arguments or body is not supported");
            }
            constants.put(fromIdentifierTSNode(child.getChildByFieldName("name")), null);
        }

        EnumDeclaration declaration = new EnumDeclaration(modifiers, enumName, constants, true);
        declaration.setAnnotations(annotations);
        return declaration;
    }

    private ClassDefinition fromClassDeclarationTSNode(TSNode node) {
        int currentChildIndex = 0;

        List<DeclarationModifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        if (node.getChild(currentChildIndex).getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, node.getChild(currentChildIndex)));
            currentChildIndex++;
        }

        Identifier className = fromIdentifierTSNode(node.getChildByFieldName("name"));
        List<Type> parents = new ArrayList<>();
        TSNode superclass = node.getChildByFieldName("superclass");
        if (!superclass.isNull()) {
            parents.add(fromTypeTSNode(superclass.getNamedChild(0)));
        }
        TSNode interfaces = node.getChildByFieldName("interfaces");
        if (!interfaces.isNull()) {
            parents.addAll(fromTypeList(interfaces));
        }

        ClassDeclaration decl = ClassDeclaration.withTypeNode(
                modifiers, className, List.of(), new Class(qualifiedClassName(node, className)), parents.toArray(Type[]::new)
        );
        _userTypes.put(className.toString(), decl.getTypeNode());
        CompoundStatement classBody = fromBlockTSNode(node.getChildByFieldName("body"));
        UserType owner = (UserType) decl.getTypeNode().freshClone();
        Node[] members = classBody.getNodes();
        for (int i = 0; i < classBody.getLength(); i++) {
            Node member = members[i];
            MethodDeclaration memberMethod = switch (member) {
                case MethodDeclaration methodDeclaration -> methodDeclaration;
                case MethodDefinition methodDefinition -> methodDefinition.getDeclaration();
                default -> null;
            };
            if (memberMethod != null) {
                memberMethod.setOwner((UserType) owner.freshClone());
            }
            if (member instanceof FunctionDefinition function && !(member instanceof MethodDefinition)) {
                FunctionDeclaration functionDeclaration = function.getDeclaration();
                member = new MethodDefinition(new MethodDeclaration(
                        owner,
                        functionDeclaration.getName(),
                        functionDeclaration.getReturnType(),
                        functionDeclaration.getAnnotations(),
                        List.of(DeclarationModifier.PUBLIC, DeclarationModifier.STATIC),
                        functionDeclaration.getArguments().toArray(new DeclarationArgument[0])
                ), function.getBody());
                ctx.substituteNode(classBody, i, member);
            }

            if (member instanceof ObjectConstructorDefinition constructor && !decl.getParents().isEmpty()) {
                Node[] constructorBody = constructor.getBody().getNodes();
                for (int j = 0; j < constructorBody.length; j++) {
                    if (constructorBody[j] instanceof ExpressionStatement statement
                            && statement.getExpression() instanceof ConstructorCall call
                            && call.isBaseClassCall()) {
                        constructor.getBody().substitute(j, new ExpressionStatement(new ConstructorCall(
                                (Type) decl.getParents().getFirst().freshClone(), true, call.getArguments()
                        )));
                    }
                }
            }

            if (member instanceof MethodDefinition method
                    && method.getName().equalsIdentifier("finalize")
                    && method.getDeclaration().getArguments().isEmpty()
                    && method.getDeclaration().getReturnType() instanceof NoReturn
                    && !method.getDeclaration().getModifiers().contains(DeclarationModifier.STATIC)) {
                MethodDeclaration methodDeclaration = method.getDeclaration();
                ctx.substituteNode(classBody, i, new ObjectDestructorDefinition(
                        owner,
                        methodDeclaration.getName(),
                        methodDeclaration.getAnnotations(),
                        methodDeclaration.getModifiers(),
                        method.getBody()
                ));
            }
        }
        // TODO: нужно поменять getNodes() у CompoundStatement, чтобы он не массив возвращал
        ClassDefinition def = new ClassDefinition(decl, classBody);
        def.getDeclaration().setAnnotations(annotations);
        return def;
    }

    private InterfaceDefinition fromInterfaceDeclarationTSNode(TSNode node) {
        List<DeclarationModifier> modifiers = new ArrayList<>();
        List<Annotation> annotations = new ArrayList<>();
        if (node.getChild(0).getType().equals("modifiers")) {
            modifiers.addAll(fromModifiers(annotations, node.getChild(0)));
        }

        Identifier interfaceName = fromIdentifierTSNode(node.getChildByFieldName("name"));
        List<Type> parents = new ArrayList<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode child = node.getNamedChild(i);
            if (child.getType().equals("extends_interfaces")) {
                parents.addAll(fromTypeList(child));
            }
        }

        InterfaceDeclaration declaration = InterfaceDeclaration.withTypeNode(
                modifiers, interfaceName, List.of(),
                new Interface(qualifiedClassName(node, interfaceName)), parents.toArray(Type[]::new)
        );
        declaration.setAnnotations(annotations);
        _userTypes.put(interfaceName.toString(), declaration.getTypeNode());

        CompoundStatement body = fromBlockTSNode(node.getChildByFieldName("body"));
        Node[] members = body.getNodes();
        for (int i = 0; i < members.length; i++) {
            Node member = members[i];
            if (member instanceof FunctionDefinition function && !(member instanceof MethodDefinition)) {
                FunctionDeclaration functionDeclaration = function.getDeclaration();
                member = new MethodDefinition(new MethodDeclaration(
                        (UserType) declaration.getTypeNode().freshClone(),
                        functionDeclaration.getName(),
                        functionDeclaration.getReturnType(),
                        functionDeclaration.getAnnotations(),
                        List.of(DeclarationModifier.PUBLIC, DeclarationModifier.STATIC),
                        functionDeclaration.getArguments().toArray(new DeclarationArgument[0])
                ), function.getBody());
                ctx.substituteNode(body, i, member);
            }
            MethodDeclaration method = switch (member) {
                case MethodDeclaration methodDeclaration -> methodDeclaration;
                case MethodDefinition methodDefinition -> methodDefinition.getDeclaration();
                default -> null;
            };
            if (method == null) {
                continue;
            }
            method.setOwner((UserType) declaration.getTypeNode().freshClone());
            if (!method.getModifiers().contains(DeclarationModifier.PUBLIC)
                    && !method.getModifiers().contains(DeclarationModifier.PROTECTED)
                    && !method.getModifiers().contains(DeclarationModifier.PRIVATE)) {
                method.addModifiers(DeclarationModifier.PUBLIC);
            }
            if (member instanceof MethodDeclaration
                    && !method.getModifiers().contains(DeclarationModifier.STATIC)
                    && !method.getModifiers().contains(DeclarationModifier.PRIVATE)) {
                method.addModifiers(DeclarationModifier.ABSTRACT);
            }
        }
        return new InterfaceDefinition(declaration, body);
    }

    private List<Type> fromTypeList(TSNode container) {
        TSNode typeList = container;
        if (!container.getType().equals("type_list") && container.getNamedChildCount() == 1) {
            typeList = container.getNamedChild(0);
        }
        List<Type> result = new ArrayList<>();
        for (int i = 0; i < typeList.getNamedChildCount(); i++) {
            result.add(fromTypeTSNode(typeList.getNamedChild(i)));
        }
        return result;
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
    private RangeForLoop tryMakeRangeForLoop(Node init,
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
            Range.Direction type = Range.Direction.UNKNOWN;
            if (step instanceof IntegerLiteral integer) {
                if (integer.getLongValue() > 0) {
                    type = Range.Direction.UP;
                } else {
                    type = Range.Direction.DOWN;
                }
            } else if (step instanceof UnaryPlusOp) {
                type = Range.Direction.UP;
            } else if (step instanceof UnaryMinusOp) {
                type = Range.Direction.DOWN;
            }
            Range range = new Range(start, stop, step, false, isExcludingEnd, type);
            return new RangeForLoop(range, loopVariable, body);
        }

        return null;
    }

    private Loop fromForStatementTSNode(TSNode node) {
        Node init = null;
        Expression condition = null;
        Expression update = null;

        if (!node.getChildByFieldName("init").isNull()) {
            List<TSNode> assignments = getChildrenByFieldName(node, "init");

            if (assignments.size() == 1) {
                init = parseTSNode(assignments.getFirst());
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
                col.setTypeHint((Type) type.freshClone());
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
                    case "char" -> new CharacterType(16);
                    case "byte" -> new IntType(8);
                    case "short" -> new IntType(16);
                    case "int" -> new IntType(32);
                    case "long" -> new IntType(64);
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
                    // java.lang.String неизменяем: значение переприсвоить нельзя, переменную — можно
                    case "String" -> parsedType = new StringType(16, false, true, null);
                    case "Object", "var" -> parsedType = new UnknownType();
                    case "Integer" -> parsedType = new IntType(32);
                    case "Byte" -> parsedType = new IntType(8);
                    case "Short" -> parsedType = new IntType(16);
                    case "Long" -> parsedType =  new IntType(64);
                    case "Float" -> parsedType = new FloatType(32);
                    case "Double" -> parsedType = new FloatType(64);
                    case "Boolean" -> parsedType = new BooleanType();
                    case "Character" -> parsedType = new CharacterType(16);
                    default -> {
                        UserType resolved = resolveDeclaredUserType(typeName);
                        if (resolved != null) {
                            parsedType = (Type) resolved.freshClone();
                        } else {
                            if (!_userTypes.containsKey(typeName)) {
                                _userTypes.put(typeName, new Class(new SimpleIdentifier(typeName)));
                            }
                            parsedType = (Type) _userTypes.get(typeName).freshClone();
                        }
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
                    // Вид словаря уже определён базовым типом (TreeMap упорядочен, HashMap —
                    // нет): раньше он здесь терялся, и любой параметризованный словарь
                    // становился неупорядоченным
                    Type keyType = !subTypes.isEmpty() ? subTypes.getFirst() : new UnknownType();
                    Type valueType = subTypes.size() > 1 ? subTypes.get(1) : new UnknownType();
                    parsedType = subType instanceof OrderedDictionaryType
                            ? new OrderedDictionaryType(keyType, valueType)
                            : new UnorderedDictionaryType(keyType, valueType);
                } else if (subType instanceof SetType) {
                    parsedType = new SetType(!subTypes.isEmpty() ? subTypes.getFirst() : new UnknownType());
                } else if (subType instanceof Class cls) {
                    parsedType = new GenericClass((Identifier) cls.getQualifiedName().freshClone(), subTypes.toArray(new Type[0]));
                }
                break;
            case "scoped_type_identifier":
                ScopedIdentifier idents = fromScopedTypeIdentifier(node);
                parsedType = switch (idents.getScopeResolution().getLast().toString()) {
                    case "ArrayList", "List" -> new ListType(new UnknownType());
                    case "HashMap", "Map" -> new UnorderedDictionaryType(new UnknownType(), new UnknownType());
                    case "TreeMap", "OrderedMap" -> new OrderedDictionaryType(new UnknownType(), new UnknownType());
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

    /**
     * Ищет класс/интерфейс с именем {@code typeName}, уже объявленный и видимый из текущей точки
     * разбора (в т.ч. в объемлющем классе), через {@link ScopeTable}. Возвращает его собственный,
     * возможно квалифицированный, {@link UserType}, чтобы ссылка (например, в {@code extends})
     * совпадала (по {@code equals()}) с типом самого объявления, а не с голым одноимённым, который
     * дал бы плоский {@code _userTypes}. {@code null}, если ничего подходящего не найдено — вызывающий
     * код в этом случае использует прежний фолбэк.
     */
    @Nullable
    private UserType resolveDeclaredUserType(String typeName) {
        return ctx.getScopeTable()
                .findDeclaration(new SimpleIdentifier(typeName), ClassDeclaration.class, ScopeLookupMode.VISIBLE)
                .map(decl -> ((ClassDeclaration) decl).getTypeNode())
                .orElse(null);
    }

    /**
     * Строит имя объявляемого класса/интерфейса с учётом вложенности: если {@code declNode} лежит
     * внутри тела другого {@code class_declaration}/{@code interface_declaration}, возвращает
     * {@link ScopedIdentifier} со всей цепочкой внешних имён (снаружи внутрь) плюс собственное имя;
     * иначе — {@code bareName} без изменений. Цепочка вычисляется на месте, по дереву tree-sitter
     * ({@code declNode.getParent()}), а не хранится в отдельном состоянии парсера.
     * <p>
     * Подъём останавливается на теле метода ({@code block}): класс, объявленный внутри метода, —
     * не член внешнего класса, и квалифицировать его именем этого класса нельзя.
     */
    private Identifier qualifiedClassName(TSNode declNode, Identifier bareName) {
        List<SimpleIdentifier> chain = new ArrayList<>();
        TSNode ancestor = declNode.getParent();
        while (!ancestor.isNull()) {
            String type = ancestor.getType();
            if (type.equals("block") || type.equals("constructor_body")) {
                break;
            }
            if (type.equals("class_declaration") || type.equals("interface_declaration")) {
                chain.addFirst((SimpleIdentifier) fromIdentifierTSNode(ancestor.getChildByFieldName("name")));
            }
            ancestor = ancestor.getParent();
        }
        if (chain.isEmpty()) {
            return (Identifier) bareName.freshClone();
        }
        chain.add((SimpleIdentifier) bareName.freshClone());
        return new ScopedIdentifier(chain);
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

        TSQuery all_declarators = new TSQuery(_tsLanguage, "(variable_declarator) @decls");
        TSQueryCursor cursor = new TSQueryCursor();
        cursor.exec(all_declarators, node);
        TSQueryMatch match = new TSQueryMatch();
        while (cursor.nextMatch(match)) {
            TSQueryCapture capture = match.getCaptures()[0];
            VariableDeclarator decl = fromVariableDeclarator(capture.getNode(), type);
            declarators.add(decl);
        }

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
        var statements = ctx.createNodeBody(false);
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            statements.add(parseTSNode(node.getNamedChild(i)));
        }

        ClassDefinition mainClass = null;
        FunctionDefinition mainMethod = null;
        for (Node n : statements) {
            if (!(n instanceof ClassDefinition classDefinition)) {
                continue;
            }

            FunctionDefinition candidateMainMethod = classDefinition.findFunction("main");
            if (candidateMainMethod == null) {
                continue;
            }

            boolean isPublicClass =
                    classDefinition.getModifiers().contains(DeclarationModifier.PUBLIC);
            if (mainMethod == null || isPublicClass) {
                mainClass = classDefinition;
                mainMethod = candidateMainMethod;
            }

            if (isPublicClass) {
                break;
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

        List<Node> body = statements.getNodes();

        if (mainMethod != null && getConfigParameter("translationUnitMode").equalsValue("simple")) {
            return new ProgramEntryPoint(Arrays.asList(mainMethod.getBody().getNodes()), mainClass, mainMethod);
        }

        if (mainMethod != null && getConfigParameter("translationUnitMode").equalsValue("procedural")) {
            return JavaProceduralProgramTransformer.transform(body, mainClass, mainMethod);
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
        var statements = ctx.createNodeBody(true);
        for (int i = 1; i < node.getChildCount() - 1; i++) {
            statements.add(parseTSNode(node.getChild(i)));
        }
        return statements.build();
    }

    private Node fromStatementTSNode(TSNode node) {
        return parseTSNode(node.getChild(0));
    }

    private IfStatement fromIfStatementTSNode(TSNode node) {
        // Берем ребенка под индексом 1, чтобы избежать захвата скобок, а значит
        // неправильного парсинга (получаем выражение в скобках в качестве условия, а не просто выражение)
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition").getChild(1));
        Node consequence = parseTSNode(node.getChildByFieldName("consequence"));
        Statement consequenceStmt;
        if (consequence instanceof HasVariableDeclaration) {
            consequenceStmt = ctx.createNodeBody().add(consequence).build();
        } else {
            consequenceStmt = (Statement) consequence;
        }

        TSNode alternativeNode = node.getChildByFieldName("alternative");
        if (alternativeNode.isNull()) {
            return new IfStatement(condition, consequenceStmt);
        }

        Statement alternative = (Statement) parseTSNode(alternativeNode);
        return new IfStatement(condition, consequenceStmt, alternative);
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

    private final JavaImportResolver importResolver = new JavaImportResolver();

    @Override
    protected ImportResolver getImportResolver() {
        return importResolver;
    }
}
