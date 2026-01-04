package org.vstu.meaningtree.nodes.expressions.comparison;


import org.vstu.meaningtree.nodes.Expression;

public class GeOp extends BinaryComparison {
    public GeOp(Expression left, Expression right) {
        super(left, right);
    }

    @Override
    public Expression tryInvert() {
        return new LtOp(this.getLeft(), this.getRight());
    }


}
