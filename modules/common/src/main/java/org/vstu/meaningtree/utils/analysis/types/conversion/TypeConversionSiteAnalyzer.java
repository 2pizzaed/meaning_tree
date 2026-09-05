package org.vstu.meaningtree.utils.analysis.types.conversion;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression;
import org.vstu.meaningtree.nodes.expressions.other.CastTypeExpression;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.ChainedAssignmentStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.utils.analysis.symbols.OverloadCallResolver;
import org.vstu.meaningtree.utils.analysis.types.SimpleTypeInferrer;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;

/** Tree traversal and best-effort callable matching used by {@link TypeConversionAnalyzer}. */
final class TypeConversionSiteAnalyzer {
    private final TypeConversionAnalyzer analyzer;
    private final MeaningTree tree;
    private final ScopeTable scope;
    private final List<TypeConversionCheck> checks = new ArrayList<>();

    /**
     * Выбор перегрузки для места вызова. Собственной реализации отбора кандидатов здесь нет:
     * разойдись она с той, что проставляет ссылки на дереве, анализ проверял бы преобразование
     * к параметру, который на самом деле не выбран.
     */
    private final OverloadCallResolver callResolver;

    TypeConversionSiteAnalyzer(TypeConversionAnalyzer analyzer, MeaningTree tree, ScopeTable scope) {
        this.analyzer = analyzer;
        this.tree = tree;
        this.scope = scope;
        this.callResolver = new OverloadCallResolver(tree, scope, analyzer, analyzer.overloadSemantics());
    }

    TypeConversionReport analyze() {
        for (NodeInfo info : tree) {
            withSiteScope(info, () -> {
                analyzeSite(info);
                return null;
            });
        }
        return new TypeConversionReport(checks);
    }

    private void analyzeSite(NodeInfo info) {
        switch (info.node()) {
            case CastTypeExpression cast -> addCheck(
                    cast,
                    ConversionSiteKind.CAST,
                    infer(cast.getValue()),
                    cast.getCastType(),
                    ConversionKind.EXPLICIT,
                    cast.getValue());
            case VariableDeclarator declarator -> analyzeVariableInitializer(info, declarator);
            case DeclarationArgument argument -> analyzeDefaultArgument(argument);
            case AssignmentStatement assignment -> addCheck(
                    assignment,
                    ConversionSiteKind.ASSIGNMENT,
                    knownOrInfer(assignment.getRealType(), assignment.getRValue()),
                    infer(assignment.getLValue()),
                    ConversionKind.IMPLICIT,
                    assignment.getRValue());
            case AssignmentExpression assignment -> addCheck(
                    assignment,
                    ConversionSiteKind.ASSIGNMENT,
                    knownOrInfer(assignment.getRealType(), assignment.getRValue()),
                    infer(assignment.getLValue()),
                    ConversionKind.IMPLICIT,
                    assignment.getRValue());
            case ChainedAssignmentStatement assignment -> analyzeChainedAssignment(assignment);
            // ObjectNewExpression тоже Callable и тоже резолвится: без него аргументы обычного
            // new Point(...) не попадали в отчёт вовсе.
            case ObjectNewExpression call -> analyzeCall(info, call, call.getArguments());
            case ConstructorCall call -> analyzeCall(info, call, call.getArguments());
            case MethodCall call -> analyzeCall(info, call, call.getArguments());
            case FunctionCall call -> analyzeCall(info, call, call.getArguments());
            case ReturnStatement returnStatement -> analyzeReturn(info, returnStatement);
            default -> {
                // This node is not a value-transfer boundary.
            }
        }
    }

    private void analyzeVariableInitializer(NodeInfo info, VariableDeclarator declarator) {
        if (!declarator.hasInitialization()
                || info.parentNode() == null
                || !(info.parentNode() instanceof VariableDeclaration declaration)) {
            return;
        }
        addCheck(
                declarator,
                ConversionSiteKind.INITIALIZER,
                knownOrInfer(declarator.getRealType(), declarator.getRValue()),
                declaration.getType(),
                ConversionKind.IMPLICIT,
                declarator.getRValue());
    }

    private void analyzeDefaultArgument(DeclarationArgument argument) {
        if (!argument.hasInitialExpression()) {
            return;
        }
        addCheck(
                argument,
                ConversionSiteKind.INITIALIZER,
                infer(argument.getInitialExpression()),
                argument.getType(),
                ConversionKind.IMPLICIT,
                argument.getInitialExpression());
    }

    private void analyzeChainedAssignment(ChainedAssignmentStatement assignment) {
        Type source = knownOrInfer(assignment.getRealType(), assignment.getValue());
        for (int index = 0; index < assignment.getTargets().size(); index++) {
            addCheck(
                    assignment,
                    ConversionSiteKind.ASSIGNMENT,
                    index,
                    source,
                    infer(assignment.getTargets().get(index)),
                    ConversionKind.IMPLICIT,
                    assignment.getValue());
        }
    }

    private void analyzeCall(NodeInfo info, Node call, List<Expression> arguments) {
        List<Type> targets = callResolver.resolveArgumentTargets(info);
        for (int index = 0; index < arguments.size(); index++) {
            Expression value = argumentValue(arguments.get(index));
            Type target = targets == null ? new UnknownType() : targets.get(index);
            addCheck(
                    call,
                    ConversionSiteKind.ARGUMENT,
                    index,
                    infer(value),
                    target,
                    ConversionKind.IMPLICIT,
                    value);
        }
    }

    private void analyzeReturn(NodeInfo info, ReturnStatement returnStatement) {
        if (returnStatement.getExpression() == null) {
            return;
        }
        FunctionDefinition function = nearestParent(info, FunctionDefinition.class);
        Type target = function == null
                ? new UnknownType()
                : function.getDeclaration().getReturnType();
        Type source = infer(returnStatement.getExpression());
        if (source instanceof UnknownType && function != null
                && returnStatement.getExpression() instanceof SimpleIdentifier identifier) {
            source = function.getDeclaration().getArguments().stream()
                    .filter(argument -> argument.getName().equals(identifier))
                    .map(DeclarationArgument::getType)
                    .findFirst()
                    .orElse(source);
        }
        addCheck(
                returnStatement,
                ConversionSiteKind.RETURN,
                source,
                target,
                ConversionKind.IMPLICIT,
                returnStatement.getExpression());
    }

    private Expression argumentValue(Expression argument) {
        return argument instanceof DefinitionArgument definitionArgument
                ? definitionArgument.getInitialExpression()
                : argument;
    }

    private Type knownOrInfer(Type computedType, Expression expression) {
        return computedType instanceof UnknownType ? infer(expression) : computedType;
    }

    private Type infer(Expression expression) {
        return expression == null ? new UnknownType() : SimpleTypeInferrer.inference(expression, scope);
    }

    private void addCheck(
            Node node,
            ConversionSiteKind siteKind,
            Type source,
            Type target,
            ConversionKind kind,
            Expression value) {
        addCheck(node, siteKind, OptionalInt.empty(), source, target, kind, value);
    }

    private void addCheck(
            Node node,
            ConversionSiteKind siteKind,
            int valueIndex,
            Type source,
            Type target,
            ConversionKind kind,
            Expression value) {
        addCheck(node, siteKind, OptionalInt.of(valueIndex), source, target, kind, value);
    }

    private void addCheck(
            Node node,
            ConversionSiteKind siteKind,
            OptionalInt valueIndex,
            Type source,
            Type target,
            ConversionKind kind,
            Expression value) {
        checks.add(new TypeConversionCheck(
                node,
                siteKind,
                valueIndex,
                source,
                target,
                kind,
                analyzer.compatibility(source, target, kind, siteKind, value, scope)));
    }

    /** Проверки выполняются в области самого места преобразования, а не там, где его нашёл обход. */
    private <T> T withSiteScope(NodeInfo info, Supplier<T> action) {
        return scope.inScopeOf(info, action);
    }

    private <T extends Node> T nearestParent(NodeInfo info, Class<T> type) {
        for (NodeInfo current = info.parent(); current != null; current = current.parent()) {
            if (type.isInstance(current.node())) {
                return type.cast(current.node());
            }
        }
        return null;
    }
}
