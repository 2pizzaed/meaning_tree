package org.vstu.meaningtree.utils.analysis.symbols;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.scopes.ScopePolicy;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Индекс использований объявленных переменных: какому объявлению отвечает вхождение имени и где
 * по дереву это объявление используется.
 * <p>
 * До него ответить на второй вопрос было нечем. {@code OverloadCallResolver} проставляет
 * {@code resolvedDeclaration} только вызовам, {@link SymbolResolver} разбирает лишь присваивания
 * вида {@code self.x = ...}, а {@link ScopeTable} отвечает на вопрос «что видно отсюда», но не
 * «где это использовано». Любой анализ, которому нужно посмотреть на все обращения к переменной
 * — вывод типа по употреблению, поиск мёртвых объявлений, проверка инициализации до чтения —
 * начинался бы с собственного обхода.
 * <p>
 * Индекс языконезависим: границы областей задаются {@link ScopePolicy}, как и у
 * {@code ScopeTableBuilder}, а всё остальное — форма дерева, одинаковая для всех языков.
 *
 * <h2>Почему свой стек кадров, а не только {@link ScopeTable}</h2>
 * Таблица не знает параметров функций: {@link ScopeTable#register(Node)} перечисляет
 * определения, переменные, перечисления, импорты, {@code catch} и объявления привязок, но
 * {@link DeclarationArgument} не упомянут ни в одной ветке. Поэтому {@code char *s} из
 * {@code void f(char *s)} не нашёлся бы ни в какой области, а именно параметры чаще всего и
 * нужны анализу употреблений. Свой стек кадров закрывает этот пробел, не трогая таблицу и её
 * контракт.
 * <p>
 * Таблица при этом остаётся источником последней инстанции: имя, не найденное в кадрах
 * (глобальное, объявленное в обход обхода, пришедшее из десериализации), ищется через
 * {@link ScopeTable#inScopeOf}. Таблица только читается — ни {@code enter}, ни {@code bindScope},
 * ни перестроения: подмена привязанных областей сломала бы разрешение имён проходам, которые
 * идут следом.
 *
 * <h2>Порядок обхода</h2>
 * {@link MeaningTree#iterate()} отдаёт узлы в post-order, а кадрам нужен обратный порядок:
 * сначала объявление, потом то, что за ним следует. Поэтому узлы группируются по родителю
 * (относительный порядок детей одного родителя в post-order уже верный) и обходятся от корня
 * своей рекурсией — тот же приём, что в {@code ScopeTableBuilder}.
 * <p>
 * Объявление регистрируется до спуска в собственных детей, поэтому {@code int x = x;} считается
 * ссылкой на себя, а не на внешнее {@code x}. Это сознательное упрощение: конструкция
 * бессмысленна, а альтернатива — регистрация после детей — сломала бы куда более частое
 * {@code Node *p = p->next;} внутри цикла.
 *
 * <h2>Чего индекс не покрывает</h2>
 * Имя из {@code catch (E e)} не индексируется: {@code CatchClause} хранит имя
 * {@link SimpleIdentifier}, а не узлом-объявлением, и сослаться на него как на
 * {@link Declaration} не на что.
 */
public final class VariableUsageIndex {
    private final MeaningTree tree;
    private final ScopeTable scope;
    private final ScopePolicy policy;

    /** Использования по идентификатору узла-объявления: {@code equals} у узлов значимостный. */
    private final Map<Long, List<NodeInfo>> usagesByDeclarationId = new LinkedHashMap<>();
    private final Map<Long, Declaration> declarationsById = new LinkedHashMap<>();
    /** Объявление по идентификатору узла-вхождения имени. */
    private final Map<Long, Declaration> declarationByUseId = new HashMap<>();

    private final Deque<Map<SimpleIdentifier, Declaration>> frames = new ArrayDeque<>();
    private boolean built;

    public VariableUsageIndex(@NotNull MeaningTree tree, @NotNull ScopeTable scope,
                              @NotNull ScopePolicy policy) {
        this.tree = Objects.requireNonNull(tree, "tree must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * Строит индекс одним проходом. Повторный вызов пересобирает его с нуля — индекс
     * идемпотентен, поэтому его можно перестроить после правки дерева.
     */
    public VariableUsageIndex build() {
        usagesByDeclarationId.clear();
        declarationsById.clear();
        declarationByUseId.clear();
        frames.clear();
        frames.push(new HashMap<>());

        Map<Long, List<NodeInfo>> childrenByParentId = groupByParent(tree.iterate());
        visit(tree.getNodeById(tree.getRootNode().getId()), childrenByParentId);

        frames.clear();
        built = true;
        return this;
    }

    /** Объявление, к которому относится вхождение имени, если его удалось найти. */
    public Optional<Declaration> declarationOf(@NotNull NodeInfo use) {
        requireBuilt();
        return Optional.ofNullable(declarationByUseId.get(use.id()));
    }

    /**
     * Все вхождения имени, относящиеся к объявлению, кроме имени в самом объявлении.
     * Порядок — порядок обхода дерева.
     */
    public List<NodeInfo> usagesOf(@NotNull Declaration declaration) {
        requireBuilt();
        return List.copyOf(usagesByDeclarationId.getOrDefault(declaration.getId(), List.of()));
    }

    /** Объявления, которые обход встретил в самом дереве, в порядке встречи. */
    public List<Declaration> indexedDeclarations() {
        requireBuilt();
        return List.copyOf(declarationsById.values());
    }

    private void requireBuilt() {
        if (!built) {
            throw new IllegalStateException("VariableUsageIndex is not built yet: call build() first");
        }
    }

    private static Map<Long, List<NodeInfo>> groupByParent(List<NodeInfo> flat) {
        Map<Long, List<NodeInfo>> childrenByParentId = new HashMap<>();
        for (NodeInfo info : flat) {
            NodeInfo parent = info.parent();
            if (parent == null) {
                continue;
            }
            childrenByParentId.computeIfAbsent(parent.id(), id -> new ArrayList<>()).add(info);
        }
        return childrenByParentId;
    }

    private void visit(NodeInfo info, Map<Long, List<NodeInfo>> childrenByParentId) {
        declare(info.node());
        if (info.node() instanceof SimpleIdentifier name && isUsage(info)) {
            resolve(name, info).ifPresent(declaration -> record(declaration, info));
        }

        boolean entered = info.node() instanceof CompoundStatement body
                && policy.opensScope(body, info.parentNode());
        if (entered) {
            frames.push(new HashMap<>());
            declareParameters(info.parentNode());
        }
        for (NodeInfo child : childrenByParentId.getOrDefault(info.id(), List.of())) {
            visit(child, childrenByParentId);
        }
        if (entered) {
            frames.pop();
        }
    }

    /**
     * Кадр тела функции открывается уже с её параметрами: по дереву параметры лежат на
     * объявлении, то есть вне тела, а видны именно в теле.
     */
    private void declareParameters(@Nullable Node bodyOwner) {
        if (!(bodyOwner instanceof FunctionDefinition definition)) {
            return;
        }
        for (DeclarationArgument argument : definition.getDeclaration().getArguments()) {
            bind(argument.getName(), argument);
        }
    }

    private void declare(Node node) {
        if (node instanceof SeparatedVariableDeclaration separated) {
            for (VariableDeclaration declaration : separated.getDeclarations()) {
                declare(declaration);
            }
            return;
        }
        if (node instanceof VariableDeclaration declaration) {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                bind(declarator.getIdentifier(), declaration);
            }
        }
    }

    private void bind(SimpleIdentifier name, Declaration declaration) {
        Objects.requireNonNull(frames.peek()).put(name, declaration);
        declarationsById.putIfAbsent(declaration.getId(), declaration);
    }

    private Optional<Declaration> resolve(SimpleIdentifier name, NodeInfo info) {
        for (Map<SimpleIdentifier, Declaration> frame : frames) {
            Declaration declaration = frame.get(name);
            if (declaration != null) {
                return Optional.of(declaration);
            }
        }
        return scope.inScopeOf(info, () -> scope.getVariableDeclaration(name, null))
                .map(Declaration.class::cast);
    }

    private void record(Declaration declaration, NodeInfo use) {
        declarationByUseId.put(use.id(), declaration);
        declarationsById.putIfAbsent(declaration.getId(), declaration);
        usagesByDeclarationId.computeIfAbsent(declaration.getId(), id -> new ArrayList<>()).add(use);
    }

    /**
     * Имя в самом объявлении — не использование, иначе объявление считалось бы собственным
     * употреблением и любая проверка «переменная где-то читается» всегда была бы истинной.
     * Член в {@code a.length} тоже не использование: это имя поля, а не переменной {@code length}.
     */
    private static boolean isUsage(NodeInfo info) {
        Node parent = info.parentNode();
        String field = info.field() == null ? null : info.field().getName();
        if (parent instanceof VariableDeclarator && "identifier".equals(field)) {
            return false;
        }
        if (parent instanceof DeclarationArgument && "name".equals(field)) {
            return false;
        }
        return !(parent instanceof MemberAccess && "member".equals(field));
    }
}
