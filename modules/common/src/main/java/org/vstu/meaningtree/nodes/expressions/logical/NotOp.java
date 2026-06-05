package org.vstu.meaningtree.nodes.expressions.logical;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;

public class NotOp extends UnaryExpression {
    public NotOp(Expression argument) {
        super(argument);
    }
}
