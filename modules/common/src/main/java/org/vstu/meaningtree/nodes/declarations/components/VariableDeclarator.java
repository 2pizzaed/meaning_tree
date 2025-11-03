package org.vstu.meaningtree.nodes.declarations.components;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.Objects;

public class VariableDeclarator extends Node {
    @TreeNode private SimpleIdentifier identifier;
    @TreeNode @Nullable private Expression rvalue;

    public VariableDeclarator(SimpleIdentifier identifier, @Nullable Expression rvalue) {
        this.identifier = identifier;
        this.rvalue = rvalue;
    }

    public VariableDeclarator(SimpleIdentifier identifier) {
        this(identifier, null);
    }

    @Nullable
    public Expression getRValue() {
        return rvalue;
    }

    public boolean hasInitialization() {
        return rvalue != null;
    }

    public SimpleIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VariableDeclarator that = (VariableDeclarator) o;
        return Objects.equals(identifier, that.identifier) && Objects.equals(rvalue, that.rvalue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), identifier, rvalue);
    }

    public VariableDeclarator clone() {
        var clone = (VariableDeclarator) super.clone();
        clone.identifier = identifier.clone();
        clone.rvalue = rvalue.clone();
        return clone;
    }
}
