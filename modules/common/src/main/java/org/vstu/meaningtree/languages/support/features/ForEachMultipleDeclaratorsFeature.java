package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.loops.ForEachLoop;

import java.util.Optional;

public class ForEachMultipleDeclaratorsFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-foreach-multiple-declarators";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return node instanceof ForEachLoop loop && loop.getItem().getDeclarators().length > 1;
    }

    @Override
    public String description(Node node) {
        return "For-each with multiple declarators is not supported by this viewer";
    }
}
