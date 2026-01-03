package org.vstu.meaningtree.nodes.expressions.comparison;

import org.vstu.meaningtree.nodes.Expression;

public class LeOp extends BinaryComparison {
    public LeOp(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public Expression tryInvert() {
        return new GtOp(this.getLeft(), this.getRight());
    }
}
