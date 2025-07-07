package org.vstu.meaningtree.nodes.statements.conditions.components;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

public class ConditionBranch extends Statement implements HasBodyStatement {
    @TreeNode protected Expression condition;
    @TreeNode protected Statement body;

    public ConditionBranch(Expression condition, Statement body) {
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

    public String generateDot() {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%s [label=\"%s\"];\n", _id, getClass().getSimpleName()));
        builder.append(condition.generateDot());
        builder.append(body.generateDot());
        builder.append(String.format("%s -- %s [label=\"%s\"];\n", _id, condition.getId(), "condition"));
        builder.append(String.format("%s -- %s [label=\"%s\"];\n", _id, body.getId(), "body"));

        return builder.toString();
    }
}
