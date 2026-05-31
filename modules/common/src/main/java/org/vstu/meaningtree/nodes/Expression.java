package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

abstract public class Expression extends Node {
    @Override
    public Expression clone() {
        return (Expression) super.clone();
    }

    public boolean equalsIdentifier(String name) {
        return equals(new SimpleIdentifier(name));
    }

    public boolean canBeEvaluatedToBoolean() {
        return false;
    }

    public Optional<Boolean> tryEvaluateAsBoolean(ScopeTable scopeTable) {
        return Optional.empty();
    }

    public Expression tryInvert() {
        if (canBeEvaluatedToBoolean()) {
            return new NotOp(this);
        }
        return this;
    }
}
