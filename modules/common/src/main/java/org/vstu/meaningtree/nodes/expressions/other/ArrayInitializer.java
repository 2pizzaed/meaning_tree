package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.utils.InternalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@InternalNode
public class ArrayInitializer extends Expression {
    @TreeNode private List<Expression> values;

    public ArrayInitializer(List<Expression> values) {
        this.values = List.copyOf(values);
    }

    public List<Expression> getValues() {
        return List.copyOf(values);
    }

    public int getValuesCount() {
        return values.size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArrayInitializer that = (ArrayInitializer) o;
        return Objects.equals(values, that.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), values);
    }

    @Override
    public ArrayInitializer clone() {
        ArrayInitializer obj = (ArrayInitializer) super.clone();
        obj.values = new ArrayList<>(values);
        return obj;
    }
}
