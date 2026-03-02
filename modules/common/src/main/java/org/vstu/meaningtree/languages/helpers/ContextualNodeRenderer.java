package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.nodes.Node;

@FunctionalInterface
public interface ContextualNodeRenderer<T extends Node, C> {
    String render(T node, C context);
}
