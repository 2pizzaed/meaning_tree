package org.vstu.meaningtree.nodes.statements.loops;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;

import java.util.Objects;

public class InfiniteLoop extends Loop {
    @TreeNode private Statement body;

    public InfiniteLoop(Statement body, LoopType originalType) {
        this.body = body;
        _originalType = originalType;
    }

    @Override
    public Statement getBody() {
        return body;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        if (!(body instanceof CompoundStatement)) {
            body = new CompoundStatement(getBody());
        }
        return (CompoundStatement) body;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InfiniteLoop nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), body);
    }

    public InfiniteLoop clone() {
        var clone = (InfiniteLoop) super.clone();
        clone.body = body.clone();
        return clone;
    }
}
