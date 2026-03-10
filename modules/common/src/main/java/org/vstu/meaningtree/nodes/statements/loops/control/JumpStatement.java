package org.vstu.meaningtree.nodes.statements.loops.control;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;

public abstract class JumpStatement extends Statement {
    @TreeNode
    private JumpLabel jumpDestination;

    @Nullable
    public JumpLabel getJumpDestination() {
        return jumpDestination;
    }

    public JumpStatement(JumpLabel jumpDestination) {
        super();
        this.jumpDestination = jumpDestination;
    }

    public JumpStatement() {
        super();
        this.jumpDestination = null;
    }

    public @NotNull GotoStatement toGoto() {
        return new GotoStatement(jumpDestination);
    }
}
