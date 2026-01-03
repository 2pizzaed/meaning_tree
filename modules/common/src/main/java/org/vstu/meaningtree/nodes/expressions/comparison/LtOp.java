package org.vstu.meaningtree.nodes.expressions.comparison;

import org.vstu.meaningtree.nodes.Expression;

public class LtOp extends BinaryComparison {
    public LtOp(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public Expression tryInvert() {
        return new GeOp(this.getLeft(), this.getRight());
    }
}
