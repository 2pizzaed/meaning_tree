package org.vstu.meaningtree.nodes.types;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;

import java.util.Objects;

/**
 * Тип, значение которого может быть не задано (если это явно указано). Только для случаев: Optional<T>, T?, T | None
 */
public class OptionalType extends Type {
    @TreeNode private Type targetType;

    public OptionalType(@NotNull Type targetType) {
        this.targetType = targetType;
    }

    public Type getTargetType() {
        return targetType;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        OptionalType that = (OptionalType) o;
        return Objects.equals(targetType, that.targetType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targetType);
    }

    @Override
    public OptionalType clone() {
        OptionalType obj = (OptionalType) super.clone();
        obj.targetType = targetType.clone();
        return obj;
    }
}
