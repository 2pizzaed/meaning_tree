package org.vstu.meaningtree.nodes;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;

abstract public class Statement extends Node {
    @TreeNode
    protected JumpLabel jumpLabel;

    public Statement clone() {
        return (Statement) super.clone();
    }

    public Statement setJumpLabel(JumpLabel loopName) {
        this.jumpLabel = loopName;
        return this;
    }

    @Nullable
    public JumpLabel getJumpLabel() {
        return jumpLabel;
    }
}
