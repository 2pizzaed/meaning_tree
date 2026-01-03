package org.vstu.meaningtree.nodes.expressions;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;

import java.util.Objects;

abstract public class UnaryExpression extends Expression {
    @TreeNode private Expression argument;

    public UnaryExpression(Expression argument) {
        this.argument = argument;
    }

    public Expression getArgument() {
        return argument;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        UnaryExpression that = (UnaryExpression) o;
        return Objects.equals(argument, that.argument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), argument);
    }

    @Override
    public UnaryExpression clone() {
        UnaryExpression obj = (UnaryExpression) super.clone();
        obj.argument = argument.clone();
        return obj;
    }
}
