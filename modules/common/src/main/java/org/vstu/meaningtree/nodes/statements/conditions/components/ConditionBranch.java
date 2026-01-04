package org.vstu.meaningtree.nodes.statements.conditions.components;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.util.Objects;

public class ConditionBranch extends Statement implements HasBodyStatement {
    @TreeNode protected Expression condition;
    @TreeNode protected Statement body;

    public ConditionBranch(Expression condition, Statement body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ConditionBranch nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(condition, nodeInfos.condition) && Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), condition, body);
    }

    public ConditionBranch clone() {
        ConditionBranch clone = new ConditionBranch(condition, body);
        clone.body = body.clone();
        clone.condition = condition.clone();
        return clone;
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

}
