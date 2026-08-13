package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;

/**
 * Тип-указатель в любом объявлении: переменной, аргумента функции или возвращаемого значения.
 * Языки без указателей не могут выразить его семантику, поэтому конвертация должна падать.
 */
public class PointerTypeFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-pointer-type";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return node instanceof PointerType;
    }

    @Override
    public String description(Node node) {
        return "Pointer type is not supported";
    }
}
