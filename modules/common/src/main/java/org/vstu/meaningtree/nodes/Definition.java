package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.iterators.utils.TreeNode;

public abstract class Definition extends Node {
    @TreeNode private Declaration _decl;

    protected Definition(Declaration decl) {
        _decl = decl;
    }

    public Declaration getDeclaration() {
        return _decl;
    }

    public Definition clone() {
        return (Definition) super.clone();
    }
}
