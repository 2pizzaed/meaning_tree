package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.statements.loops.ForEachLoop;
import org.vstu.meaningtree.nodes.types.UnknownType;

import java.util.Arrays;

public class UninferableVariableTypeFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-uninferable-variable-type";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        if (!(node instanceof VariableDeclaration declaration)
                || !(declaration.getType() instanceof UnknownType)
                || Arrays.stream(declaration.getDeclarators()).noneMatch(declarator -> !declarator.hasInitialization())) {
            return false;
        }

        return featureContext == null
                || !(featureContext.nodeInfo().parentNode() instanceof ForEachLoop loop
                && loop.getItem() == declaration);
    }

    @Override
    public String description(Node node) {
        return "C++ requires a type or an initializer for every declared variable";
    }
}
