package org.vstu.meaningtree.nodes.interfaces;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;

public interface NestedDeclaration<P extends Declaration> {
    @Nullable P getParentDeclaration();
    void setParentDeclaration(P declaration);

    default boolean foundParentDeclaration() {
        return getParentDeclaration() != null;
    }
}
