package org.vstu.meaningtree.nodes.expressions.pointers;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;

import java.util.Objects;

public class PointerToMemberAccess extends MemberAccess {
    private final boolean throughPointer;

    public PointerToMemberAccess(Expression expr, SimpleIdentifier member, boolean throughPointer) {
        super(expr, member);
        this.throughPointer = throughPointer;
    }

    public boolean isThroughPointer() {
        return throughPointer;
    }

    public String getAccessOperator() {
        return throughPointer ? "->*" : ".*";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PointerToMemberAccess that)) return false;
        if (!super.equals(o)) return false;
        return throughPointer == that.throughPointer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), throughPointer);
    }

    @Override
    public PointerToMemberAccess clone() {
        return (PointerToMemberAccess) super.clone();
    }
}
