package org.vstu.meaningtree.utils.analysis.expressions;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.ExpressionValueEstimate;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryMinusOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryPlusOp;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.scopes.ScopeTable;
import org.vstu.meaningtree.utils.scopes.ScopeTableElement;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public class ExpressionValueEvaluator {
    private final MeaningTree tree;
    private final ScopeTable scopeTable;

    public ExpressionValueEvaluator(MeaningTree tree, ScopeTable scopeTable) {
        this.tree = tree;
        this.scopeTable = scopeTable;
    }

    public MeaningTree getTree() {
        return tree;
    }

    public ScopeTable getScopeTable() {
        return scopeTable;
    }

    public void analyze() {
        for (NodeInfo info : tree) {
            if (info.node() instanceof Expression expression) {
                estimate(expression, expression);
            }
        }
    }

    public Optional<Boolean> evaluateAsBoolean(@Nullable Expression expression,
                                               Map<String, Long> env,
                                               @Nullable Node contextNode) {
        ExpressionValueEstimate<Boolean> estimate = estimateBoolean(expression, env, contextNode);
        return estimate.exactValue();
    }

    public OptionalLong evaluateAsLong(@Nullable Expression expression,
                                       Map<String, Long> env,
                                       @Nullable Node contextNode) {
        ExpressionValueEstimate<Long> estimate = estimateLong(expression, env, contextNode);
        return estimate.exactValue().isPresent()
                ? OptionalLong.of(estimate.exactValue().get())
                : OptionalLong.empty();
    }

    public OptionalLong evaluateCollectionSize(@Nullable Expression expression,
                                               @Nullable Node contextNode) {
        ExpressionValueEstimate<Long> estimate = estimateCollectionSize(expression, contextNode);
        return estimate.exactValue().isPresent()
                ? OptionalLong.of(estimate.exactValue().get())
                : OptionalLong.empty();
    }

    public ExpressionValueEstimate<?> estimate(@Nullable Expression expression,
                                               @Nullable Node contextNode) {
        if (expression == null) {
            return ExpressionValueEstimate.unknown();
        }

        ExpressionValueEstimate<Boolean> booleanEstimate = estimateBoolean(expression, Map.of(), contextNode);
        if (hasInformation(booleanEstimate)) {
            return booleanEstimate;
        }

        ExpressionValueEstimate<Long> longEstimate = estimateLong(expression, Map.of(), contextNode);
        if (hasInformation(longEstimate)) {
            return longEstimate;
        }

        return ExpressionValueEstimate.unknown();
    }

    public @Nullable ComparisonModel extractComparison(@Nullable Expression expression,
                                                       Map<String, Long> env,
                                                       @Nullable Node contextNode) {
        if (!(unwrap(expression) instanceof BinaryComparison comparison)) {
            return null;
        }

        if (comparison.getLeft() instanceof SimpleIdentifier identifier) {
            OptionalLong bound = evaluateAsLong(comparison.getRight(), env, contextNode);
            if (bound.isPresent()) {
                return new ComparisonModel(identifier, bound.getAsLong(), comparison.getClass());
            }
        }

        if (comparison.getRight() instanceof SimpleIdentifier identifier) {
            OptionalLong bound = evaluateAsLong(comparison.getLeft(), env, contextNode);
            if (bound.isPresent()) {
                return new ComparisonModel(identifier, bound.getAsLong(), invertComparison(comparison.getClass()));
            }
        }
        return null;
    }

    public OptionalLong resolveVisibleConstant(SimpleIdentifier identifier, @Nullable Node contextNode) {
        ScopeTableElement scope = visibleScope(contextNode);
        if (scope == null) {
            return OptionalLong.empty();
        }

        Optional<VariableDeclaration> declaration = scope.getVariableDeclaration(identifier, null);
        if (declaration.isEmpty()) {
            return OptionalLong.empty();
        }
        for (VariableDeclarator declarator : declaration.get().getDeclarators()) {
            if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                return evaluateAsLong(declarator.getRValue(), Map.of(), contextNode);
            }
        }
        return OptionalLong.empty();
    }

    public @Nullable Node visibleType(SimpleIdentifier identifier, @Nullable Node contextNode) {
        ScopeTableElement scope = visibleScope(contextNode);
        return scope == null ? null : scope.getVariableType(identifier);
    }

    public @Nullable ScopeTableElement visibleScope(@Nullable Node contextNode) {
        if (contextNode instanceof org.vstu.meaningtree.nodes.statements.Loop loop
                && loop.getBody() instanceof CompoundStatement compound
                && compound.getScopeId().isPresent()) {
            return scopeTable.findScope(compound.getScopeId().getAsLong()).orElse(null);
        }

        if (contextNode == null) {
            return null;
        }

        NodeInfo nodeInfo = tree.getNodeById(contextNode.getId());
        if (nodeInfo != null && nodeInfo.parentNode() instanceof CompoundStatement parentCompound && parentCompound.getScopeId().isPresent()) {
            return scopeTable.findScope(parentCompound.getScopeId().getAsLong()).orElse(null);
        }

        if (contextNode instanceof CompoundStatement compound && compound.getScopeId().isPresent()) {
            return scopeTable.findScope(compound.getScopeId().getAsLong()).orElse(null);
        }
        return null;
    }

    public @Nullable Expression unwrap(@Nullable Expression expression) {
        if (expression instanceof ParenthesizedExpression parenthesizedExpression) {
            return unwrap(parenthesizedExpression.getExpression());
        }
        return expression;
    }

    public @Nullable Node unwrap(@Nullable Node node) {
        if (node instanceof ParenthesizedExpression parenthesizedExpression) {
            return unwrap(parenthesizedExpression.getExpression());
        }
        return node;
    }

    private ExpressionValueEstimate<Boolean> estimateBoolean(@Nullable Expression expression,
                                                             Map<String, Long> env,
                                                             @Nullable Node contextNode) {
        if (expression == null) {
            return ExpressionValueEstimate.unknown();
        }
        Expression unwrapped = unwrap(expression);
        if (unwrapped instanceof BoolLiteral boolLiteral) {
            return remember(expression, ExpressionValueEstimate.exact(boolLiteral.getValue()), env);
        }
        if (unwrapped instanceof NotOp notOp) {
            Optional<Boolean> nested = evaluateAsBoolean((Expression) notOp.getArgument(), env, contextNode);
            if (nested.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(!nested.get()), env);
            }
            return remember(expression, ExpressionValueEstimate.possible(Set.of(Boolean.TRUE, Boolean.FALSE), false), env);
        }
        if (unwrapped instanceof ShortCircuitAndOp andOp) {
            Optional<Boolean> left = evaluateAsBoolean(andOp.getLeft(), env, contextNode);
            Optional<Boolean> right = evaluateAsBoolean(andOp.getRight(), env, contextNode);
            if (left.isPresent() && right.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(left.get() && right.get()), env);
            }
            return remember(expression, ExpressionValueEstimate.possible(Set.of(Boolean.TRUE, Boolean.FALSE), false), env);
        }
        if (unwrapped instanceof ShortCircuitOrOp orOp) {
            Optional<Boolean> left = evaluateAsBoolean(orOp.getLeft(), env, contextNode);
            Optional<Boolean> right = evaluateAsBoolean(orOp.getRight(), env, contextNode);
            if (left.isPresent() && right.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(left.get() || right.get()), env);
            }
            return remember(expression, ExpressionValueEstimate.possible(Set.of(Boolean.TRUE, Boolean.FALSE), false), env);
        }
        ComparisonModel comparison = extractComparison(unwrapped, env, contextNode);
        if (comparison != null && env.containsKey(comparison.identifier().getName())) {
            return remember(
                    expression,
                    ExpressionValueEstimate.exact(testCondition(
                            env.get(comparison.identifier().getName()),
                            comparison.bound(),
                            comparison.operator()
                    )),
                    env
            );
        }
        return ExpressionValueEstimate.unknown();
    }

    private ExpressionValueEstimate<Long> estimateLong(@Nullable Expression expression,
                                                       Map<String, Long> env,
                                                       @Nullable Node contextNode) {
        if (expression == null) {
            return ExpressionValueEstimate.unknown();
        }
        Expression unwrapped = unwrap(expression);
        if (unwrapped instanceof IntegerLiteral integerLiteral) {
            return remember(expression, ExpressionValueEstimate.exact(integerLiteral.getLongValue()), env);
        }
        if (unwrapped instanceof SimpleIdentifier identifier) {
            Long envValue = env.get(identifier.getName());
            if (envValue != null) {
                return remember(expression, ExpressionValueEstimate.exact(envValue), env);
            }
            OptionalLong constantValue = resolveVisibleConstant(identifier, contextNode);
            if (constantValue.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(constantValue.getAsLong()), env);
            }
            return ExpressionValueEstimate.unknown();
        }
        if (unwrapped instanceof UnaryPlusOp unaryPlusOp) {
            return estimateLong((Expression) unaryPlusOp.getArgument(), env, contextNode);
        }
        if (unwrapped instanceof UnaryMinusOp unaryMinusOp) {
            OptionalLong argumentValue = evaluateAsLong((Expression) unaryMinusOp.getArgument(), env, contextNode);
            if (argumentValue.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(-argumentValue.getAsLong()), env);
            }
            return ExpressionValueEstimate.unknown();
        }
        if (unwrapped instanceof AddOp addOp) {
            OptionalLong left = evaluateAsLong(addOp.getLeft(), env, contextNode);
            OptionalLong right = evaluateAsLong(addOp.getRight(), env, contextNode);
            if (left.isPresent() && right.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(left.getAsLong() + right.getAsLong()), env);
            }
            return ExpressionValueEstimate.unknown();
        }
        if (unwrapped instanceof SubOp subOp) {
            OptionalLong left = evaluateAsLong(subOp.getLeft(), env, contextNode);
            OptionalLong right = evaluateAsLong(subOp.getRight(), env, contextNode);
            if (left.isPresent() && right.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(left.getAsLong() - right.getAsLong()), env);
            }
            return ExpressionValueEstimate.unknown();
        }
        return ExpressionValueEstimate.unknown();
    }

    private ExpressionValueEstimate<Long> estimateCollectionSize(@Nullable Expression expression,
                                                                 @Nullable Node contextNode) {
        if (expression == null) {
            return ExpressionValueEstimate.unknown();
        }
        Expression unwrapped = unwrap(expression);
        if (unwrapped instanceof PlainCollectionLiteral plainCollectionLiteral) {
            return remember(expression, ExpressionValueEstimate.exact((long) plainCollectionLiteral.getList().size()), Map.of());
        }
        if (unwrapped instanceof DictionaryLiteral dictionaryLiteral) {
            return remember(expression, ExpressionValueEstimate.exact((long) dictionaryLiteral.getContent().size()), Map.of());
        }
        if (unwrapped instanceof StringLiteral stringLiteral) {
            return remember(expression, ExpressionValueEstimate.exact((long) stringLiteral.getUnescapedValue().length()), Map.of());
        }
        if (unwrapped instanceof SimpleIdentifier identifier) {
            ScopeTableElement scope = visibleScope(contextNode);
            if (scope == null) {
                return ExpressionValueEstimate.unknown();
            }
            Optional<VariableDeclaration> declaration = scope.getVariableDeclaration(identifier, null);
            if (declaration.isEmpty()) {
                return ExpressionValueEstimate.unknown();
            }
            for (VariableDeclarator declarator : declaration.get().getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                    return estimateCollectionSize(declarator.getRValue(), contextNode);
                }
            }
        }
        return ExpressionValueEstimate.unknown();
    }

    private <T> ExpressionValueEstimate<T> remember(Expression expression,
                                                    ExpressionValueEstimate<T> estimate,
                                                    Map<String, Long> env) {
        if (env.isEmpty()) {
            expression.setValueEstimate(estimate);
        }
        return estimate;
    }

    private boolean hasInformation(ExpressionValueEstimate<?> estimate) {
        return estimate.exactValue().isPresent() || !estimate.possibleValues().isEmpty();
    }

    private Class<? extends BinaryComparison> invertComparison(Class<? extends BinaryComparison> comparisonClass) {
        if (comparisonClass == LtOp.class) {
            return GtOp.class;
        }
        if (comparisonClass == LeOp.class) {
            return GeOp.class;
        }
        if (comparisonClass == GtOp.class) {
            return LtOp.class;
        }
        if (comparisonClass == GeOp.class) {
            return LeOp.class;
        }
        return comparisonClass;
    }

    private boolean testCondition(long value, long bound, Class<? extends BinaryComparison> operator) {
        if (operator == LtOp.class) {
            return value < bound;
        }
        if (operator == LeOp.class) {
            return value <= bound;
        }
        if (operator == GtOp.class) {
            return value > bound;
        }
        if (operator == GeOp.class) {
            return value >= bound;
        }
        return false;
    }

    public record ComparisonModel(SimpleIdentifier identifier,
                                  long bound,
                                  Class<? extends BinaryComparison> operator) {
    }
}
