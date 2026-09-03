package org.vstu.meaningtree.utils.analysis;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.LanguageBehavior;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.scopes.ScopePolicy;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Строит {@link ScopeTable} с нуля отдельным проходом по уже готовому {@link MeaningTree} —
 * вместо попутного наполнения во время разбора ({@code BodyConstructor}/
 * {@code TranslatorContext.registerInScope}). Поэтому работает на любом дереве: только что
 * распарсенном, десериализованном из JSON или собранном программно/тестом.
 * <p>
 * Правила языка, из которого дерево получено, задаёт {@link LanguageBehavior}: единого правила
 * здесь быть не может, потому что в Java и C++ область открывает любой блок, а в Python только
 * определение. Правила передаются явно, без умолчания, — иначе проход молча приписывал бы
 * дереву чужие правила видимости.
 * <p>
 * Проход по готовому дереву заодно закрывает пробел попутной сборки: C++ строит тела классов и
 * единицу трансляции в обход {@code BodyConstructor} (см. {@code CppParser.fromTranslationUnit},
 * class/struct/interface body), поэтому эти {@code CompoundStatement} никогда не получают
 * {@code scope}, и свободные функции/методы попадают в {@code ScopeTable} только благодаря
 * fallback на корневую область в {@code OverloadIndexer}.
 * <p>
 * Диспетчеризация "как зарегистрировать узел по его типу" живёт в
 * {@link ScopeTable#register(Node)} и общая с попутным наполнением при разборе, поэтому два
 * пути регистрации не могут разойтись.
 * <p>
 * {@link MeaningTree#iterate()} отдаёт узлы в post-order (потомки раньше родителя — см.
 * {@code DFSNodeIterator}), а для входа/выхода из области видимости через
 * {@link ScopeTable#enter(Node)}/{@link ScopeTable#leave()} нужен обратный порядок: сначала
 * узел-владелец, потом содержимое. Поэтому вместо прямого использования этого порядка сначала
 * группируем узлы по родителю (относительный порядок детей одного родителя в post-order уже
 * верный), а затем идём от корня обычной рекурсией — это и даёт нужный pre-order.
 */
public final class ScopeTableBuilder {
    private ScopeTableBuilder() {
    }

    /**
     * Правила языка принимаются целиком, а не по одной черте, потому что перестроенная таблица
     * обязана совпасть с той, что собрал разбор. Пока границы областей и правило связывания
     * передавались двумя аргументами, их можно было взять от разных языков — и получить
     * таблицу, описывающую программу, которой нет.
     */
    public static ScopeTable build(MeaningTree tree, LanguageBehavior behavior) {
        ScopeTable scope = new ScopeTable();
        scope.setAssignmentBinding(behavior.assignmentBinding());
        Map<Long, List<NodeInfo>> childrenByParentId = groupByParent(tree.iterate());
        NodeInfo root = tree.getNodeById(tree.getRootNode().getId());
        visit(root, scope, childrenByParentId, behavior.scopePolicy());
        return scope;
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

    private static void visit(NodeInfo info, ScopeTable scope,
                              Map<Long, List<NodeInfo>> childrenByParentId, ScopePolicy policy) {
        scope.register(info.node());

        boolean entered = info.node() instanceof CompoundStatement body
                && policy.opensScope(body, info.parent() == null ? null : info.parent().node());
        if (entered) {
            scope.enter(info.node());
        }
        for (NodeInfo child : childrenByParentId.getOrDefault(info.id(), List.of())) {
            visit(child, scope, childrenByParentId, policy);
        }
        if (entered) {
            scope.leave();
        }
    }

}
