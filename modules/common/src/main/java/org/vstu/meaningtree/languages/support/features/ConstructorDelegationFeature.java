package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;

public class ConstructorDelegationFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-constructor-delegation";
    }

    @Override
    public boolean matches(Node node, FeatureContext context) {
        return node instanceof ConstructorCall call && !call.isBaseClassCall();
    }

    @Override
    public String description(Node node) {
        return "Delegating to another constructor of the current class is unsupported";
    }
}
