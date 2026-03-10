package org.vstu.meaningtree.nodes.statements.loops.control;

import org.vstu.meaningtree.nodes.expressions.identifiers.JumpLabel;

public class BreakStatement extends JumpStatement {
    public BreakStatement(JumpLabel jumpDestination) {
        super(jumpDestination);
    }

    public BreakStatement() {
        super();
    }
}
