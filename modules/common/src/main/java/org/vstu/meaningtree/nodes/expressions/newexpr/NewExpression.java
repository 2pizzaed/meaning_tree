package org.vstu.meaningtree.nodes.expressions.newexpr;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;

import java.util.Objects;

public abstract class NewExpression extends Expression {
    @TreeNode protected Type type;

    /**
     * Объект создаётся на стеке, а не в динамической памяти: C++ Box a(1) вместо new Box(1).
     * Признак значим только для языков с ручным управлением памятью, остальные его игнорируют.
     */
    private boolean stackAllocated = false;

    protected NewExpression(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public boolean isStackAllocated() {
        return stackAllocated;
    }

    public NewExpression setStackAllocated(boolean stackAllocated) {
        this.stackAllocated = stackAllocated;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewExpression that = (NewExpression) o;
        return stackAllocated == that.stackAllocated && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type, stackAllocated);
    }

    @Override
    public NewExpression clone() {
        NewExpression obj = (NewExpression) super.clone();
        obj.type = type.clone();
        return obj;
    }
}
