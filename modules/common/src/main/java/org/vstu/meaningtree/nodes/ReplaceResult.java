package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.iterators.utils.FieldDescriptor;

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
