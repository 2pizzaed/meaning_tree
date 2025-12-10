package org.vstu.meaningtree.nodes.types.containers;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.Generic;
import org.vstu.meaningtree.nodes.interfaces.SequenceType;

import java.util.Objects;

public abstract class DictionaryType extends Type implements Generic, SequenceType {
    @TreeNode private Type keyType;
    @TreeNode private Type valueType;

    public DictionaryType(Type keyType, Type valueType) {
        this.keyType = keyType;
        this.valueType = valueType;
    }

    public Type getKeyType() {
        return keyType;
    }

    public Type getValueType() {
        return valueType;
    }

    @Override
    public Type[] getTypeParameters() {
        return new Type[] {keyType, valueType};
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DictionaryType that = (DictionaryType) o;
        return Objects.equals(keyType, that.keyType) && Objects.equals(valueType, that.valueType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keyType, valueType);
    }

    @Override
    public DictionaryType clone() {
        DictionaryType obj = (DictionaryType) super.clone();
        obj.keyType = keyType.clone();
        obj.valueType = valueType.clone();
        return obj;
    }
}
