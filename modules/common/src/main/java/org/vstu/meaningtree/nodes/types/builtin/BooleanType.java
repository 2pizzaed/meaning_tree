package org.vstu.meaningtree.nodes.types.builtin;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.PrimitiveType;
import org.vstu.meaningtree.nodes.interfaces.ScalarType;

public class BooleanType extends Type implements PrimitiveType, ScalarType {
    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }
}
