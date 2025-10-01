package org.vstu.meaningtree.nodes.types.builtin;

import org.vstu.meaningtree.nodes.Type;

public abstract class NumericType extends Type {
    public final int size;

    protected NumericType(int size) {
        this.size = size;
    }

    protected NumericType() {
        this.size = 32;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }
}
