package org.vstu.meaningtree.nodes.statements;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;

import java.util.Objects;

public class ReturnStatement extends Statement {
    @TreeNode private Expression expression;

    public ReturnStatement(Expression expr) {
        expression = expr;
    }

    public ReturnStatement() {
        this(null);
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Nullable
    public Expression getExpression() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ReturnStatement nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(expression, nodeInfos.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression);
    }

    public ReturnStatement clone() {
        var clone = (ReturnStatement) super.clone();
        clone.expression = expression.clone();
        return clone;
    }
}
