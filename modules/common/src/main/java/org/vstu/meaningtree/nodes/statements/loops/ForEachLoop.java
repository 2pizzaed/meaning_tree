package org.vstu.meaningtree.nodes.statements.loops;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.env.SymbolEnvironment;

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
    public CompoundStatement makeCompoundBody(SymbolEnvironment env) {
        if (!(body instanceof CompoundStatement)) {
            body = new CompoundStatement(new SymbolEnvironment(env), getBody());
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

