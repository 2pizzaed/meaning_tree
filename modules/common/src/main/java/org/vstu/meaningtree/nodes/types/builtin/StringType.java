package org.vstu.meaningtree.nodes.types.builtin;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.SequenceType;

import java.util.Objects;

public class StringType extends Type implements SequenceType {
    public final int charSize;

    public StringType() {
        charSize = 16;
    }

    public StringType(int charSize) {
        this.charSize = Math.min(Math.max(charSize, 8), 32);
    }

    public boolean isUnicode() {
        return charSize >= 16;
    }

    public int getCharSize() {
        return charSize;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        StringType that = (StringType) o;
        return charSize == that.charSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), charSize);
    }
}
