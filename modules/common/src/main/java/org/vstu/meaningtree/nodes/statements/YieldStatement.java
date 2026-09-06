package org.vstu.meaningtree.nodes.statements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.iterators.utils.NodeInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Выдача очередного значения из генератора: python-<code>yield e</code>, <code>yield</code>
 * и <code>yield from e</code>.
 * <p>
 * Это оператор, а не выражение. Python допускает <code>yield</code> и в позиции выражения
 * (<code>x = yield v</code>), но это часть двустороннего протокола <code>send</code>, который
 * модель не выражает; такой <code>yield</code> отвергается при разборе, а не теряется молча.
 * <p>
 * Делегирование (<code>yield from</code>) — флаг {@link #isDelegated()}, а не отдельный узел:
 * от обычной выдачи оно отличается только тем, что выражение даёт не одно значение, а
 * последовательность. Флаг доживает до отрисовки, поэтому python → python сохраняет
 * <code>yield from</code> дословно; для языков без генераторов делегирование разворачивается
 * в цикл при понижении.
 */
public class YieldStatement extends Statement {
    @TreeNode @Nullable private Expression value;

    private final boolean delegated;

    public YieldStatement(@Nullable Expression value, boolean delegated) {
        if (delegated && value == null) {
            throw new IllegalArgumentException("Delegated yield requires an expression to delegate to");
        }
        this.value = value;
        this.delegated = delegated;
    }

    public YieldStatement(@Nullable Expression value) {
        this(value, false);
    }

    public YieldStatement() {
        this(null, false);
    }

    /**
     * Создаёт <code>yield from e</code>.
     */
    public static YieldStatement delegating(@NotNull Expression value) {
        return new YieldStatement(Objects.requireNonNull(value), true);
    }

    /**
     * Выдаваемое значение или <code>null</code> у голого <code>yield</code>.
     * У делегирующей выдачи здесь стоит источник последовательности.
     */
    @Nullable
    public Expression getValue() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    /**
     * <code>true</code> для <code>yield from e</code>: выражение даёт последовательность,
     * каждый элемент которой выдаётся по очереди.
     */
    public boolean isDelegated() {
        return delegated;
    }

    /**
     * Выдачи, принадлежащие этому поддереву. Во вложенные определения обход не заходит: их
     * выдачи делают генератором вложенную функцию, а не ту, чьё тело здесь просматривается.
     * <p>
     * Проверка идёт по цепочке родителей, а не отсечением обхода: {@link Node#iterate(boolean)}
     * обходит всё поддерево целиком, и пропуск самого узла-определения не мешает ему спуститься
     * внутрь.
     */
    public static List<YieldStatement> ownedBy(@NotNull Node root) {
        List<YieldStatement> owned = new ArrayList<>();
        if (root instanceof YieldStatement yield) {
            owned.add(yield);
        }
        for (NodeInfo info : root.iterate(false)) {
            if (info.node() instanceof YieldStatement yield && !isInsideNestedDefinition(info, root)) {
                owned.add(yield);
            }
        }
        return owned;
    }

    /**
     * Есть ли в поддереве хотя бы одна принадлежащая ему выдача.
     */
    public static boolean isYieldedBy(@NotNull Node root) {
        return !ownedBy(root).isEmpty();
    }

    private static boolean isInsideNestedDefinition(NodeInfo info, Node root) {
        for (NodeInfo parent = info.parent(); parent != null; parent = parent.parent()) {
            if (parent.node() == root) {
                return false;
            }
            if (parent.node() instanceof Definition) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof YieldStatement other)) return false;
        if (!super.equals(o)) return false;
        return delegated == other.delegated && Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), value, delegated);
    }

    @Override
    public YieldStatement clone() {
        var clone = (YieldStatement) super.clone();
        clone.value = value == null ? null : value.clone();
        return clone;
    }
}
