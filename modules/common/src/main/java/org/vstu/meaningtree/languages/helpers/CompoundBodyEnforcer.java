package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;

import java.util.ArrayList;
import java.util.List;

/**
 * Оборачивает одиночные тела циклов и веток {@code if} в {@link CompoundStatement} — разборная
 * часть параметра {@link org.vstu.meaningtree.languages.configs.ConfigParameters#forceCompoundBodies}.
 * <p>
 * Ветка {@code else}, которая сама является {@code if}, остаётся как есть: иначе цепочка
 * {@code else if} превратилась бы в {@code else { if ... }}. Новый блок ссылается на тело,
 * которое обернул, через {@link Node#remap}, чтобы карта исходников вела к исходному коду.
 * Меняет переданное дерево.
 */
public final class CompoundBodyEnforcer {
    private CompoundBodyEnforcer() {
    }

    public static void apply(MeaningTree tree) {
        // Сначала собрать, потом менять: обход не переживает подмену детей на ходу
        List<Node> targets = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info.node() instanceof Loop || info.node() instanceof IfStatement) {
                targets.add(info.node());
            }
        }
        for (Node node : targets) {
            switch (node) {
                case Loop loop -> enforce(loop);
                case IfStatement ifStatement -> enforce(ifStatement);
                default -> throw new IllegalStateException("Unexpected node " + node.getClass());
            }
        }
    }

    private static void enforce(Loop loop) {
        Statement body = loop.getBody();
        if (!(body instanceof CompoundStatement)) {
            loop.makeCompoundBody().remap(body);
        }
        if (loop.hasElseBranch()) {
            Statement elseBody = loop.getElseBranch();
            if (!(elseBody instanceof CompoundStatement)) {
                loop.makeCompoundElseBody().remap(elseBody);
            }
        }
    }

    private static void enforce(IfStatement ifStatement) {
        for (ConditionBranch branch : ifStatement.getBranches()) {
            Statement body = branch.getBody();
            if (!(body instanceof CompoundStatement)) {
                branch.makeCompoundBody().remap(body);
            }
        }
        if (ifStatement.hasElseBranch()) {
            Statement elseBranch = ifStatement.getElseBranch();
            if (!(elseBranch instanceof CompoundStatement) && !(elseBranch instanceof IfStatement)) {
                ifStatement.setElseBranch(new CompoundStatement(elseBranch).remap(elseBranch));
            }
        }
    }
}
