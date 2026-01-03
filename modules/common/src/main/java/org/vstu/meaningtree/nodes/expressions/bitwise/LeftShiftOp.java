package org.vstu.meaningtree.nodes.expressions.bitwise;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;

public class LeftShiftOp extends BinaryExpression {
    public LeftShiftOp(Expression left, Expression right) {
        super(left, right);
    }

}
