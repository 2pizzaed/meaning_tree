package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerToMemberAccess;

public class PointerToMemberOperatorFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-pointer-to-member-operator";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return node instanceof PointerToMemberAccess;
    }

    @Override
    public String description(Node node) {
        PointerToMemberAccess access = (PointerToMemberAccess) node;
        return "Pointer-to-member operator %s is not supported".formatted(access.getAccessOperator());
    }
}
