package org.vstu.meaningtree.nodes.statements.loops;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;

public class WhileLoop extends Loop {
    @TreeNode protected Expression condition;
    @TreeNode protected Statement body;


    public Expression getCondition() {
        return condition;
    }

    public Statement getBody() {
        return body;
    }

    public WhileLoop(Expression condition, Statement body) {
        this.condition = condition;
        this.body = body;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        if (!(body instanceof CompoundStatement)) {
            body = new CompoundStatement(getBody());
        }
        return (CompoundStatement) body;
    }

    @Override
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
