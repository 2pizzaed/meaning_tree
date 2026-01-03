package org.vstu.meaningtree.nodes.definitions.components;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.Objects;
public class DefinitionArgument extends Expression {
    public SimpleIdentifier getName() {
        return name;
    }

    @Nullable @TreeNode private SimpleIdentifier name;
    @TreeNode private Expression initial;

    private boolean isListUnpacking;
    private boolean isDictUnpacking; // special for python

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DefinitionArgument that = (DefinitionArgument) o;
        return Objects.equals(name, that.name) && Objects.equals(initial, that.initial) &&
                Objects.equals(isDictUnpacking, this.isDictUnpacking) &&
                Objects.equals(isListUnpacking, that.isListUnpacking);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, initial, isDictUnpacking, isListUnpacking);
    }

    public DefinitionArgument(SimpleIdentifier name, Expression initial) {
        super();
        this.name = name;
        this.initial = Objects.requireNonNull(initial);
    }

    DefinitionArgument(Expression initial) {
        super();
        this.name = null;
        this.initial = Objects.requireNonNull(initial);
    }

    public static DefinitionArgument listUnpacking(Expression initial) {
        var res = new DefinitionArgument(initial);
        res.isListUnpacking = true;
        return res;
    }

    public static DefinitionArgument dictUnpacking(Expression initial) {
        var res = new DefinitionArgument(initial);
        res.isDictUnpacking = true;
        return res;
    }

    public boolean hasVisibleName() {
        return name != null;
    }

    public boolean isListUnpacking() {
        return isListUnpacking;
    }

    public boolean isDictUnpacking() {
        return isDictUnpacking;
    }

    public Expression getInitialExpression() {
        return initial;
    }

    @Override
    public DefinitionArgument clone() {
        DefinitionArgument obj = (DefinitionArgument) super.clone();
        obj.name = name.clone();
        obj.initial = initial.clone();
        return obj;
    }

}
