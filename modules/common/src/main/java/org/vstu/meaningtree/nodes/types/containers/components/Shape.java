package org.vstu.meaningtree.nodes.types.containers.components;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.InternalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@InternalNode
public class Shape extends Node {
    // Количество измерений
    private final int dimensionCount;
    // Размер каждого измерения
    @TreeNode private List<Expression> dimensions;

    public Shape(int dimensionCount) {
        this.dimensionCount = dimensionCount;
        dimensions = new ArrayList<>();
        for (int i = 0; i < dimensionCount; i++) {
            dimensions.add(null);
        };
    }

    public Shape(int dimensionCount, Expression... dimensions) {
        this(dimensionCount, List.of(dimensions));
    }

    public Shape(int dimensionCount, List<Expression> dimensions) {
        if (dimensionCount != dimensions.size()) {
            throw new IllegalArgumentException();
        }

        this.dimensionCount = dimensionCount;
        this.dimensions = new ArrayList<>(dimensions);
    }

    public int getDimensionCount() {
        return dimensionCount;
    }

    @Nullable
    public Expression getDimension(int index) {
        try {
            return dimensions.get(index);
        }
        catch (IndexOutOfBoundsException exception) {
            return null;
        }
    }

    public List<Expression> getDimensions() {
        return dimensions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Shape shape = (Shape) o;
        return dimensionCount == shape.dimensionCount && Objects.equals(dimensions, shape.dimensions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dimensionCount, dimensions);
    }

    @Override
    public Shape clone() {
        Shape obj = (Shape) super.clone();
        obj.dimensions = new ArrayList<>(dimensions.stream()
                .map(expr -> expr == null ? null : expr.clone())
                .toList());
        return obj;
    }
}
