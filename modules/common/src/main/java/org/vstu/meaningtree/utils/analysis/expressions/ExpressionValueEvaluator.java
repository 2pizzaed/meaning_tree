package org.vstu.meaningtree.utils.analysis.expressions;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
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
import org.vstu.meaningtree.nodes.expressions.math.MulOp;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryMinusOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryPlusOp;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.utils.scopes.ScopeTable;
import org.vstu.meaningtree.utils.scopes.ScopeTableElement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

public class ExpressionValueEvaluator {
    private final MeaningTree tree;
    private final ScopeTable scopeTable;
    /**
     * Сводки того, что способно изменить поддерево, по идентификатору узла. Область видимости или
     * оператор обходится один раз, а не заново на каждое имя: иначе длинная функция стоила бы
     * квадрата её размера.
     */
    private final Map<Long, MutationScanner.Summary> scalarSummaries = new HashMap<>();
    private final Map<Long, MutationScanner.Summary> collectionSummaries = new HashMap<>();
    /** Глубина вложенных поисков значения по присваиваниям: значение одной переменной ссылается на другую. */
    private int flowDepth;
    /**
     * Объявления, чьё значение вычисляется прямо сейчас. Инициализатор вправе ссылаться на само
     * объявление: {@code int x = x + 1;} в C++, {@code n = n + 1} в Python, где первое присваивание
     * параметру объявляет новую локальную переменную, а таблица областей связывает имя справа с ней
     * же. Без этого учёта такое значение вычисляло бы себя бесконечно.
     */
    private final java.util.Set<Long> resolving = new java.util.HashSet<>();
    private static final int MAX_FLOW_DEPTH = 8;
    /** Длина цепочки {@code int b = a + 1; int c = b + 1; ...}: дальше ответ «неизвестно», а не переполнение стека. */
    private static final int MAX_RESOLVE_CHAIN = 64;
    /** Сколько предыдущих операторов просматривается в поисках присваивания: дальше ответ «неизвестно». */
    private static final int MAX_SCANNED_STATEMENTS = 256;

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
                estimate(expression, pointOf(expression));
            }
        }
    }

    /**
     * Оператор в блоке, внутри которого вычисляется выражение: от него зависят область
     * видимости имён и «ближайшее присваивание». Сама по себе глубоко вложенное выражение ни
     * того ни другого не определяет: родитель условия {@code if} — не блок, и имена в нём не
     * нашлись бы вовсе. Если такого оператора нет, остаётся само выражение.
     */
    private Node pointOf(Expression expression) {
        NodeInfo info = tree.getNodeById(expression.getId());
        while (info != null && info.parentNode() != null) {
            Node parent = info.parentNode();
            if (isStatementInBlock(parent)) {
                return parent;
            }
            info = tree.getNodeById(parent.getId());
        }
        return expression;
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

    /**
     * Значение целочисленной переменной в точке {@code contextNode}, если оно известно.
     * <ol>
     *   <li>Переменная неизменна ({@link #isEffectivelyConstant}): значение её инициализатора.</li>
     *   <li>Иначе, если контекст — оператор в блоке (цикл, объявление, присваивание), значение
     *       ближайшего присваивания перед ним ({@link #valueBeforePoint}).</li>
     * </ol>
     * Для выражения-контекста действует только первый способ: у выражения нет места в потоке
     * исполнения, до которого можно искать присваивание.
     */
    public OptionalLong resolveVisibleConstant(SimpleIdentifier identifier, @Nullable Node contextNode) {
        ScopeTableElement scope = visibleScope(contextNode);
        if (scope == null) {
            return OptionalLong.empty();
        }

        Optional<VariableDeclaration> declaration = scope.getVariableDeclaration(identifier, null);
        if (declaration.isEmpty()) {
            return OptionalLong.empty();
        }
        if (!isEffectivelyConstant(declaration.get(), identifier, MutationScanner.Mode.SCALAR)) {
            return valueBeforePoint(identifier, declaration.get(), contextNode);
        }
        // Инициализатор вычисляется в месте объявления, а не там, где переменную прочитали:
        // int k = n; n = 2; for (..; i < k; ..) — k равно прежнему n, а не новому
        Node initializerPoint = isStatementInBlock(declaration.get()) ? declaration.get() : contextNode;
        return initialValue(declaration.get(), identifier, initializerPoint);
    }

    private OptionalLong initialValue(VariableDeclaration declaration, SimpleIdentifier identifier, @Nullable Node point) {
        if (resolving.size() >= MAX_RESOLVE_CHAIN || !resolving.add(declaration.getId())) {
            return OptionalLong.empty();
        }
        try {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier()) && declarator.hasInitialization()) {
                    return evaluateAsLong(declarator.getRValue(), Map.of(), point);
                }
            }
            return OptionalLong.empty();
        } finally {
            resolving.remove(declaration.getId());
        }
    }

    /**
     * Значение переменной перед оператором {@code point}: присваивание {@code x = <константа>}
     * или объявление с константой, ближайшее перед ним в потоке исполнения. Это значение верно
     * для всего оператора, пока он сам переменную не меняет.
     * <p>
     * Поиск идёт назад по предыдущим операторам блока, затем выше, через блоки и {@code if}.
     * Он безопасен, только пока выполнено всё из следующего, иначе ответ «неизвестно»:
     * <ul>
     *   <li>переменная локальная: объявлена в блоке, и в его области видимости у неё нет
     *       псевдонима ({@code &x}, ссылка) — иначе запись возможна, не называя имени;</li>
     *   <li>сам оператор {@code point} переменную не меняет, ни в теле цикла, ни в его заголовке;</li>
     *   <li>между присваиванием и оператором нет записи в переменную: любой оператор, который её
     *       меняет не как {@code x = <константа>}, обрывает поиск;</li>
     *   <li>вверх поиск идёт только через блоки, {@code if} с условиями, не меняющими переменную,
     *       и циклы, которые переменную не меняют нигде. {@code switch}, определение функции или
     *       {@code try} его обрывают: до оператора управление может дойти по другому пути.</li>
     * </ul>
     */
    private OptionalLong valueBeforePoint(SimpleIdentifier identifier, VariableDeclaration declaration, @Nullable Node point) {
        if (flowDepth >= MAX_FLOW_DEPTH || point == null || !isStatementInBlock(point)) {
            return OptionalLong.empty();
        }
        Optional<Node> root = scopeRootOf(declaration);
        if (root.isEmpty() || !(root.get() instanceof CompoundStatement)
                || summaryOf(root.get(), MutationScanner.Mode.SCALAR).mayAlias(identifier.getName())
                || mayModify(point, identifier)) {
            return OptionalLong.empty();
        }

        flowDepth++;
        try {
            return scanBackwards(identifier, declaration, point);
        } finally {
            flowDepth--;
        }
    }

    private OptionalLong scanBackwards(SimpleIdentifier identifier, VariableDeclaration declaration, Node point) {
        Node node = point;
        int scanned = 0;
        while (true) {
            NodeInfo info = tree.getNodeById(node.getId());
            if (info == null) {
                return OptionalLong.empty();
            }
            Node parent = info.parentNode();
            if (parent instanceof CompoundStatement compound) {
                List<Node> siblings = compound.getNodeList();
                int index = indexOf(siblings, node);
                if (index < 0) {
                    return OptionalLong.empty();
                }
                for (int i = index - 1; i >= 0; i--) {
                    if (++scanned > MAX_SCANNED_STATEMENTS) {
                        return OptionalLong.empty();
                    }
                    Optional<OptionalLong> outcome = valueFromStatement(siblings.get(i), identifier, declaration);
                    if (outcome.isPresent()) {
                        return outcome.get();
                    }
                }
                node = compound;
            } else if (parent instanceof ConditionBranch) {
                node = parent;
            } else if (parent instanceof IfStatement ifStatement) {
                // Условия всех ветвей вычисляются до того, как управление дойдёт до тела одной из них
                for (ConditionBranch branch : ifStatement.getBranches()) {
                    if (mayModify(branch.getCondition(), identifier)) {
                        return OptionalLong.empty();
                    }
                }
                node = parent;
            } else if (parent instanceof Loop loop) {
                // Если переменную не меняет ни тело внешнего цикла, ни его заголовок, она на каждом
                // заходе во внутренний оператор такая же, как при входе во внешний цикл
                if (mayModify(loop, identifier)) {
                    return OptionalLong.empty();
                }
                node = parent;
            } else {
                return OptionalLong.empty();
            }
        }
    }

    /**
     * Что даёт оператор для поиска значения: пусто — оператор переменную не трогает, искать
     * дальше; иначе окончательный ответ (возможно, «неизвестно»).
     */
    private Optional<OptionalLong> valueFromStatement(Node statement, SimpleIdentifier identifier, VariableDeclaration declaration) {
        if (statement instanceof SeparatedVariableDeclaration separated) {
            for (VariableDeclaration inner : separated.getDeclarations()) {
                Optional<OptionalLong> outcome = valueFromStatement(inner, identifier, declaration);
                if (outcome.isPresent()) {
                    return outcome;
                }
            }
            return Optional.empty();
        }
        if (statement instanceof VariableDeclaration candidate) {
            if (candidate.getId() == declaration.getId()) {
                return Optional.of(initialValue(candidate, identifier, candidate));
            }
        } else if (statement instanceof AssignmentStatement assignment
                && assignment.getAugmentedOperator() == AugmentedAssignmentOperator.NONE
                && identifier.equals(unwrapIdentifier(assignment.getLValue()))) {
            if (mayModify(assignment.getRValue(), identifier)) {
                return Optional.of(OptionalLong.empty());
            }
            return Optional.of(evaluateAsLong(assignment.getRValue(), Map.of(), assignment));
        }
        return mayModify(statement, identifier)
                ? Optional.of(OptionalLong.empty())
                : Optional.empty();
    }

    private @Nullable SimpleIdentifier unwrapIdentifier(@Nullable Node node) {
        Node unwrapped = unwrap(node);
        return unwrapped instanceof SimpleIdentifier identifier ? identifier : null;
    }

    /** Оператор, у которого есть место в потоке исполнения: он стоит в блоке или в теле ветви {@code if}. */
    private boolean isStatementInBlock(Node node) {
        if (!(node instanceof Statement) && !(node instanceof Declaration)) {
            return false;
        }
        NodeInfo info = tree.getNodeById(node.getId());
        return info != null
                && (info.parentNode() instanceof CompoundStatement || info.parentNode() instanceof ConditionBranch);
    }

    private static int indexOf(List<Node> nodes, Node node) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).getId() == node.getId()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Область видимости, в которой объявлена переменная, то есть всё место, где её значение
     * могло измениться. Пусто, если объявления нет в таблице или в дереве (параметр функции,
     * переменная из десериализованной таблицы без дерева).
     */
    public Optional<Node> declarationScopeRoot(SimpleIdentifier identifier, @Nullable Node contextNode) {
        ScopeTableElement scope = visibleScope(contextNode);
        if (scope == null) {
            return Optional.empty();
        }
        return scope.getVariableDeclaration(identifier, null).flatMap(this::scopeRootOf);
    }

    /**
     * Переменная не меняется после объявления: она объявлена константой либо во всей её области
     * видимости нет ни одной записи, псевдонима или передачи по ссылке (см. {@link MutationScanner}).
     * Только такое значение инициализатора можно подставлять вместо имени.
     */
    public boolean isEffectivelyConstant(VariableDeclaration declaration,
                                         SimpleIdentifier identifier,
                                         MutationScanner.Mode mode) {
        if (declaration.getType() != null && declaration.getType().isConst()) {
            return true;
        }
        return scopeRootOf(declaration)
                .map(root -> !summaryOf(root, mode).mayModifyBesidesItsDeclaration(identifier.getName()))
                .orElse(false);
    }

    private MutationScanner.Summary summaryOf(Node node, MutationScanner.Mode mode) {
        Map<Long, MutationScanner.Summary> cache =
                mode == MutationScanner.Mode.SCALAR ? scalarSummaries : collectionSummaries;
        MutationScanner.Summary summary = cache.get(node.getId());
        if (summary == null) {
            summary = MutationScanner.summarize(node, mode);
            cache.put(node.getId(), summary);
        }
        return summary;
    }

    private boolean mayModify(Node node, SimpleIdentifier identifier) {
        return summaryOf(node, MutationScanner.Mode.SCALAR).mayModify(identifier.getName());
    }

    private Optional<Node> scopeRootOf(VariableDeclaration declaration) {
        NodeInfo info = tree.getNodeById(declaration.getId());
        if (info == null) {
            return Optional.empty();
        }
        // Объявление нескольких переменных через запятую лежит в обёртке: область — её родитель
        Node parent = info.parentNode();
        if (parent instanceof SeparatedVariableDeclaration) {
            NodeInfo wrapper = tree.getNodeById(parent.getId());
            parent = wrapper == null ? null : wrapper.parentNode();
        }
        return Optional.ofNullable(parent);
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
            // Ложная сторона решает результат, что бы ни стояло с другой: слева она обрывает
            // вычисление, справа результат всё равно ложен
            if (left.equals(Optional.of(false)) || right.equals(Optional.of(false))) {
                return remember(expression, ExpressionValueEstimate.exact(false), env);
            }
            if (left.isPresent() && right.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(true), env);
            }
            return remember(expression, ExpressionValueEstimate.possible(Set.of(Boolean.TRUE, Boolean.FALSE), false), env);
        }
        if (unwrapped instanceof ShortCircuitOrOp orOp) {
            Optional<Boolean> left = evaluateAsBoolean(orOp.getLeft(), env, contextNode);
            Optional<Boolean> right = evaluateAsBoolean(orOp.getRight(), env, contextNode);
            if (left.equals(Optional.of(true)) || right.equals(Optional.of(true))) {
                return remember(expression, ExpressionValueEstimate.exact(true), env);
            }
            if (left.isPresent() && right.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(false), env);
            }
            return remember(expression, ExpressionValueEstimate.possible(Set.of(Boolean.TRUE, Boolean.FALSE), false), env);
        }
        if (unwrapped instanceof BinaryComparison binaryComparison) {
            Optional<Boolean> folded = compareEvaluable(binaryComparison, env, contextNode);
            if (folded.isPresent()) {
                return remember(expression, ExpressionValueEstimate.exact(folded.get()), env);
            }
        }
        ComparisonModel comparison = extractComparison(unwrapped, env, contextNode);
        if (comparison != null) {
            if (env.containsKey(comparison.identifier().getName()) && isEvaluable(comparison.operator())) {
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
            return remember(expression, ExpressionValueEstimate.possible(Set.of(Boolean.TRUE, Boolean.FALSE), false), env);
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
            if (argumentValue.isPresent() && argumentValue.getAsLong() != Long.MIN_VALUE) {
                return checkedArithmetic(expression, unwrapped, -argumentValue.getAsLong(), env, contextNode);
            }
            return ExpressionValueEstimate.unknown();
        }
        if (unwrapped instanceof AddOp addOp) {
            OptionalLong left = evaluateAsLong(addOp.getLeft(), env, contextNode);
            OptionalLong right = evaluateAsLong(addOp.getRight(), env, contextNode);
            if (left.isPresent() && right.isPresent()) {
                try {
                    return checkedArithmetic(expression, unwrapped,
                            Math.addExact(left.getAsLong(), right.getAsLong()), env, contextNode);
                } catch (ArithmeticException overflow) {
                    return ExpressionValueEstimate.unknown();
                }
            }
            return ExpressionValueEstimate.unknown();
        }
        if (unwrapped instanceof SubOp subOp) {
            OptionalLong left = evaluateAsLong(subOp.getLeft(), env, contextNode);
            OptionalLong right = evaluateAsLong(subOp.getRight(), env, contextNode);
            if (left.isPresent() && right.isPresent()) {
                try {
                    return checkedArithmetic(expression, unwrapped,
                            Math.subtractExact(left.getAsLong(), right.getAsLong()), env, contextNode);
                } catch (ArithmeticException overflow) {
                    return ExpressionValueEstimate.unknown();
                }
            }
            return ExpressionValueEstimate.unknown();
        }
        if (unwrapped instanceof MulOp mulOp) {
            OptionalLong left = evaluateAsLong(mulOp.getLeft(), env, contextNode);
            OptionalLong right = evaluateAsLong(mulOp.getRight(), env, contextNode);
            if (left.isPresent() && right.isPresent()) {
                try {
                    return checkedArithmetic(expression, unwrapped,
                            Math.multiplyExact(left.getAsLong(), right.getAsLong()), env, contextNode);
                } catch (ArithmeticException overflow) {
                    return ExpressionValueEstimate.unknown();
                }
            }
            return ExpressionValueEstimate.unknown();
        }
        return ExpressionValueEstimate.unknown();
    }

    /**
     * Сравнение двух значений, которые удалось вычислить: {@code 1 < 2}, {@code N > 3} при
     * постоянной {@code N}, {@code (1 + 2) * 3 == 9}. Для {@code ==} и {@code !=} сравниваются и
     * два вычислимых булевых значения.
     * <p>
     * Пусто, если хотя бы одну сторону вычислить нельзя либо если сравнение беззнакового со
     * знаковым отрицательным значением: в C-семействе отрицательное число там превращается в
     * большое положительное, и результат отличается от арифметического.
     */
    private Optional<Boolean> compareEvaluable(BinaryComparison comparison,
                                               Map<String, Long> env,
                                               @Nullable Node contextNode) {
        if (!isEvaluable(comparison.getClass())) {
            return Optional.empty();
        }
        OptionalLong left = evaluateAsLong(comparison.getLeft(), env, contextNode);
        OptionalLong right = evaluateAsLong(comparison.getRight(), env, contextNode);
        if (left.isPresent() && right.isPresent()) {
            boolean unsignedInvolved = involvesUnsigned(comparison.getLeft(), contextNode)
                    || involvesUnsigned(comparison.getRight(), contextNode);
            if (unsignedInvolved && (left.getAsLong() < 0 || right.getAsLong() < 0)) {
                return Optional.empty();
            }
            return Optional.of(testCondition(left.getAsLong(), right.getAsLong(), comparison.getClass()));
        }
        if (comparison.getClass() == EqOp.class || comparison.getClass() == NotEqOp.class) {
            Optional<Boolean> leftFlag = evaluateAsBoolean(comparison.getLeft(), env, contextNode);
            Optional<Boolean> rightFlag = evaluateAsBoolean(comparison.getRight(), env, contextNode);
            if (leftFlag.isPresent() && rightFlag.isPresent()) {
                boolean equal = leftFlag.get().equals(rightFlag.get());
                return Optional.of(comparison.getClass() == EqOp.class ? equal : !equal);
            }
        }
        return Optional.empty();
    }

    /**
     * Результат арифметики принимается, только если он гарантированно такой же, какой получит
     * программа. Вычисление идёт в {@code long}, а у выражения в C++ или Java тип уже: за
     * пределами {@code int} результат — переполнение, а не число. Если участвует беззнаковое, то
     * отрицательный результат — это заворот, а не отрицательное число. В обоих случаях ответ
     * «неизвестно», а не неверное число. Так же осторожен Python, у которого целые не ограничены:
     * теряется точность, но не правильность.
     */
    private ExpressionValueEstimate<Long> checkedArithmetic(Expression expression,
                                                            Expression operation,
                                                            long value,
                                                            Map<String, Long> env,
                                                            @Nullable Node contextNode) {
        boolean fits = involvesUnsigned(operation, contextNode)
                ? value >= 0 && value <= 0xFFFFFFFFL
                : value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
        return fits ? remember(expression, ExpressionValueEstimate.exact(value), env) : ExpressionValueEstimate.unknown();
    }

    /** Есть ли в выражении беззнаковый литерал или переменная беззнакового типа. */
    private boolean involvesUnsigned(Node expression, @Nullable Node contextNode) {
        for (NodeInfo info : expression.iterate(true)) {
            Node node = info.node();
            if (node instanceof IntegerLiteral literal && literal.isUnsigned()) {
                return true;
            }
            if (node instanceof SimpleIdentifier identifier
                    && visibleType(identifier, contextNode) instanceof IntType type && type.isUnsigned) {
                return true;
            }
        }
        return false;
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
            if (declaration.isEmpty()
                    || !isEffectivelyConstant(declaration.get(), identifier, MutationScanner.Mode.COLLECTION)) {
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

    private static boolean isEvaluable(Class<? extends BinaryComparison> operator) {
        return operator == LtOp.class || operator == LeOp.class || operator == GtOp.class
                || operator == GeOp.class || operator == EqOp.class || operator == NotEqOp.class;
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
        throw new IllegalArgumentException("Unsupported comparison: " + operator.getName());
    }

    public record ComparisonModel(SimpleIdentifier identifier,
                                  long bound,
                                  Class<? extends BinaryComparison> operator) {
    }
}
