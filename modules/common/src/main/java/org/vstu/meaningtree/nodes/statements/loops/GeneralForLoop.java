package org.vstu.meaningtree.nodes.statements.loops;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.util.Objects;

public class GeneralForLoop extends ForLoop {
    @TreeNode @Nullable private Node initializer;

    @TreeNode @Nullable private Expression condition;

    @TreeNode @Nullable private final Expression update;
    @TreeNode private Statement body;

    public GeneralForLoop(@Nullable Node initializer, @Nullable Expression condition,
                          @Nullable Expression update, Statement body) {
        if (!(initializer instanceof Expression || initializer instanceof HasInitialization)) {
            throw new MeaningTreeException("GeneralForLoop initializer requires an expression or HasInitialization");
        }
        this.initializer = initializer;
        this.condition = condition;
        this.update = update;
        this.body = body;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        if (!(body instanceof CompoundStatement)) {
            body = new CompoundStatement(getBody());
        }
        return (CompoundStatement) body;
    }

    public boolean hasInitialization() {
        return initializer != null && initializer instanceof HasInitialization;
    }

    public boolean hasInitializerAsExpression() {
        return initializer != null && initializer instanceof Expression;
    }

    public boolean hasInitializer() {
        return initializer != null;
    }

    public Node getInitializer() {
        return initializer;
    }

    public HasInitialization getInitialization() {
        if (!hasInitialization()) {
            throw new MeaningTreeException("No initializer");
        }

        return (HasInitialization) initializer;
    }

    public Expression getInitializerAsExpression() {
        if (!hasInitializerAsExpression()) {
            throw new MeaningTreeException("No expression in initializer place");
        }
        return (Expression) initializer;
    }

    public boolean hasCondition() {
        return condition != null;
    }

    public Expression getCondition() {
        if (!hasCondition()) {
            throw new MeaningTreeException("No condition");
        }

        return condition;
    }

    public boolean hasUpdate() {
        return update != null;
    }

    public Expression getUpdate() {
        if (!hasUpdate()) {
            throw new MeaningTreeException("No update");
        }

        return update;
    }

    public Statement getBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GeneralForLoop nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(initializer, nodeInfos.initializer) && Objects.equals(condition, nodeInfos.condition) && Objects.equals(update, nodeInfos.update) && Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), initializer, condition, update, body);
    }

    public GeneralForLoop clone() {
        var clone = (GeneralForLoop) super.clone();
        clone.initializer = initializer.clone();
        clone.condition = condition.clone();
        return clone;
    }

}
