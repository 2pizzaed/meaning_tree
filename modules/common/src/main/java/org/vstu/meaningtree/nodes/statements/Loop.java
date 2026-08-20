package org.vstu.meaningtree.nodes.statements;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.loops.LoopIterationEstimate;
import org.vstu.meaningtree.nodes.statements.loops.LoopType;

import java.util.Objects;
import java.util.Optional;

public abstract class Loop extends Statement implements HasBodyStatement {
    protected LoopType _originalType;
    protected LoopIterationEstimate iterationEstimate;

    @TreeNode
    @Nullable
    private Statement elseBranch;

    public LoopType getLoopType() {
        return _originalType;
    }

    public Optional<LoopIterationEstimate> getIterationEstimate() {
        return Optional.ofNullable(iterationEstimate);
    }

    public void setIterationEstimate(LoopIterationEstimate iterationEstimate) {
        this.iterationEstimate = iterationEstimate;
    }

    /**
     * Python-style loop else-clause: executed when the loop completes without hitting a break
     * belonging to it. Only Python's for/while parse into this; other languages have no syntax
     * for it and desugar it via LoopElseLowerer when generating code.
     */
    public boolean hasElseBranch() {
        return elseBranch != null;
    }

    public Statement getElseBranch() {
        return Objects.requireNonNull(elseBranch, "Loop does not have an else branch");
    }

    public void setElseBranch(@Nullable Statement elseBranch) {
        this.elseBranch = elseBranch;
    }

    public CompoundStatement makeCompoundElseBody() {
        if (elseBranch != null && !(elseBranch instanceof CompoundStatement)) {
            elseBranch = new CompoundStatement(elseBranch);
        }
        return elseBranch != null ? (CompoundStatement) elseBranch : null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Loop loop)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(elseBranch, loop.elseBranch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), elseBranch);
    }

    @Override
    public Loop clone() {
        Loop clone = (Loop) super.clone();
        clone.elseBranch = elseBranch == null ? null : elseBranch.clone();
        return clone;
    }
}
