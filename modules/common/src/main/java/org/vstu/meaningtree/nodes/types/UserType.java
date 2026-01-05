package org.vstu.meaningtree.nodes.types;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.Objects;

public abstract class UserType extends Type {
    @TreeNode private Identifier name;

    public SimpleIdentifier getName() {
        if (getQualifiedName() instanceof QualifiedIdentifier qualified) {
            return qualified.getMember();
        } else if (getQualifiedName() instanceof ScopedIdentifier scoped) {
            return scoped.getScopeResolution().getLast();
        }
        return (SimpleIdentifier) name;
    }

    // Например, для вывода имени вместе с namespace. Может быть QualifiedIdentifier в случае C++, либо ScopedIdentifier в случае Java и Python
    public Identifier getQualifiedName() {
        return name;
    }

    protected UserType(Identifier name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        UserType other = (UserType) o;
        return getName().equals(other.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name);
    }

    @Override
    public UserType clone() {
        UserType obj = (UserType) super.clone();
        obj.name = name.clone();
        return obj;
    }

    @Override
    public String internalRepresentation() {
        return name.internalRepresentation();
    }
}
