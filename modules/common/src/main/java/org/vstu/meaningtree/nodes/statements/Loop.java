package org.vstu.meaningtree.nodes.statements;

import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.loops.LoopType;

public abstract class Loop extends Statement implements HasBodyStatement {
    protected LoopType _originalType;

    public LoopType getLoopType() {
        return _originalType;
    }
}
