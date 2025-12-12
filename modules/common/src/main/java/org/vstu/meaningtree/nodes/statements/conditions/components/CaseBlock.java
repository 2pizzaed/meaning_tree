package org.vstu.meaningtree.nodes.statements.conditions.components;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.util.Objects;

public abstract class CaseBlock extends Statement implements HasBodyStatement {
    @TreeNode private Statement body;

    public CaseBlock(Statement body) {
        this.body = body;
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
        if (!(o instanceof CaseBlock nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), body);
    }

    public CaseBlock clone() {
        CaseBlock clone = (CaseBlock) super.clone();
        clone.body = body.clone();
        return clone;
    }
}
