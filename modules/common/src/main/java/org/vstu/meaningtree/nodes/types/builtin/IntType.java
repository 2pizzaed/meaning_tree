package org.vstu.meaningtree.nodes.types.builtin;

import java.util.Objects;

public class IntType extends NumericType {
    public final boolean isUnsigned;

    /**
     * Создаёт знаковый целочисленный тип размерностью 32
     */
    public IntType() {
        this(32, false);
    }

    /**
     * Создаёт целочисленный тип размером 32
     * @param isUnsigned флаг, является ли тип беззнаковым.
     */
    public IntType(boolean isUnsigned) {
        this(32, isUnsigned);
    }

    /**
     * Создаёт знаковый целочисленный тип
     * @param size размер типа.
     */
    public IntType(int size) {
        this(size, false);
    }

    /**
     * Создаёт целочисленный тип
     * @param isUnsigned флаг, является ли тип беззнаковым.
     * @param size размер типа.
     */
    public IntType(int size, boolean isUnsigned) {
        super(size);
        this.isUnsigned = isUnsigned;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        IntType intType = (IntType) o;
        return isUnsigned == intType.isUnsigned && size == intType.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), isUnsigned, size);
    }
}
