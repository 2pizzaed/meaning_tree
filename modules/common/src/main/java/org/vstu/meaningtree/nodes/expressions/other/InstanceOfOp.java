package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;

public class InstanceOfOp extends BinaryExpression {
    public InstanceOfOp(Expression left, Type right) {
        super(left, right);
    }

    public Type getType() {
        return (Type) getRight();
    }
}
