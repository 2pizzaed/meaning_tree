package org.vstu.meaningtree.utils.analysis.loops;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression;
import org.vstu.meaningtree.nodes.expressions.other.Range;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.CompoundAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.GotoStatement;
import org.vstu.meaningtree.utils.scopes.ScopeTable;
import org.vstu.meaningtree.utils.scopes.ScopeTableElement;

import java.util.*;

public class LoopIterationAnalyzer {
    public void analyze(MeaningTree tree, ScopeTable scopeTable) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof Loop loop) {
                loop.setIterationEstimate(analyzeLoop(loop, tree, scopeTable));
            }
        }
    }

    private LoopIterationEstimate analyzeLoop(Loop loop, MeaningTree tree, ScopeTable scopeTable) {
        if (loop instanceof InfiniteLoop infiniteLoop) {
            return analyzeInfiniteLoop(infiniteLoop);
        }
        if (loop instanceof RangeForLoop rangeForLoop) {
            return analyzeRangeForLoop(rangeForLoop, tree, scopeTable);
        }
        if (loop instanceof ForEachLoop forEachLoop) {
            return analyzeForEachLoop(forEachLoop, tree, scopeTable);
        }
        if (loop instanceof GeneralForLoop generalForLoop) {
            return analyzeGeneralForLoop(generalForLoop, tree, scopeTable);
        }
        if (loop instanceof WhileLoop whileLoop) {
            return analyzeWhileLoop(whileLoop, tree, scopeTable);
        }
        if (loop instanceof DoWhileLoop doWhileLoop) {
            return analyzeDoWhileLoop(doWhileLoop, tree, scopeTable);
        }
        return LoopIterationEstimate.unknown();
    }

    private LoopIterationEstimate analyzeInfiniteLoop(InfiniteLoop loop) {
        if (hasTopLevelEarlyExit(loop.getBody())) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }
        return LoopIterationEstimate.ofKind(LoopIterationCount.INFINITE, true, Range.Direction.UNKNOWN);
    }

    private LoopIterationEstimate analyzeRangeForLoop(RangeForLoop loop, MeaningTree tree, ScopeTable scopeTable) {
        Range.Direction direction = inferRangeDirection(loop.getRange(), tree, scopeTable, loop);
        OptionalLong startOpt = evaluateAsLong(loop.getStart(), Map.of(), tree, scopeTable, loop);
        OptionalLong stopOpt = evaluateAsLong(loop.getStop(), Map.of(), tree, scopeTable, loop);
        LoopIterationEstimate estimate;
        if (startOpt.isEmpty() || stopOpt.isEmpty()) {
            estimate = LoopIterationEstimate.ofKind(
                    LoopIterationCount.MANY,
                    false,
                    direction
            );
            return syncRangeMetadata(loop.getRange(), estimate);
        }

        long step = evaluateRangeStep(loop.getRange(), tree, scopeTable, loop);
        estimate = estimateMonotonicLoop(
                startOpt.getAsLong(),
                stopOpt.getAsLong(),
                step,
                detectRangeOperator(loop.getRange()),
                direction != Range.Direction.UNKNOWN ? direction : directionFromStep(step)
        );
        return syncRangeMetadata(loop.getRange(), estimate);
    }

    private LoopIterationEstimate analyzeForEachLoop(ForEachLoop loop, MeaningTree tree, ScopeTable scopeTable) {
        OptionalLong size = evaluateCollectionSize(loop.getExpression(), tree, scopeTable, loop);
        return size.isPresent()
                ? LoopIterationEstimate.exact(size.getAsLong())
                : LoopIterationEstimate.ofKind(LoopIterationCount.MANY, false);
    }

    private LoopIterationEstimate analyzeGeneralForLoop(GeneralForLoop loop, MeaningTree tree, ScopeTable scopeTable) {
        ComparisonModel comparison = extractComparison(loop.getCondition(), Map.of(), tree, scopeTable, loop);
        if (comparison == null) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        Optional<VariableState> variableState = extractInitializer(loop.getInitializer(), comparison.identifier(), tree, scopeTable, loop);
        if (variableState.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        OptionalLong stepOpt = extractStep(loop.getUpdate(), comparison.identifier(), Map.of(comparison.identifier().getName(), variableState.get().value()), tree, scopeTable, loop);
        if (stepOpt.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        if (!isBodyStable(loop.getBody(), comparison.identifier(), variableState.get().declarationType())) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        return estimateMonotonicLoop(
                variableState.get().value(),
                comparison.bound(),
                stepOpt.getAsLong(),
                comparison.operator(),
                directionFromStep(stepOpt.getAsLong())
        );
    }

    private LoopIterationEstimate analyzeWhileLoop(WhileLoop loop, MeaningTree tree, ScopeTable scopeTable) {
        Optional<Boolean> constantCondition = evaluateAsBoolean(loop.getCondition(), Map.of(), tree, scopeTable, loop);
        if (constantCondition.isPresent()) {
            return constantCondition.get()
                    ? LoopIterationEstimate.ofKind(
                            hasTopLevelEarlyExit(loop.getBody()) ? LoopIterationCount.UNDEFINED : LoopIterationCount.INFINITE,
                            !hasTopLevelEarlyExit(loop.getBody()),
                            Range.Direction.UNKNOWN
                    )
                    : LoopIterationEstimate.exact(0);
        }

        ComparisonModel comparison = extractComparison(loop.getCondition(), Map.of(), tree, scopeTable, loop);
        if (comparison == null) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        Optional<VariableState> initialState = findStateBeforeLoop(loop, comparison.identifier(), tree, scopeTable);
        if (initialState.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        OptionalLong stepOpt = extractSingleBodyStep(loop.getBody(), comparison.identifier(), initialState.get().declarationType(), Map.of(comparison.identifier().getName(), initialState.get().value()), tree, scopeTable, loop);
        if (stepOpt.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        return estimateMonotonicLoop(
                initialState.get().value(),
                comparison.bound(),
                stepOpt.getAsLong(),
                comparison.operator(),
                directionFromStep(stepOpt.getAsLong())
        );
    }

    private LoopIterationEstimate analyzeDoWhileLoop(DoWhileLoop loop, MeaningTree tree, ScopeTable scopeTable) {
        Optional<Boolean> constantCondition = evaluateAsBoolean(loop.getCondition(), Map.of(), tree, scopeTable, loop);
        if (constantCondition.isPresent() && !constantCondition.get()) {
            return LoopIterationEstimate.exact(1);
        }

        ComparisonModel comparison = extractComparison(loop.getCondition(), Map.of(), tree, scopeTable, loop);
        if (comparison == null) {
            return constantCondition.orElse(false)
                    ? LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false)
                    : LoopIterationEstimate.ofKind(LoopIterationCount.MANY, false);
        }

        Optional<VariableState> initialState = findStateBeforeLoop(loop, comparison.identifier(), tree, scopeTable);
        if (initialState.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        OptionalLong stepOpt = extractSingleBodyStep(loop.getBody(), comparison.identifier(), initialState.get().declarationType(), Map.of(comparison.identifier().getName(), initialState.get().value()), tree, scopeTable, loop);
        if (stepOpt.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        long firstValue = initialState.get().value() + stepOpt.getAsLong();
        LoopIterationEstimate tailEstimate = estimateMonotonicLoop(
                firstValue,
                comparison.bound(),
                stepOpt.getAsLong(),
                comparison.operator(),
                directionFromStep(stepOpt.getAsLong())
        );
        if (tailEstimate.exactIterations().isPresent()) {
            return LoopIterationEstimate.fixed(
                    tailEstimate.exactIterations().getAsLong() + 1,
                    tailEstimate.reliable(),
                    tailEstimate.direction()
            );
        }

        return switch (tailEstimate.kind()) {
            case ZERO -> LoopIterationEstimate.exact(1);
            case INFINITE -> LoopIterationEstimate.ofKind(
                    LoopIterationCount.INFINITE,
                    tailEstimate.reliable(),
                    tailEstimate.direction()
            );
            default -> LoopIterationEstimate.ofKind(LoopIterationCount.MANY, false, tailEstimate.direction());
        };
    }

    private Optional<VariableState> extractInitializer(@Nullable Node initializer,
                                                       SimpleIdentifier identifier,
                                                       MeaningTree tree,
                                                       ScopeTable scopeTable,
                                                       Loop contextLoop) {
        if (initializer instanceof VariableDeclaration declaration) {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                    OptionalLong value = evaluateAsLong(declarator.getRValue(), Map.of(), tree, scopeTable, contextLoop);
                    if (value.isPresent()) {
                        return Optional.of(new VariableState(value.getAsLong(), declaration.getType()));
                    }
                }
            }
        } else if (initializer instanceof AssignmentStatement assignment) {
            if (isIdentifier(assignment.getLValue(), identifier)) {
                OptionalLong value = evaluateAssignedValue(assignment, Map.of(), tree, scopeTable, contextLoop);
                if (value.isPresent()) {
                    return Optional.of(new VariableState(value.getAsLong(), null));
                }
            }
        } else if (initializer instanceof CompoundAssignmentStatement compound) {
            for (AssignmentStatement assignment : compound.getAssignments()) {
                if (isIdentifier(assignment.getLValue(), identifier)) {
                    OptionalLong value = evaluateAssignedValue(assignment, Map.of(), tree, scopeTable, contextLoop);
                    if (value.isPresent()) {
                        return Optional.of(new VariableState(value.getAsLong(), null));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private Optional<VariableState> findStateBeforeLoop(Loop loop,
                                                        SimpleIdentifier identifier,
                                                        MeaningTree tree,
                                                        ScopeTable scopeTable) {
        NodeInfo loopInfo = tree.getNodeById(loop.getId());
        if (loopInfo == null || !(loopInfo.parentNode() instanceof CompoundStatement parentBody) || loopInfo.field() == null || !loopInfo.field().isIndexed()) {
            return Optional.empty();
        }

        List<Node> nodes = parentBody.getNodeList();
        int loopIndex = loopInfo.field().getIndex();
        if (loopIndex <= 0 || loopIndex > nodes.size() - 1) {
            return Optional.empty();
        }

        Node previous = nodes.get(loopIndex - 1);
        if (previous instanceof VariableDeclaration declaration) {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                    OptionalLong value = evaluateAsLong(
                            declarator.getRValue(),
                            Map.of(),
                            tree,
                            scopeTable,
                            loop
                    );
                    if (value.isPresent()) {
                        return Optional.of(new VariableState(value.getAsLong(), declaration.getType()));
                    }
                }
            }
        } else if (previous instanceof AssignmentStatement assignment && isIdentifier(assignment.getLValue(), identifier)) {
            OptionalLong value = evaluateAssignedValue(assignment, Map.of(), tree, scopeTable, loop);
            if (value.isPresent()) {
                return Optional.of(new VariableState(value.getAsLong(), visibleType(identifier, loop, tree, scopeTable)));
            }
        }

        return Optional.empty();
    }

    private OptionalLong extractSingleBodyStep(Statement body,
                                               SimpleIdentifier identifier,
                                               @Nullable Node declarationType,
                                               Map<String, Long> env,
                                               MeaningTree tree,
                                               ScopeTable scopeTable,
                                               Loop contextLoop) {
        if (!isBodyStable(body, identifier, declarationType)) {
            return OptionalLong.empty();
        }

        Long foundStep = null;
        for (NodeInfo info : body.iterate(true)) {
            Node node = info.node();
            OptionalLong step = extractStepFromNode(node, identifier, env, tree, scopeTable, contextLoop);
            if (step.isPresent()) {
                if (foundStep != null) {
                    return OptionalLong.empty();
                }
                foundStep = step.getAsLong();
            }
        }
        return foundStep == null ? OptionalLong.empty() : OptionalLong.of(foundStep);
    }

    private boolean isBodyStable(Statement body, SimpleIdentifier identifier, @Nullable Node declarationType) {
        if (hasTopLevelEarlyExit(body)) {
            return false;
        }
        if (escapesThroughCall(body, identifier, declarationType)) {
            return false;
        }

        int writeCount = 0;
        for (NodeInfo info : body.iterate(true)) {
            if (writesIdentifier(info.node(), identifier)) {
                writeCount++;
            }
        }
        return writeCount <= 1;
    }

    private boolean escapesThroughCall(Statement body, SimpleIdentifier identifier, @Nullable Node declarationType) {
        boolean referenceLike = declarationType instanceof org.vstu.meaningtree.nodes.types.builtin.PointerType
                || declarationType instanceof org.vstu.meaningtree.nodes.types.builtin.ReferenceType;
        for (NodeInfo info : body.iterate(true)) {
            Node node = info.node();
            if (node instanceof FunctionCall call) {
                for (Expression argument : call.getArguments()) {
                    if (!containsIdentifier(argument, identifier)) {
                        continue;
                    }
                    if (referenceLike || argument instanceof PointerPackOp || argument instanceof PointerUnpackOp) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasTopLevelEarlyExit(Statement body) {
        return hasTopLevelEarlyExit(body, 0);
    }

    private boolean hasTopLevelEarlyExit(Node node, int nestedLoopDepth) {
        if (node instanceof ReturnStatement || node instanceof GotoStatement) {
            return true;
        }
        if ((node instanceof BreakStatement || node instanceof ContinueStatement) && nestedLoopDepth == 0) {
            return true;
        }

        int nextDepth = nestedLoopDepth;
        if (node instanceof Loop) {
            nextDepth++;
        }

        for (NodeInfo info : node.iterate(false)) {
            if (hasTopLevelEarlyExit(info.node(), nextDepth)) {
                return true;
            }
        }
        return false;
    }

    private OptionalLong extractStep(@Nullable Expression expression,
                                     SimpleIdentifier identifier,
                                     Map<String, Long> env,
                                     MeaningTree tree,
                                     ScopeTable scopeTable,
                                     Loop contextLoop) {
        return extractStepFromNode(expression, identifier, env, tree, scopeTable, contextLoop);
    }

    private OptionalLong extractStepFromNode(@Nullable Node node,
                                             SimpleIdentifier identifier,
                                             Map<String, Long> env,
                                             MeaningTree tree,
                                             ScopeTable scopeTable,
                                             Loop contextLoop) {
        if (node == null) {
            return OptionalLong.empty();
        }
        if (node instanceof PrefixIncrementOp increment && isIdentifier(increment.getArgument(), identifier)) {
            return OptionalLong.of(1);
        }
        if (node instanceof PostfixIncrementOp increment && isIdentifier(increment.getArgument(), identifier)) {
            return OptionalLong.of(1);
        }
        if (node instanceof PrefixDecrementOp decrement && isIdentifier(decrement.getArgument(), identifier)) {
            return OptionalLong.of(-1);
        }
        if (node instanceof PostfixDecrementOp decrement && isIdentifier(decrement.getArgument(), identifier)) {
            return OptionalLong.of(-1);
        }
        if (node instanceof AssignmentStatement assignment && isIdentifier(assignment.getLValue(), identifier)) {
            return extractStepFromAssignment(assignment, identifier, env, tree, scopeTable, contextLoop);
        }
        if (node instanceof ExpressionStatement statement) {
            return extractStepFromNode(statement.getExpression(), identifier, env, tree, scopeTable, contextLoop);
        }
        if (node instanceof AssignmentExpression assignment && isIdentifier(assignment.getLValue(), identifier)) {
            return extractStepFromAssignment(
                    new AssignmentStatement(assignment.getLValue(), assignment.getRValue(), assignment.getAugmentedOperator()),
                    identifier,
                    env,
                    tree,
                    scopeTable,
                    contextLoop
            );
        }
        return OptionalLong.empty();
    }

    private OptionalLong extractStepFromAssignment(AssignmentStatement assignment,
                                                   SimpleIdentifier identifier,
                                                   Map<String, Long> env,
                                                   MeaningTree tree,
                                                   ScopeTable scopeTable,
                                                   Loop contextLoop) {
        return switch (assignment.getAugmentedOperator()) {
            case ADD -> evaluateAsLong(assignment.getRValue(), env, tree, scopeTable, contextLoop);
            case SUB -> evaluateAsLong(assignment.getRValue(), env, tree, scopeTable, contextLoop).stream().map(v -> -v).findFirst();
            case NONE -> {
                OptionalLong value = evaluateAsLong(assignment.getRValue(), env, tree, scopeTable, contextLoop);
                if (value.isPresent() && env.containsKey(identifier.getName())) {
                    yield OptionalLong.of(value.getAsLong() - env.get(identifier.getName()));
                }
                yield OptionalLong.empty();
            }
            default -> OptionalLong.empty();
        };
    }

    private OptionalLong evaluateAssignedValue(AssignmentStatement assignment,
                                               Map<String, Long> env,
                                               MeaningTree tree,
                                               ScopeTable scopeTable,
                                               Loop contextLoop) {
        OptionalLong rightValue = evaluateAsLong(assignment.getRValue(), env, tree, scopeTable, contextLoop);
        if (rightValue.isEmpty()) {
            return OptionalLong.empty();
        }

        return switch (assignment.getAugmentedOperator()) {
            case NONE -> rightValue;
            case ADD -> evaluateAsLong(assignment.getLValue(), env, tree, scopeTable, contextLoop)
                    .isPresent() ? OptionalLong.of(evaluateAsLong(assignment.getLValue(), env, tree, scopeTable, contextLoop).getAsLong() + rightValue.getAsLong()) : OptionalLong.empty();
            case SUB -> evaluateAsLong(assignment.getLValue(), env, tree, scopeTable, contextLoop)
                    .isPresent() ? OptionalLong.of(evaluateAsLong(assignment.getLValue(), env, tree, scopeTable, contextLoop).getAsLong() - rightValue.getAsLong()) : OptionalLong.empty();
            default -> OptionalLong.empty();
        };
    }

    private Optional<Boolean> evaluateAsBoolean(@Nullable Expression expression,
                                                Map<String, Long> env,
                                                MeaningTree tree,
                                                ScopeTable scopeTable,
                                                Loop contextLoop) {
        if (expression == null) {
            return Optional.empty();
        }
        expression = unwrap(expression);
        if (expression instanceof BoolLiteral boolLiteral) {
            return Optional.of(boolLiteral.getValue());
        }
        if (expression instanceof NotOp notOp) {
            return evaluateAsBoolean((Expression) notOp.getArgument(), env, tree, scopeTable, contextLoop).map(v -> !v);
        }
        if (expression instanceof ShortCircuitAndOp andOp) {
            Optional<Boolean> left = evaluateAsBoolean(andOp.getLeft(), env, tree, scopeTable, contextLoop);
            Optional<Boolean> right = evaluateAsBoolean(andOp.getRight(), env, tree, scopeTable, contextLoop);
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(left.get() && right.get());
            }
            return Optional.empty();
        }
        if (expression instanceof ShortCircuitOrOp orOp) {
            Optional<Boolean> left = evaluateAsBoolean(orOp.getLeft(), env, tree, scopeTable, contextLoop);
            Optional<Boolean> right = evaluateAsBoolean(orOp.getRight(), env, tree, scopeTable, contextLoop);
            if (left.isPresent() && right.isPresent()) {
                return Optional.of(left.get() || right.get());
            }
            return Optional.empty();
        }
        ComparisonModel comparison = extractComparison(expression, env, tree, scopeTable, contextLoop);
        if (comparison != null && env.containsKey(comparison.identifier().getName())) {
            return Optional.of(testCondition(env.get(comparison.identifier().getName()), comparison.bound(), comparison.operator()));
        }
        return Optional.empty();
    }

    private OptionalLong evaluateAsLong(@Nullable Expression expression,
                                        Map<String, Long> env,
                                        MeaningTree tree,
                                        ScopeTable scopeTable,
                                        Loop contextLoop) {
        if (expression == null) {
            return OptionalLong.empty();
        }
        expression = unwrap(expression);
        if (expression instanceof IntegerLiteral integerLiteral) {
            return OptionalLong.of(integerLiteral.getLongValue());
        }
        if (expression instanceof SimpleIdentifier identifier) {
            Long envValue = env.get(identifier.getName());
            if (envValue != null) {
                return OptionalLong.of(envValue);
            }
            return resolveVisibleConstant(identifier, tree, scopeTable, contextLoop);
        }
        if (expression instanceof UnaryPlusOp unaryPlusOp) {
            return evaluateAsLong((Expression) unaryPlusOp.getArgument(), env, tree, scopeTable, contextLoop);
        }
        if (expression instanceof UnaryMinusOp unaryMinusOp) {
            OptionalLong arg = evaluateAsLong((Expression) unaryMinusOp.getArgument(), env, tree, scopeTable, contextLoop);
            return arg.isPresent() ? OptionalLong.of(-arg.getAsLong()) : OptionalLong.empty();
        }
        if (expression instanceof AddOp addOp) {
            OptionalLong left = evaluateAsLong(addOp.getLeft(), env, tree, scopeTable, contextLoop);
            OptionalLong right = evaluateAsLong(addOp.getRight(), env, tree, scopeTable, contextLoop);
            return left.isPresent() && right.isPresent() ? OptionalLong.of(left.getAsLong() + right.getAsLong()) : OptionalLong.empty();
        }
        if (expression instanceof SubOp subOp) {
            OptionalLong left = evaluateAsLong(subOp.getLeft(), env, tree, scopeTable, contextLoop);
            OptionalLong right = evaluateAsLong(subOp.getRight(), env, tree, scopeTable, contextLoop);
            return left.isPresent() && right.isPresent() ? OptionalLong.of(left.getAsLong() - right.getAsLong()) : OptionalLong.empty();
        }
        return OptionalLong.empty();
    }

    private OptionalLong resolveVisibleConstant(SimpleIdentifier identifier,
                                               MeaningTree tree,
                                               ScopeTable scopeTable,
                                               Loop contextLoop) {
        ScopeTableElement scope = visibleScope(contextLoop, tree, scopeTable);
        if (scope == null) {
            return OptionalLong.empty();
        }

        Optional<VariableDeclaration> declaration = scope.getVariableDeclaration(identifier, null);
        if (declaration.isEmpty()) {
            return OptionalLong.empty();
        }
        for (VariableDeclarator declarator : declaration.get().getDeclarators()) {
            if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                return evaluateAsLong(declarator.getRValue(), Map.of(), tree, scopeTable, contextLoop);
            }
        }
        return OptionalLong.empty();
    }

    private ScopeTableElement visibleScope(Loop loop, MeaningTree tree, ScopeTable scopeTable) {
        if (loop.getBody() instanceof CompoundStatement compound && compound.getScopeId().isPresent()) {
            return scopeTable.findScope(compound.getScopeId().getAsLong()).orElse(null);
        }

        NodeInfo loopInfo = tree.getNodeById(loop.getId());
        if (loopInfo != null && loopInfo.parentNode() instanceof CompoundStatement parentCompound && parentCompound.getScopeId().isPresent()) {
            return scopeTable.findScope(parentCompound.getScopeId().getAsLong()).orElse(null);
        }
        return null;
    }

    private @Nullable org.vstu.meaningtree.nodes.Node visibleType(SimpleIdentifier identifier,
                                                                  Loop loop,
                                                                  MeaningTree tree,
                                                                  ScopeTable scopeTable) {
        ScopeTableElement scope = visibleScope(loop, tree, scopeTable);
        return scope == null ? null : scope.getVariableType(identifier);
    }

    private OptionalLong evaluateCollectionSize(Expression expression,
                                                MeaningTree tree,
                                                ScopeTable scopeTable,
                                                Loop contextLoop) {
        expression = unwrap(expression);
        if (expression instanceof PlainCollectionLiteral plainCollectionLiteral) {
            return OptionalLong.of(plainCollectionLiteral.getList().size());
        }
        if (expression instanceof DictionaryLiteral dictionaryLiteral) {
            return OptionalLong.of(dictionaryLiteral.getContent().size());
        }
        if (expression instanceof StringLiteral stringLiteral) {
            return OptionalLong.of(stringLiteral.getUnescapedValue().length());
        }
        if (expression instanceof SimpleIdentifier identifier) {
            ScopeTableElement scope = visibleScope(contextLoop, tree, scopeTable);
            if (scope == null) {
                return OptionalLong.empty();
            }
            Optional<VariableDeclaration> declaration = scope.getVariableDeclaration(identifier, null);
            if (declaration.isEmpty()) {
                return OptionalLong.empty();
            }
            for (VariableDeclarator declarator : declaration.get().getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                    return evaluateCollectionSize(declarator.getRValue(), tree, scopeTable, contextLoop);
                }
            }
        }
        return OptionalLong.empty();
    }

    private ComparisonModel extractComparison(@Nullable Expression expression,
                                              Map<String, Long> env,
                                              MeaningTree tree,
                                              ScopeTable scopeTable,
                                              Loop contextLoop) {
        if (!(unwrap(expression) instanceof BinaryComparison comparison)) {
            return null;
        }

        if (comparison.getLeft() instanceof SimpleIdentifier identifier) {
            OptionalLong bound = evaluateAsLong(comparison.getRight(), env, tree, scopeTable, contextLoop);
            if (bound.isPresent()) {
                return new ComparisonModel(identifier, bound.getAsLong(), comparison.getClass());
            }
        }

        if (comparison.getRight() instanceof SimpleIdentifier identifier) {
            OptionalLong bound = evaluateAsLong(comparison.getLeft(), env, tree, scopeTable, contextLoop);
            if (bound.isPresent()) {
                return new ComparisonModel(identifier, bound.getAsLong(), invertComparison(comparison.getClass()));
            }
        }
        return null;
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

    private LoopIterationEstimate estimateMonotonicLoop(long start,
                                                        long bound,
                                                        long step,
                                                        Class<? extends BinaryComparison> operator,
                                                        Range.Direction direction) {
        if (step == 0) {
            return testCondition(start, bound, operator)
                    ? LoopIterationEstimate.ofKind(LoopIterationCount.INFINITE, true, direction)
                    : LoopIterationEstimate.exact(0);
        }

        if (!testCondition(start, bound, operator)) {
            return LoopIterationEstimate.exact(0);
        }

        if ((step > 0 && (operator == GtOp.class || operator == GeOp.class))
                || (step < 0 && (operator == LtOp.class || operator == LeOp.class))) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.INFINITE, true, direction);
        }

        long iterations;
        if (operator == LtOp.class) {
            iterations = ceilDiv(bound - start, step);
        } else if (operator == LeOp.class) {
            iterations = floorDiv(bound - start, step) + 1;
        } else if (operator == GtOp.class) {
            iterations = ceilDiv(start - bound, -step);
        } else if (operator == GeOp.class) {
            iterations = floorDiv(start - bound, -step) + 1;
        } else {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false, direction);
        }

        return iterations < 0
                ? LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false, direction)
                : LoopIterationEstimate.fixed(iterations, true, direction);
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

    private long evaluateRangeStep(Range range, MeaningTree tree, ScopeTable scopeTable, Loop loop) {
        OptionalLong explicitStep = evaluateAsLong(range.getStep(), Map.of(), tree, scopeTable, loop);
        if (explicitStep.isPresent()) {
            return explicitStep.getAsLong();
        }
        return inferRangeDirection(range, tree, scopeTable, loop) == Range.Direction.DOWN ? -1 : 1;
    }

    private Class<? extends BinaryComparison> detectRangeOperator(Range range) {
        return switch (range.getDirection()) {
            case DOWN -> range.isExcludingEnd() ? GtOp.class : GeOp.class;
            case UNKNOWN, UP -> range.isExcludingEnd() ? LtOp.class : LeOp.class;
        };
    }

    private Range.Direction inferRangeDirection(Range range,
                                                MeaningTree tree,
                                                ScopeTable scopeTable,
                                                Loop loop) {
        Range.Direction direction = range.getDirection();
        if (direction != Range.Direction.UNKNOWN) {
            return direction;
        }

        OptionalLong explicitStep = evaluateAsLong(range.getStep(), Map.of(), tree, scopeTable, loop);
        if (explicitStep.isPresent()) {
            direction = directionFromStep(explicitStep.getAsLong());
        }

        if (direction == Range.Direction.UNKNOWN) {
            OptionalLong start = evaluateAsLong(range.getStart(), Map.of(), tree, scopeTable, loop);
            OptionalLong stop = evaluateAsLong(range.getStop(), Map.of(), tree, scopeTable, loop);
            if (start.isPresent() && stop.isPresent()) {
                direction = Long.compare(start.getAsLong(), stop.getAsLong()) < 0
                        ? Range.Direction.UP
                        : Long.compare(start.getAsLong(), stop.getAsLong()) > 0
                        ? Range.Direction.DOWN
                        : Range.Direction.UNKNOWN;
            }
        }

        range.setDirection(direction);
        return direction;
    }

    private Range.Direction directionFromStep(long step) {
        if (step > 0) {
            return Range.Direction.UP;
        }
        if (step < 0) {
            return Range.Direction.DOWN;
        }
        return Range.Direction.UNKNOWN;
    }

    private LoopIterationEstimate syncRangeMetadata(Range range, LoopIterationEstimate estimate) {
        range.setIterationEstimate(estimate);
        if (estimate.direction() != Range.Direction.UNKNOWN) {
            range.setDirection(estimate.direction());
        }
        return estimate;
    }

    private long ceilDiv(long dividend, long divisor) {
        return Math.floorDiv(dividend + divisor - 1, divisor);
    }

    private long floorDiv(long dividend, long divisor) {
        return Math.floorDiv(dividend, divisor);
    }

    private boolean writesIdentifier(Node node, SimpleIdentifier identifier) {
        if (node instanceof VariableDeclaration declaration) {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier())) {
                    return true;
                }
            }
        }
        if (node instanceof SeparatedVariableDeclaration separated) {
            for (VariableDeclaration declaration : separated.getDeclarations()) {
                if (writesIdentifier(declaration, identifier)) {
                    return true;
                }
            }
        }
        if (node instanceof AssignmentStatement assignment && isIdentifier(assignment.getLValue(), identifier)) {
            return true;
        }
        if (node instanceof AssignmentExpression assignment && isIdentifier(assignment.getLValue(), identifier)) {
            return true;
        }
        if (node instanceof UnaryExpression unaryExpression && (unaryExpression instanceof PrefixIncrementOp
                || unaryExpression instanceof PostfixIncrementOp
                || unaryExpression instanceof PrefixDecrementOp
                || unaryExpression instanceof PostfixDecrementOp)) {
            return isIdentifier(unaryExpression.getArgument(), identifier);
        }
        return false;
    }

    private boolean containsIdentifier(Node node, SimpleIdentifier identifier) {
        if (node instanceof SimpleIdentifier simpleIdentifier) {
            return Objects.equals(simpleIdentifier, identifier);
        }
        for (NodeInfo info : node.iterate(false)) {
            if (containsIdentifier(info.node(), identifier)) {
                return true;
            }
        }
        return false;
    }

    private boolean isIdentifier(Node node, SimpleIdentifier identifier) {
        return unwrap(node) instanceof SimpleIdentifier simpleIdentifier && simpleIdentifier.equals(identifier);
    }

    private @Nullable Expression unwrap(@Nullable Expression expression) {
        if (expression instanceof ParenthesizedExpression parenthesizedExpression) {
            return unwrap(parenthesizedExpression.getExpression());
        }
        return expression;
    }

    private @Nullable Node unwrap(@Nullable Node node) {
        if (node instanceof ParenthesizedExpression parenthesizedExpression) {
            return unwrap(parenthesizedExpression.getExpression());
        }
        return node;
    }

    private record ComparisonModel(SimpleIdentifier identifier,
                                   long bound,
                                   Class<? extends BinaryComparison> operator) {
    }

    private record VariableState(long value, @Nullable Node declarationType) {
    }
}
