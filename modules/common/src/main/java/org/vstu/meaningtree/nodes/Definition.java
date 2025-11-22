package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.iterators.utils.TreeNode;

public abstract class Definition extends Node {
    @TreeNode private Declaration declaration;

    protected Definition(Declaration decl) {
        declaration = decl;
    }

    public Declaration getDeclaration() {
        return declaration;
    }

    public Definition clone() {
        return (Definition) super.clone();
    }
}
