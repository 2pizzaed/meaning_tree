package org.vstu.meaningtree.nodes.statements.loops;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.util.Objects;

public class ForEachLoop extends ForLoop {
    @TreeNode private VariableDeclaration item;
    @TreeNode private Expression expr;
    @TreeNode private Statement body;

    public ForEachLoop(VariableDeclaration item, Expression expr, Statement body) {
        this.item = item;
        this.expr = expr;
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ForEachLoop nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(item, nodeInfos.item) && Objects.equals(expr, nodeInfos.expr) && Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), item, expr, body);
    }

    public ForEachLoop clone() {
        var clone = (ForEachLoop) super.clone();
        clone.item = item.clone();
        clone.expr = expr.clone();
        clone.body = body.clone();
        return clone;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        if (!(body instanceof CompoundStatement)) {
            body = new CompoundStatement(getBody());
        }
        return (CompoundStatement) body;
    }
    
    public Expression getExpression() {
        return expr;
    }

    public VariableDeclaration getItem() {
        return item;
    }

    public Statement getBody() {
        return body;
    }
}

