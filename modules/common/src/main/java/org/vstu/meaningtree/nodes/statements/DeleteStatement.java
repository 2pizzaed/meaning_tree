package org.vstu.meaningtree.nodes.statements;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.other.DeleteExpression;

import java.util.Objects;

public class DeleteStatement extends Statement {
    @TreeNode private Expression target;
    private boolean isCollection;

    public DeleteStatement(Expression target, boolean isCollection) {
        this.target = target;
        this.isCollection = isCollection;
    }

    public DeleteStatement(Expression target) {
        this(target, false);
    }

    public Expression getTarget() {
        return target;
    }

    public DeleteExpression toExpression() {
        return new DeleteExpression(target, isCollection);
    }

    public boolean isCollectionTarget() {
        return isCollection;
    }

    public DeleteStatement clone() {
        var clone = (DeleteStatement) super.clone();
        clone.target = target.clone();
        clone.isCollection = isCollection;
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DeleteStatement nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return isCollection == nodeInfos.isCollection && Objects.equals(target, nodeInfos.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), target, isCollection);
    }
}
