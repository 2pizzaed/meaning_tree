package org.vstu.meaningtree.nodes.types.containers;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.PrimitiveType;
import org.vstu.meaningtree.nodes.interfaces.SequenceType;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;

import java.util.List;
import java.util.Objects;

public class ArrayType extends PlainCollectionType implements PrimitiveType, SequenceType {

    @TreeNode private Shape shape;

    public ArrayType(Type itemType, int dimensionCount) {
        super(itemType);
        shape = new Shape(dimensionCount);
    }

    public ArrayType(Type itemType, Expression size) {
        super(itemType);
        shape = new Shape(1, size);
    }

    public ArrayType(Type itemType, int dimensionCount, Expression... dimensions) {
        this(itemType, dimensionCount, List.of(dimensions));
    }

    public ArrayType(Type itemType, int dimensionCount, List<Expression> dimensions) {
        super(itemType);
        shape = new Shape(dimensionCount, dimensions);
    }

    public Shape getShape() {
        return shape;
    }

    public int getDimensionsCount() {
        return shape.getDimensionCount();
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ArrayType arrayType = (ArrayType) o;
        return Objects.equals(shape, arrayType.shape);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), shape);
    }

    @Override
    public ArrayType clone() {
        ArrayType obj = (ArrayType) super.clone();
        obj.shape = shape.clone();
        return obj;
    }
}
