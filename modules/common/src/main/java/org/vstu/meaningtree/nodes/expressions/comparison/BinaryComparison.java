package org.vstu.meaningtree.nodes.expressions.comparison;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;

public abstract class BinaryComparison extends BinaryExpression {

    public BinaryComparison(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public boolean canBeEvaluatedToBoolean() {
        return true;
    }

    public Expression inverse() {
        if (this instanceof GtOp) {
            return new LeOp(left, right);
        } else if (this instanceof LtOp) {
            return new GeOp(left, right);
        } else if (this instanceof EqOp) {
            return new NotEqOp(left, right);
        } else if (this instanceof NotEqOp) {
            return new EqOp(left, right);
        } else if (this instanceof GeOp) {
            return new LtOp(left, right);
        } else if (this instanceof LeOp) {
            return new GtOp(left, right);
        }
        return this;
    }
}
