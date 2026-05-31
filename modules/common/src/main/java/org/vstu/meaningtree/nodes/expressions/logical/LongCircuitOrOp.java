package org.vstu.meaningtree.nodes.expressions.logical;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;

public class LongCircuitOrOp extends BinaryExpression {
    public LongCircuitOrOp(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public boolean canBeEvaluatedToBoolean() {
        return true;
    }
}
