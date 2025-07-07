package org.vstu.meaningtree.nodes.statements.loops;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

public class GeneralForLoop extends ForLoop {
    @TreeNode @Nullable private HasInitialization initializer;

    @TreeNode @Nullable private Expression condition;

    @TreeNode @Nullable private final Expression update;
    @TreeNode private Statement body;

    public GeneralForLoop(@Nullable HasInitialization initializer, @Nullable Expression condition,
                          @Nullable Expression update, Statement body) {
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

    public boolean hasInitializer() {
        return initializer != null;
    }

    public HasInitialization getInitializer() {
        if (!hasInitializer()) {
            throw new MeaningTreeException("No initializer");
        }

        return initializer;
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
    public String generateDot() {
        throw new UnsupportedOperationException();
    }
}
