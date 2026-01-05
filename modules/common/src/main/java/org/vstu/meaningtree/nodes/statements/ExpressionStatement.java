package org.vstu.meaningtree.nodes.statements;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;

import java.util.Objects;

public class ExpressionStatement extends Statement {
    @TreeNode protected Expression expression;

    public ExpressionStatement(Expression expr) {
        expression = expr;
    }

    public Expression getExpression() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ExpressionStatement that = (ExpressionStatement) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression);
    }

    @Override
    public ExpressionStatement clone() {
        ExpressionStatement obj = (ExpressionStatement) super.clone();
        if (expression != null) obj.expression = expression.clone();
        return obj;
    }
}
