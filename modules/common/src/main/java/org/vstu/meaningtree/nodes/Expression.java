package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.Optional;

abstract public class Expression extends Node {
    private ExpressionValueEstimate<?> valueEstimate;

    @Override
    public Expression clone() {
        return (Expression) super.clone();
    }

    public boolean equalsIdentifier(String name) {
        return equals(new SimpleIdentifier(name));
    }

    public Optional<ExpressionValueEstimate<?>> getValueEstimate() {
        return Optional.ofNullable(valueEstimate);
    }

    public void setValueEstimate(ExpressionValueEstimate<?> valueEstimate) {
        this.valueEstimate = valueEstimate;
    }

    public Expression tryInvert() {
        return this;
    }
}
