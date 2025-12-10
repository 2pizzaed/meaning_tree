package org.vstu.meaningtree.nodes.types.containers;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.Generic;
import org.vstu.meaningtree.nodes.interfaces.SequenceType;

import java.util.Objects;

public class PlainCollectionType extends Type implements Generic, SequenceType {
    @TreeNode private Type itemType;

    public PlainCollectionType(Type itemType) {
        this.itemType = itemType;
    }

    @Override
    public Type[] getTypeParameters() {
        return new Type[] {itemType};
    }

    public Type getItemType() {
        return itemType;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlainCollectionType that = (PlainCollectionType) o;
        return Objects.equals(itemType, that.itemType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), itemType);
    }

    @Override
    public PlainCollectionType clone() {
        PlainCollectionType obj = (PlainCollectionType) super.clone();
        obj.itemType = itemType.clone();
        return obj;
    }
}
