package org.vstu.meaningtree.iterators.utils;

import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Experimental;


public record NodeInfo(Node node, NodeInfo parent, FieldDescriptor field, int depth) {
    public long id() {
        return node.getId();
    }

    public Node parentNode() {
        return parent == null ? null : parent.node();
    }

    public boolean isInCollection() {
        return field instanceof ArrayFieldDescriptor || field instanceof CollectionFieldDescriptor;
    }

    @Experimental
    public String path() {
        if (parent() == null) {
            return "$";
        }
        if (field() == null) {
            return "$/" + node().getClass().getSimpleName() + "#" + node().getId();
        }
        StringBuilder builder = new StringBuilder("$/");
        builder.append(parent().node().getClass().getSimpleName())
                .append("#")
                .append(parent().node().getId())
                .append(".")
                .append(field().getName());
        if (field().isIndexed()) {
            builder.append("[").append(field().getIndex()).append("]");
        }
        return builder.toString();
    }
}
