package org.vstu.meaningtree.nodes.statements.loops.control;

import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;

public class GotoStatement extends JumpStatement {
    public GotoStatement(JumpLabel jumpDestination) {
        super(jumpDestination);
    }
}
