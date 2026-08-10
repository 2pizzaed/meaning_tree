package org.vstu.meaningtree.nodes.expressions.calls;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;

import java.util.Objects;

public class DestructorCall extends Expression {
    @TreeNode private Type destructorOwner;

    public DestructorCall(Type destructorOwner) {
        this.destructorOwner = destructorOwner;
    }

    public Type getOwner() {
        return destructorOwner;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DestructorCall that
                && super.equals(o)
                && Objects.equals(destructorOwner, that.destructorOwner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), destructorOwner);
    }

    @Override
    public DestructorCall clone() {
        DestructorCall clone = (DestructorCall) super.clone();
        clone.destructorOwner = destructorOwner.clone();
        return clone;
    }
}
