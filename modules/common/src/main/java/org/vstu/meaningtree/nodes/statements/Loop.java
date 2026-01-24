package org.vstu.meaningtree.nodes.statements;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.loops.LoopType;

public abstract class Loop extends Statement implements HasBodyStatement {
    protected LoopType _originalType;
    @TreeNode protected JumpLabel loopName;

    public Loop setJumpLabel(JumpLabel loopName) {
        this.loopName = loopName;
        return this;
    }

    @Nullable
    public JumpLabel getJumpLabel() {
        return loopName;
    }

    public LoopType getLoopType() {
        return _originalType;
    }
}
