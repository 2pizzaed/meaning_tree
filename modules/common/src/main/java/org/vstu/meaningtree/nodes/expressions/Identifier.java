package org.vstu.meaningtree.nodes.expressions;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

public abstract class Identifier extends Expression {
    @Override
    public Identifier clone() {
        return (Identifier) super.clone();
    }

    public abstract boolean contains(Identifier other);
    public abstract int contentSize();

    public SimpleIdentifier getSimpleIdentifierOrThrow() {
        if (this instanceof SimpleIdentifier) {
            return (SimpleIdentifier) this;
        }
        throw new AssertionError("This identifier isn't simple identifier");
    }

    public abstract String internalRepresentation();
}
