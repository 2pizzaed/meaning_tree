package org.vstu.meaningtree.nodes.statements;

import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.loops.LoopIterationEstimate;
import org.vstu.meaningtree.nodes.statements.loops.LoopType;

import java.util.Optional;

public abstract class Loop extends Statement implements HasBodyStatement {
    protected LoopType _originalType;
    protected LoopIterationEstimate iterationEstimate;

    public LoopType getLoopType() {
        return _originalType;
    }

    public Optional<LoopIterationEstimate> getIterationEstimate() {
        return Optional.ofNullable(iterationEstimate);
    }

    public void setIterationEstimate(LoopIterationEstimate iterationEstimate) {
        this.iterationEstimate = iterationEstimate;
    }
}
