package org.vstu.meaningtree.nodes.expressions.newexpr;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.other.ArrayInitializer;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;

import java.util.Objects;

public class ArrayNewExpression extends NewExpression {
    @TreeNode private Shape shape;
    @TreeNode private ArrayInitializer initializer;

    public ArrayNewExpression(Type type, Shape shape, ArrayInitializer initializer) {
        super(type);
        this.shape = shape;
        this.initializer = initializer;
    }

    public ArrayNewExpression(Type type, Shape shape) {
        this(type, shape, null);
    }

    public Shape getShape() {
       return shape;
    }

    public int getDimensionsCount() {
        return shape.getDimensionCount();
    }

    @Nullable
    public ArrayInitializer getInitializer() {
        return initializer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ArrayNewExpression that = (ArrayNewExpression) o;
        return Objects.equals(shape, that.shape) && Objects.equals(initializer, that.initializer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), shape, initializer);
    }

    @Override
    public ArrayNewExpression clone() {
        ArrayNewExpression obj = (ArrayNewExpression) super.clone();
        obj.shape = shape.clone();
        if (initializer != null) obj.initializer = initializer.clone();
        return obj;
    }
}
