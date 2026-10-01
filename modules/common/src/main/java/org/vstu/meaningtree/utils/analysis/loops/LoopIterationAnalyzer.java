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
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression;
import org.vstu.meaningtree.nodes.expressions.other.Range;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.expressions.unary.PostfixDecrementOp;
import org.vstu.meaningtree.nodes.expressions.unary.PostfixIncrementOp;
import org.vstu.meaningtree.nodes.expressions.unary.PrefixDecrementOp;
import org.vstu.meaningtree.nodes.expressions.unary.PrefixIncrementOp;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.GotoStatement;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEvaluator;
import org.vstu.meaningtree.utils.analysis.expressions.MutationScanner;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.*;

public class LoopIterationAnalyzer {
    /** Запись в переменную счётного цикла в теле число итераций не меняет (Python). */
    private final boolean loopVariableRebound;

    /** Правила C-семейства: запись счётчика в теле сдвигает цикл, оценка тогда неизвестна. */
    public LoopIterationAnalyzer() {
        this(false);
    }

    /**
     * @param loopVariableRebound переменная счётного цикла привязывается заново на каждой
     *                            итерации; см. {@code LanguageBehavior#loopVariableRebound()}
     */
    public LoopIterationAnalyzer(boolean loopVariableRebound) {
        this.loopVariableRebound = loopVariableRebound;
    }

    /**
     * Создаёт собственный вычислитель выражений. Подходит для изолированного вызова
     * (например, из тестов); в конвейере анализа следует передавать уже отработавший
     * вычислитель через {@link #analyze(MeaningTree, ExpressionValueEvaluator)}, иначе на
     * одно дерево создаются два независимых экземпляра.
     */
    public void analyze(MeaningTree tree, ScopeTable scopeTable) {
        analyze(tree, new ExpressionValueEvaluator(tree, scopeTable));
    }

    public void analyze(MeaningTree tree, ExpressionValueEvaluator evaluator) {
        for (NodeInfo info : tree) {
            if (info.node() instanceof Loop loop) {
                loop.setIterationEstimate(analyzeLoop(loop, evaluator));
            }
        }
    }

    private LoopIterationEstimate analyzeLoop(Loop loop, ExpressionValueEvaluator evaluator) {
        if (loop instanceof InfiniteLoop infiniteLoop) {
            return analyzeInfiniteLoop(infiniteLoop);
        }
        if (loop instanceof RangeForLoop rangeForLoop) {
            return analyzeRangeForLoop(rangeForLoop, evaluator);
        }
        if (loop instanceof ForEachLoop forEachLoop) {
            return analyzeForEachLoop(forEachLoop, evaluator);
        }
        if (loop instanceof GeneralForLoop generalForLoop) {
            return analyzeGeneralForLoop(generalForLoop, evaluator);
        }
        if (loop instanceof WhileLoop whileLoop) {
            return analyzeWhileLoop(whileLoop, evaluator);
        }
        if (loop instanceof DoWhileLoop doWhileLoop) {
            return analyzeDoWhileLoop(doWhileLoop, evaluator);
        }
        return LoopIterationEstimate.unknown();
    }

    private LoopIterationEstimate analyzeInfiniteLoop(InfiniteLoop loop) {
        if (hasTopLevelEarlyExit(loop.getBody(), false)) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }
        return LoopIterationEstimate.ofKind(LoopIterationCount.INFINITE, true, Range.Direction.UNKNOWN);
    }

    private LoopIterationEstimate analyzeRangeForLoop(RangeForLoop loop, ExpressionValueEvaluator evaluator) {
        Range.Direction direction = inferRangeDirection(loop.getRange(), evaluator, loop);
        OptionalLong startOpt = evaluator.evaluateAsLong(loop.getStart(), Map.of(), loop);
        OptionalLong stopOpt = evaluator.evaluateAsLong(loop.getStop(), Map.of(), loop);
        LoopIterationEstimate estimate;
        if (startOpt.isEmpty() || stopOpt.isEmpty()) {
            estimate = LoopIterationEstimate.ofKind(
                    LoopIterationCount.MANY,
                    false,
                    direction
            );
            return syncRangeMetadata(loop.getRange(), estimate);
        }

        long step = evaluateRangeStep(loop.getRange(), evaluator, loop);
        estimate = estimateMonotonicLoop(
                startOpt.getAsLong(),
                stopOpt.getAsLong(),
                step,
                detectRangeOperator(loop.getRange()),
                direction != Range.Direction.UNKNOWN ? direction : directionFromStep(step)
        );
        // Диапазон задаёт число итераций, только если тело не обрывает цикл и не трогает счётчик
        return syncRangeMetadata(loop.getRange(), guardBody(loop.getBody(), loop.getIdentifier(), estimate));
    }

    private LoopIterationEstimate analyzeForEachLoop(ForEachLoop loop, ExpressionValueEvaluator evaluator) {
        OptionalLong size = evaluator.evaluateCollectionSize(loop.getExpression(), loop);
        return size.isPresent()
                ? guardBody(loop.getBody(), null, LoopIterationEstimate.exact(size.getAsLong()))
                : LoopIterationEstimate.ofKind(LoopIterationCount.MANY, false);
    }

    private LoopIterationEstimate analyzeGeneralForLoop(GeneralForLoop loop, ExpressionValueEvaluator evaluator) {
        if (!loop.hasCondition()) {
            return hasTopLevelEarlyExit(loop.getBody(), false)
                    ? LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false)
                    : LoopIterationEstimate.ofKind(LoopIterationCount.INFINITE, true, Range.Direction.UNKNOWN);
        }

        ExpressionValueEvaluator.ComparisonModel comparison = evaluator.extractComparison(loop.getCondition(), Map.of(), loop);
        if (comparison == null) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        Optional<VariableState> variableState = extractInitializer(loop.getInitializer(), comparison.identifier(), evaluator, loop);
        if (variableState.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        OptionalLong stepOpt = extractStepFromNode(loop.getUpdate(), comparison.identifier(), evaluator, loop);
        if (stepOpt.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        // Шаг уже в заголовке, поэтому тело не должно трогать счётчик вовсе. Счётчик, объявленный
        // не в заголовке, дополнительно не должен иметь псевдонима
        if (!isBodyStable(loop.getBody(), comparison.identifier(), variableState.get().declarationType())
                || (loop.getInitializer() instanceof AssignmentStatement
                && isAliased(comparison.identifier(), evaluator, loop))) {
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

    private LoopIterationEstimate analyzeWhileLoop(WhileLoop loop, ExpressionValueEvaluator evaluator) {
        Optional<Boolean> constantCondition = evaluator.evaluateAsBoolean(loop.getCondition(), Map.of(), loop);
        if (constantCondition.isPresent()) {
            return constantCondition.get()
                    ? LoopIterationEstimate.ofKind(
                            hasTopLevelEarlyExit(loop.getBody(), true) ? LoopIterationCount.UNDEFINED : LoopIterationCount.INFINITE,
                            !hasTopLevelEarlyExit(loop.getBody(), true),
                            Range.Direction.UNKNOWN
                    )
                    : LoopIterationEstimate.exact(0);
        }

        ExpressionValueEvaluator.ComparisonModel comparison = evaluator.extractComparison(loop.getCondition(), Map.of(), loop);
        if (comparison == null) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        Optional<VariableState> initialState = findStateBeforeLoop(loop, comparison.identifier(), evaluator);
        if (initialState.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        OptionalLong stepOpt = extractSingleBodyStep(loop.getBody(), comparison.identifier(), initialState.get().declarationType(), evaluator, loop);
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

    private LoopIterationEstimate analyzeDoWhileLoop(DoWhileLoop loop, ExpressionValueEvaluator evaluator) {
        Optional<Boolean> constantCondition = evaluator.evaluateAsBoolean(loop.getCondition(), Map.of(), loop);
        if (constantCondition.isPresent() && !constantCondition.get()) {
            return LoopIterationEstimate.exact(1);
        }

        ExpressionValueEvaluator.ComparisonModel comparison = evaluator.extractComparison(loop.getCondition(), Map.of(), loop);
        if (comparison == null) {
            return constantCondition.orElse(false)
                    ? LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false)
                    : LoopIterationEstimate.ofKind(LoopIterationCount.MANY, false);
        }

        Optional<VariableState> initialState = findStateBeforeLoop(loop, comparison.identifier(), evaluator);
        if (initialState.isEmpty()) {
            return LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false);
        }

        OptionalLong stepOpt = extractSingleBodyStep(loop.getBody(), comparison.identifier(), initialState.get().declarationType(), evaluator, loop);
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
                                                       ExpressionValueEvaluator evaluator,
                                                       Loop contextLoop) {
        if (initializer instanceof VariableDeclaration declaration) {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                    OptionalLong value = evaluator.evaluateAsLong(declarator.getRValue(), Map.of(), contextLoop);
                    if (value.isPresent()) {
                        return Optional.of(new VariableState(value.getAsLong(), declaration.getType()));
                    }
                }
            }
        } else if (initializer instanceof AssignmentStatement assignment) {
            if (isIdentifier(assignment.getLValue(), identifier)) {
                OptionalLong value = evaluateAssignedValue(assignment, Map.of(), evaluator, contextLoop);
                if (value.isPresent()) {
                    return Optional.of(new VariableState(value.getAsLong(), null));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<VariableState> findStateBeforeLoop(Loop loop,
                                                        SimpleIdentifier identifier,
                                                        ExpressionValueEvaluator evaluator) {
        MeaningTree tree = evaluator.getTree();
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
                    OptionalLong value = evaluator.evaluateAsLong(
                            declarator.getRValue(),
                            Map.of(),
                            loop
                    );
                    if (value.isPresent()) {
                        return Optional.of(new VariableState(value.getAsLong(), declaration.getType()));
                    }
                }
            }
        } else if (previous instanceof AssignmentStatement assignment && isIdentifier(assignment.getLValue(), identifier)) {
            OptionalLong value = evaluateAssignedValue(assignment, Map.of(), evaluator, loop);
            if (value.isPresent()) {
                return Optional.of(new VariableState(value.getAsLong(), evaluator.visibleType(identifier, loop)));
            }
        }

        return Optional.empty();
    }

    /**
     * Шаг счётчика {@code while}/{@code do-while}: ровно один оператор тела, который является
     * шагом ({@code x--;}, {@code x -= 2;}, {@code x = x + 1;}), стоит прямо в теле и выполняется
     * на каждой итерации. Любая другая запись счётчика (под {@code if}, во вложенном цикле, вторым
     * шагом) или псевдоним у него отменяют оценку: число итераций тогда не выводится из кода.
     */
    private OptionalLong extractSingleBodyStep(Statement body,
                                               SimpleIdentifier identifier,
                                               @Nullable Node declarationType,
                                               ExpressionValueEvaluator evaluator,
                                               Loop contextLoop) {
        if (hasTopLevelEarlyExit(body, true)
                || escapesThroughCall(body, identifier, declarationType)
                || isAliased(identifier, evaluator, contextLoop)) {
            return OptionalLong.empty();
        }

        List<Node> statements = body instanceof CompoundStatement compound ? compound.getNodeList() : List.of(body);
        Long foundStep = null;
        for (Node statement : statements) {
            OptionalLong step = extractStepFromNode(statement, identifier, evaluator, contextLoop);
            if (step.isPresent()) {
                if (foundStep != null) {
                    return OptionalLong.empty();
                }
                foundStep = step.getAsLong();
            } else if (MutationScanner.mayModify(statement, identifier, MutationScanner.Mode.SCALAR, null)) {
                return OptionalLong.empty();
            }
        }
        return foundStep == null ? OptionalLong.empty() : OptionalLong.of(foundStep);
    }

    /** Тело цикла с шагом в заголовке не должно ни обрывать цикл, ни трогать счётчик. */
    private boolean isBodyStable(Statement body, SimpleIdentifier identifier, @Nullable Node declarationType) {
        return !hasTopLevelEarlyExit(body, false)
                && !escapesThroughCall(body, identifier, declarationType)
                && !MutationScanner.mayModify(body, identifier, MutationScanner.Mode.SCALAR, null);
    }

    /**
     * Оценка цикла со счётным заголовком верна, только если тело не обрывает цикл и не меняет
     * счётчик. Иначе число итераций из заголовка недостижимо или неверно; цикл, в который тело
     * не заходит, остаётся нулевым при любом теле.
     */
    private LoopIterationEstimate guardBody(Statement body,
                                            @Nullable SimpleIdentifier counter,
                                            LoopIterationEstimate estimate) {
        if (estimate.kind() == LoopIterationCount.ZERO) {
            return estimate;
        }
        boolean bodyBreaksEstimate = hasTopLevelEarlyExit(body, false)
                || (counter != null && !loopVariableRebound
                && MutationScanner.mayModify(body, counter, MutationScanner.Mode.SCALAR, null));
        return bodyBreaksEstimate
                ? LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false, estimate.direction())
                : estimate;
    }

    /**
     * Есть ли у счётчика псевдоним (адрес, ссылка): запись через него в теле не видна по имени.
     * Без объявления в таблице (параметр функции) это неизвестно, а неизвестное считается
     * псевдонимом.
     */
    private boolean isAliased(SimpleIdentifier identifier, ExpressionValueEvaluator evaluator, Loop contextLoop) {
        return evaluator.declarationScopeRoot(identifier, contextLoop)
                .map(root -> MutationScanner.mayAlias(root, identifier))
                .orElse(true);
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

    /**
     * Есть ли в теле выход, меняющий число итераций. {@code break} и {@code continue} внутри
     * вложенного цикла относятся к нему, а не к этому. {@code continue} учитывается, только если
     * он пропускает шаг счётчика: у {@code while} шаг в теле, у {@code for} он в заголовке.
     */
    private boolean hasTopLevelEarlyExit(Statement body, boolean continueSkipsStep) {
        List<NodeInfo> nodes = body.iterate(true);
        // Обход отдаёт всё поддерево разом, а не детей по очереди, поэтому «внутри ли вложенного
        // цикла» выясняется подъёмом по родителям
        Map<Long, Node> parents = new HashMap<>();
        for (NodeInfo info : nodes) {
            parents.put(info.node().getId(), info.parentNode());
        }
        for (NodeInfo info : nodes) {
            Node node = info.node();
            if (node instanceof ReturnStatement || node instanceof GotoStatement || node instanceof RaiseExceptionStatement) {
                return true;
            }
            boolean jumpsOutOfThisLoop = node instanceof BreakStatement
                    || (continueSkipsStep && node instanceof ContinueStatement);
            if (jumpsOutOfThisLoop && !isInsideNestedLoop(node, body, parents)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInsideNestedLoop(Node node, Node root, Map<Long, Node> parents) {
        if (node.getId() == root.getId()) {
            return false;
        }
        for (Node parent = parents.get(node.getId()); parent != null && parent.getId() != root.getId();
             parent = parents.get(parent.getId())) {
            if (parent instanceof Loop) {
                return true;
            }
        }
        return false;
    }

    private OptionalLong extractStepFromNode(@Nullable Node node,
                                             SimpleIdentifier identifier,
                                             ExpressionValueEvaluator evaluator,
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
            return extractStepFromAssignment(assignment.getAugmentedOperator(), assignment.getRValue(), identifier, evaluator, contextLoop);
        }
        if (node instanceof ExpressionStatement statement) {
            return extractStepFromNode(statement.getExpression(), identifier, evaluator, contextLoop);
        }
        if (node instanceof AssignmentExpression assignment && isIdentifier(assignment.getLValue(), identifier)) {
            return extractStepFromAssignment(assignment.getAugmentedOperator(), assignment.getRValue(), identifier, evaluator, contextLoop);
        }
        return OptionalLong.empty();
    }

    /**
     * Шаг присваивания, одинаковый на каждой итерации: {@code x += c}, {@code x -= c},
     * {@code x = x + c}, {@code x = c + x}, {@code x = x - c}. Правая часть вычисляется без
     * значения счётчика: {@code x += x} или {@code x = 10 - x} шага не имеют, а {@code x = 5}
     * не шаг вовсе, а сброс.
     */
    private OptionalLong extractStepFromAssignment(AugmentedAssignmentOperator operator,
                                                   Expression rightValue,
                                                   SimpleIdentifier identifier,
                                                   ExpressionValueEvaluator evaluator,
                                                   Loop contextLoop) {
        return switch (operator) {
            case ADD -> evaluator.evaluateAsLong(rightValue, Map.of(), contextLoop);
            case SUB -> negate(evaluator.evaluateAsLong(rightValue, Map.of(), contextLoop));
            case NONE -> {
                Expression value = unwrap(rightValue);
                if (value instanceof AddOp add && isIdentifier(add.getLeft(), identifier)) {
                    yield evaluator.evaluateAsLong(add.getRight(), Map.of(), contextLoop);
                }
                if (value instanceof AddOp add && isIdentifier(add.getRight(), identifier)) {
                    yield evaluator.evaluateAsLong(add.getLeft(), Map.of(), contextLoop);
                }
                if (value instanceof SubOp sub && isIdentifier(sub.getLeft(), identifier)) {
                    yield negate(evaluator.evaluateAsLong(sub.getRight(), Map.of(), contextLoop));
                }
                yield OptionalLong.empty();
            }
            default -> OptionalLong.empty();
        };
    }

    private static OptionalLong negate(OptionalLong value) {
        return value.isPresent() ? OptionalLong.of(-value.getAsLong()) : value;
    }

    private OptionalLong evaluateAssignedValue(AssignmentStatement assignment,
                                               Map<String, Long> env,
                                               ExpressionValueEvaluator evaluator,
                                               Loop contextLoop) {
        OptionalLong rightValue = evaluator.evaluateAsLong(assignment.getRValue(), env, contextLoop);
        if (rightValue.isEmpty()) {
            return OptionalLong.empty();
        }

        return switch (assignment.getAugmentedOperator()) {
            case NONE -> rightValue;
            case ADD -> evaluator.evaluateAsLong(assignment.getLValue(), env, contextLoop)
                    .isPresent() ? OptionalLong.of(evaluator.evaluateAsLong(assignment.getLValue(), env, contextLoop).getAsLong() + rightValue.getAsLong()) : OptionalLong.empty();
            case SUB -> evaluator.evaluateAsLong(assignment.getLValue(), env, contextLoop)
                    .isPresent() ? OptionalLong.of(evaluator.evaluateAsLong(assignment.getLValue(), env, contextLoop).getAsLong() - rightValue.getAsLong()) : OptionalLong.empty();
            default -> OptionalLong.empty();
        };
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

        if (operator == NotEqOp.class) {
            // Условие становится ложным, только когда счётчик попадёт ровно в границу; иначе
            // он её перешагнёт, и дальше всё решает переполнение, которого дерево не знает
            long distance = bound - start;
            return distance % step == 0 && distance / step > 0
                    ? LoopIterationEstimate.fixed(distance / step, true, direction)
                    : LoopIterationEstimate.ofKind(LoopIterationCount.UNDEFINED, false, direction);
        }
        if (operator == EqOp.class) {
            // Условие истинно только при start == bound, после первого шага оно ложно
            return LoopIterationEstimate.exact(1);
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
        if (operator == EqOp.class) {
            return value == bound;
        }
        if (operator == NotEqOp.class) {
            return value != bound;
        }
        return false;
    }

    private long evaluateRangeStep(Range range, ExpressionValueEvaluator evaluator, Loop loop) {
        OptionalLong explicitStep = evaluator.evaluateAsLong(range.getStep(), Map.of(), loop);
        if (explicitStep.isPresent()) {
            return explicitStep.getAsLong();
        }
        return inferRangeDirection(range, evaluator, loop) == Range.Direction.DOWN ? -1 : 1;
    }

    private Class<? extends BinaryComparison> detectRangeOperator(Range range) {
        return switch (range.getDirection()) {
            case DOWN -> range.isExcludingEnd() ? GtOp.class : GeOp.class;
            case UNKNOWN, UP -> range.isExcludingEnd() ? LtOp.class : LeOp.class;
        };
    }

    private Range.Direction inferRangeDirection(Range range,
                                                ExpressionValueEvaluator evaluator,
                                                Loop loop) {
        Range.Direction direction = range.getDirection();
        if (direction != Range.Direction.UNKNOWN) {
            return direction;
        }

        OptionalLong explicitStep = evaluator.evaluateAsLong(range.getStep(), Map.of(), loop);
        if (explicitStep.isPresent()) {
            direction = directionFromStep(explicitStep.getAsLong());
        }

        if (direction == Range.Direction.UNKNOWN) {
            OptionalLong start = evaluator.evaluateAsLong(range.getStart(), Map.of(), loop);
            OptionalLong stop = evaluator.evaluateAsLong(range.getStop(), Map.of(), loop);
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

    private record VariableState(long value, @Nullable Node declarationType) {
    }
}
