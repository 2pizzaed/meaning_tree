package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;

import java.util.Objects;

public class TernaryOperator extends Expression {
    @TreeNode private Expression condition;
    @TreeNode private Expression thenExpr;
    @TreeNode private Expression elseExpr;

    public TernaryOperator(Expression condition, Expression thenExpr, Expression elseExpr) {
        this.condition = condition;
        this.thenExpr = thenExpr;
        this.elseExpr = elseExpr;
    }

    public Expression getCondition() {
        return condition;
    }

    public Expression getThenExpr() {
        return thenExpr;
    }

    public Expression getElseExpr() {
        return elseExpr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TernaryOperator that = (TernaryOperator) o;
        return Objects.equals(condition, that.condition) && Objects.equals(thenExpr, that.thenExpr) && Objects.equals(elseExpr, that.elseExpr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), condition, thenExpr, elseExpr);
    }

    @Override
    public TernaryOperator clone() {
        TernaryOperator obj = (TernaryOperator) super.clone();
        obj.condition = condition.clone();
        obj.elseExpr = elseExpr.clone();
        obj.thenExpr = thenExpr.clone();
        return obj;
    }
}
