package org.vstu.meaningtree.nodes.statements.loops;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;

import java.util.Objects;

public class DoWhileLoop extends Loop {
    @TreeNode protected Expression condition;
    @TreeNode protected Statement body;

    public DoWhileLoop(Expression condition, Statement body) {
        this.condition = condition;
        this.body = body;
    }

    public Expression getCondition() {
        return condition;
    }

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
        if (!(o instanceof DoWhileLoop nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(condition, nodeInfos.condition) && Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), condition, body);
    }

    public DoWhileLoop clone() {
        var clone = (DoWhileLoop) super.clone();
        clone.condition = condition.clone();
        clone.body = body.clone();
        return clone;
    }

}
