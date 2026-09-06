package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;

import java.util.ArrayList;
import java.util.List;

/**
 * Разбивает ветвь, перехватывающую несколько типов сразу (Java multi-catch
 * {@code catch (A | B e)}, python-кортеж {@code except (A, B) as e}), на отдельные ветви — по
 * одной на тип. Нужен языкам, где {@code catch} принимает ровно один тип: в C++ иначе такую
 * конструкцию не выразить.
 * <p>
 * Тело копируется в каждую ветвь: обработчик один и тот же, но синтаксически он обязан быть
 * записан столько раз, сколько типов. Копия делается через {@code freshClone}, потому что
 * обычный {@code clone} сохраняет id, и два экземпляра одного тела дали бы в дереве
 * повторяющиеся id.
 * <p>
 * Работает на клоне входного дерева и никогда не меняет дерево вызывающего.
 */
public final class MultiCatchSplitter {
    private MultiCatchSplitter() {
    }

    public static MeaningTree lower(MeaningTree source) {
        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        for (NodeInfo info : result) {
            if (info.node() instanceof ExceptionCatchStatement stmt) {
                split(stmt);
            }
        }
        return result;
    }

    private static void split(ExceptionCatchStatement stmt) {
        List<CatchClause> expanded = new ArrayList<>();
        boolean changed = false;

        for (CatchClause clause : stmt.getCatchClauses()) {
            if (clause.getExceptionTypes().size() < 2) {
                expanded.add(clause);
                continue;
            }
            changed = true;
            boolean first = true;
            for (Type exceptionType : clause.getExceptionTypes()) {
                // Первая ветвь забирает исходные узлы, остальные получают копии со свежими id:
                // с обычным clone два экземпляра одного тела дали бы в дереве повторяющиеся id
                expanded.add(new CatchClause(
                        first ? exceptionType : copy(exceptionType, clause),
                        clause.hasName() ? (first ? clause.getName() : copy(clause.getName(), clause)) : null,
                        first ? clause.getBody() : copy(clause.getBody(), clause)
                ).remap(clause));
                first = false;
            }
        }

        if (changed) {
            stmt.setCatchClauses(expanded);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Node> T copy(T node, Node origin) {
        return remapSyntheticTree((T) node.freshClone(), origin);
    }

    private static <T extends Node> T remapSyntheticTree(T node, Node origin) {
        node.remap(origin);
        for (NodeInfo info : node) {
            info.node().remap(origin);
        }
        return node;
    }
}
