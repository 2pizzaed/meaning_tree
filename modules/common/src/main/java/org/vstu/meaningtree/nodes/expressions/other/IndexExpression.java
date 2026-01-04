package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;

import java.util.Objects;

public class IndexExpression extends Expression {
    @TreeNode private Expression expression;
    // index может содержать ExpressionSequence
    @TreeNode private Expression index;

    private boolean _preferPointers = false;

    public IndexExpression(Expression expr, Expression index) {
        this.expression = expr;
        this.index = index;
    }

    public IndexExpression(Expression expr, Expression index, boolean preferPointers) {
        this.expression = expr;
        this.index = index;
        _preferPointers = preferPointers;
    }

    public Expression getExpression() {
        return expression;
    }

    public Expression getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IndexExpression that = (IndexExpression) o;
        return Objects.equals(expression, that.expression) && Objects.equals(index, that.index) && _preferPointers == that._preferPointers;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression, index, _preferPointers);
    }

    @Override
    public IndexExpression clone() {
        IndexExpression obj = (IndexExpression) super.clone();
        obj.expression = expression.clone();
        obj.index = index.clone();
        obj._preferPointers = _preferPointers;
        return obj;
    }

    public boolean isPreferPointerRepresentation() {
        return _preferPointers;
    }
}
