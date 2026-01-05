package org.vstu.meaningtree.nodes.expressions.identifiers;

import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.Objects;

public class SimpleIdentifier extends Identifier {
    protected final String name;

    public SimpleIdentifier(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleIdentifier that = (SimpleIdentifier) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name);
    }

    @Override
    public SimpleIdentifier clone() {
        return (SimpleIdentifier) super.clone();
    }

    @Override
    public boolean contains(Identifier other) {
        return equals(other);
    }

    @Override
    public int contentSize() {
        return 1;
    }

    @Override
    public String internalRepresentation() {
        return name;
    }
}
