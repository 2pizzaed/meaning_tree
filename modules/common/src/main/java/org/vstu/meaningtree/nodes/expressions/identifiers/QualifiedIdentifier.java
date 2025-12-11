package org.vstu.meaningtree.nodes.expressions.identifiers;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.Objects;

public class QualifiedIdentifier extends Identifier {
    @TreeNode private Identifier scope;
    @TreeNode private SimpleIdentifier member;

    public QualifiedIdentifier(Identifier scope, SimpleIdentifier member) {
        this.scope = scope;
        this.member = member;
    }

    public Identifier getScope() {
        return scope;
    }

    public SimpleIdentifier getMember() {
        return member;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QualifiedIdentifier that = (QualifiedIdentifier) o;
        return Objects.equals(scope, that.scope) && Objects.equals(member, that.member);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scope, member);
    }

    @Override
    public QualifiedIdentifier clone() {
        QualifiedIdentifier obj = (QualifiedIdentifier) super.clone();
        obj.scope = scope.clone();
        obj.member = member.clone();
        return obj;
    }

    @Override
    public boolean contains(Identifier other) {
        return scope.contains(other) || member.equals(other);
    }

    @Override
    public int contentSize() {
        return scope.contentSize() + 1;
    }

    @Override
    public String internalRepresentation() {
        return String.format("%s::%s",
                scope.internalRepresentation(), getMember().getName());
    }
}
