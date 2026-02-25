package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.other.Range;
import org.vstu.meaningtree.nodes.statements.loops.RangeForLoop;

import java.util.Optional;

public class NonDirectionalRangeForFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-range-for-nondirectional";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return node instanceof RangeForLoop loop
                && loop.getRange().getType() != Range.Type.UP
                && loop.getRange().getType() != Range.Type.DOWN;
    }

    @Override
    public String description(Node node) {
        return "Can't determine range type in for loop";
    }
}
