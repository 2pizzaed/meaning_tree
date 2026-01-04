package org.vstu.meaningtree.nodes.expressions;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;

import java.util.Objects;

public class ParenthesizedExpression extends Expression {
    @TreeNode private Expression expression;

    public ParenthesizedExpression(Expression expr) {
        expression = expr;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParenthesizedExpression that = (ParenthesizedExpression) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression);
    }

    @Override
    public ParenthesizedExpression clone() {
        ParenthesizedExpression obj = (ParenthesizedExpression) super.clone();
        obj.expression = expression.clone();
        return obj;
    }

    @Override
    public void setAssignedValueTag(@Nullable Object obj) {
        expression.setAssignedValueTag(obj);
    }

    @Nullable
    @Override
    public Object getAssignedValueTag() {
        return expression.getAssignedValueTag();
    }
}
