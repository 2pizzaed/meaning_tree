package org.vstu.meaningtree.languages.helpers;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.JumpStatement;

import java.util.ArrayList;
import java.util.List;

/**
 * Куда ведут {@code break} и {@code continue}. Переход с меткой ведёт к охватывающему циклу с
 * этой меткой. {@code continue} без метки — к ближайшему циклу, {@code break} без метки — к
 * ближайшему циклу или {@code switch}, если switch его перехватывает (в дереве и в C-подобных
 * языках — да, в Python {@code match} — нет). Ветка else цикла выполняется после него, поэтому
 * переход из неё к самому циклу не относится. Функция, класс и выражение (тело лямбды) переход
 * не пропускают.
 */
public final class JumpTargets {
    private JumpTargets() {
    }

    /**
     * @param jump узел перехода из обхода; цель ищется среди его предков в этом обходе
     * @return цикл или switch, из которого выходит переход, либо {@code null}, если цели нет
     *         среди предков или путь к ней закрыт функцией или лямбдой
     */
    public static @Nullable Statement target(NodeInfo jump, boolean switchCapturesBreak) {
        JumpStatement statement = (JumpStatement) jump.node();
        JumpLabel label = statement.getJumpDestination();
        NodeInfo child = jump;
        for (NodeInfo current = jump.parent(); current != null; child = current, current = current.parent()) {
            Node node = current.node();
            if (node instanceof Definition || node instanceof Expression) {
                return null;
            }
            if (label != null) {
                if (node instanceof Loop loop && entersBody(loop, child.node()) && hasLabel(loop, label)) {
                    return loop;
                }
            } else if (capturesBreak(node, child.node(), switchCapturesBreak && statement instanceof BreakStatement)) {
                // continue сквозь switch проходит
                return (Statement) node;
            }
        }
        return null;
    }

    /**
     * Циклы и switch между переходом и его целью, из которых по очереди вышел бы обычный
     * {@code break}, стоящий на месте перехода, — от внутреннего к внешнему.
     */
    public static List<NodeInfo> crossed(NodeInfo jump, Node target, boolean switchCapturesBreak) {
        List<NodeInfo> result = new ArrayList<>();
        NodeInfo child = jump;
        for (NodeInfo current = jump.parent(); current != null && current.node() != target;
             child = current, current = current.parent()) {
            if (capturesBreak(current.node(), child.node(), switchCapturesBreak)) {
                result.add(current);
            }
        }
        return result;
    }

    private static boolean capturesBreak(Node node, Node child, boolean switchCapturesBreak) {
        return node instanceof Loop loop && entersBody(loop, child)
                || switchCapturesBreak && node instanceof SwitchStatement;
    }

    private static boolean entersBody(Loop loop, Node child) {
        return !(loop.hasElseBranch() && loop.getElseBranch() == child);
    }

    private static boolean hasLabel(Loop loop, JumpLabel label) {
        return loop.getJumpLabel() != null && loop.getJumpLabel().getName().equals(label.getName());
    }
}
