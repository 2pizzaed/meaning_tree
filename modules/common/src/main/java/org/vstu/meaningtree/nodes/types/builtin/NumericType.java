package org.vstu.meaningtree.nodes.types.builtin;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.PrimitiveType;
import org.vstu.meaningtree.nodes.interfaces.ScalarType;

public abstract class NumericType extends Type implements PrimitiveType, ScalarType {
    public final int size;

    protected NumericType(int size) {
        this.size = size;
    }

    protected NumericType() {
        this.size = 32;
    }

    public int getBitsize() {
        return size;
    }

}
