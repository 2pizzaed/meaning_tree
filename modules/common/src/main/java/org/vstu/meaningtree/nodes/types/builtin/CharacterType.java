package org.vstu.meaningtree.nodes.types.builtin;

import org.vstu.meaningtree.nodes.Type;

import java.util.Objects;

public class CharacterType extends Type {
    public final int size;

    /**
     * Создаёт тип символ размером 8 бит
     */
    public CharacterType() {
        size = 8;
    }

    /**
     * Создаёт тип символ
     * @param bits количество бит
     * */
    public CharacterType(int bits) {
        size = bits;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
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
