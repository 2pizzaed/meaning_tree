package org.vstu.meaningtree.nodes.types;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;

import java.util.List;
import java.util.Objects;

public class TupleType extends Type {
    @TreeNode protected List<? extends Type> tupleElementTypes;

    public List<? extends Type> getTupleElementTypes() {
        return tupleElementTypes;
    }

    public TupleType(List<? extends Type> tupleElementTypes) {
        this.tupleElementTypes = tupleElementTypes;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TupleType that = (TupleType) o;
        return Objects.equals(tupleElementTypes, that.tupleElementTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tupleElementTypes);
    }

    @Override
    public TupleType clone() {
        TupleType obj = (TupleType) super.clone();
        obj.tupleElementTypes = tupleElementTypes.stream().map(Type::clone).toList();
        return obj;
    }
}
