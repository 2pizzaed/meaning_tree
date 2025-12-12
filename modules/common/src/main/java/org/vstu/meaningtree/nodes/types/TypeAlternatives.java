package org.vstu.meaningtree.nodes.types;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class TypeAlternatives extends Type {
    @TreeNode
    private List<? extends Type> alternatives;

    public TypeAlternatives(List<? extends Type> alternatives) {
        this.alternatives = alternatives;
    }

    public TypeAlternatives(Type ... alternatives) {
        this.alternatives = Arrays.asList(alternatives);
    }

    public List<? extends Type> get() {
        return alternatives;
    }

    public boolean has(Type t) {
        return alternatives.contains(t);
    }

    @Override
    public TypeAlternatives clone() {
        TypeAlternatives obj = (TypeAlternatives) super.clone();
        obj.alternatives = alternatives.stream().map(Type::clone).toList();
        return obj;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TypeAlternatives that = (TypeAlternatives) o;
        return Objects.equals(alternatives, that.alternatives);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), alternatives);
    }
}
