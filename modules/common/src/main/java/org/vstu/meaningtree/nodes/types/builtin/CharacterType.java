package org.vstu.meaningtree.nodes.types.builtin;

import java.util.Objects;

public class CharacterType extends NumericType {
    /**
     * Создаёт тип символ размером 8 бит
     */
    public CharacterType() {
        super(8);
    }

    /**
     * Создаёт тип символ
     * @param bits количество бит
     * */
    public CharacterType(int bits) {
        super(bits);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CharacterType that = (CharacterType) o;
        return size == that.size;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), size);
    }
}
