package org.vstu.meaningtree.nodes.expressions.identifiers;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScopedIdentifier extends Identifier {
    @TreeNode private List<SimpleIdentifier> scopeResolutionList;

    public ScopedIdentifier(SimpleIdentifier... identifiers) {
        scopeResolutionList = List.of(identifiers);
    }

    public ScopedIdentifier(List<SimpleIdentifier> identifiers) {
        scopeResolutionList = List.copyOf(identifiers);
    }

    public List<SimpleIdentifier> getScopeResolution() {
        return scopeResolutionList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScopedIdentifier that = (ScopedIdentifier) o;
        return Objects.equals(scopeResolutionList, that.scopeResolutionList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), scopeResolutionList);
    }

    @Override
    public ScopedIdentifier clone() {
        ScopedIdentifier obj = (ScopedIdentifier) super.clone();
        obj.scopeResolutionList = new ArrayList<>(scopeResolutionList.stream().map(SimpleIdentifier::clone).toList());
        return obj;
    }

    @Override
    public boolean contains(Identifier other) {
        return scopeResolutionList.contains(other);
    }

    @Override
    public int contentSize() {
        return scopeResolutionList.size();
    }

    @Override
    public String internalRepresentation() {
        return String.join(".", getScopeResolution().stream()
                .map(SimpleIdentifier::getName).toArray(String[]::new));
    }

    public MemberAccess toMemberAccess() {
        if (scopeResolutionList.size() == 2) {
            return new MemberAccess(scopeResolutionList.getFirst(), scopeResolutionList.getLast());
        }
        return new MemberAccess(
                new ScopedIdentifier(scopeResolutionList.subList(0, scopeResolutionList.size() - 1))
                        .toMemberAccess(), scopeResolutionList.getLast());
    }
}
