package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;

import java.util.Optional;

public class PointerSubtractionInUnpackFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-pointer-subtraction-in-unpack";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return node instanceof PointerUnpackOp op && op.getArgument() instanceof SubOp;
    }

    @Override
    public String description(Node node) {
        return "Subtraction of pointers cannot be converted to indexing";
    }
}
