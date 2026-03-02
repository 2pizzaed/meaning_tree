package org.vstu.meaningtree.utils;

import org.vstu.meaningtree.iterators.utils.FieldDescriptor;
import org.vstu.meaningtree.nodes.Node;

public record ReplaceResult(
        ReplaceStatus status,
        String message,
        FieldDescriptor field,
        Node oldNode,
        Node newNode
) {
    public boolean isSuccess() {
        return status == ReplaceStatus.OK;
    }
}
