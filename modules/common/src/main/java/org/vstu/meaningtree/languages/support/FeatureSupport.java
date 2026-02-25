package org.vstu.meaningtree.languages.support;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Node;

public interface FeatureSupport {
    String id();
    boolean matches(Node node, @Nullable FeatureContext context);
    String description(Node node);

    default boolean matches(Node node) {
        return matches(node, null);
    }
}
