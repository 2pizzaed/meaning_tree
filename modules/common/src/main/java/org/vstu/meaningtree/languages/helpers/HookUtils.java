package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.nodes.Node;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

public class HookUtils {
    public record NodePreparationEntry<T extends Node>(Class<T> nodeType, UnaryOperator<T> preparation) {
        public NodePreparationEntry {
            Objects.requireNonNull(nodeType, "nodeType must not be null");
            Objects.requireNonNull(preparation, "preparation must not be null");
        }

        public boolean matches(Node node) {
            return nodeType.isAssignableFrom(node.getClass());
        }

        public Node apply(Node node) {
            return preparation.apply(nodeType.cast(node));
        }
    }

    public record PostRenderPreparationEntry<T extends Node>(Class<T> nodeType, BiFunction<T, String, String> preparation) {
        public PostRenderPreparationEntry {
            Objects.requireNonNull(nodeType, "nodeType must not be null");
            Objects.requireNonNull(preparation, "preparation must not be null");
        }

        public boolean matches(Node node) {
            return nodeType.isAssignableFrom(node.getClass());
        }

        public String apply(Node node, String rendered) {
            return preparation.apply(nodeType.cast(node), rendered);
        }
    }

}
