package org.vstu.meaningtree.nodes.expressions.comparison;

import org.vstu.meaningtree.nodes.Expression;

public class NotEqOp extends BinaryComparison {
    public NotEqOp(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public Expression tryInvert() {
        return new EqOp(this.getLeft(), this.getRight());
    }
}