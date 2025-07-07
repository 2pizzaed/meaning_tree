package org.vstu.meaningtree.nodes.statements.conditions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IfStatement extends Statement {
    @TreeNode private List<ConditionBranch> branches;

    @Nullable
    private Statement _elseBranch;

    public IfStatement(@NotNull Expression condition, @NotNull Statement thenBranch, @Nullable Statement elseBranch) {
        branches = new ArrayList<>();
        branches.add(new ConditionBranch(condition, thenBranch));
        _elseBranch = collectConditionBranches(branches, elseBranch);
    }

    private Statement collectConditionBranches(
            @NotNull List<ConditionBranch> branches,
            @Nullable Statement elseBranch
    ) {
        if (elseBranch == null) {
            return null;
        }

        Statement current = elseBranch;
        while (current instanceof IfStatement ifStatement) {
            branches.addAll(ifStatement.getBranches());

            if (ifStatement.hasElseBranch()) {
                current = ifStatement.getElseBranch();
            }
            else {
                return null;
            }
        }

        return current;
    }

    public IfStatement(List<ConditionBranch> branches, @Nullable Statement elseBranch) {
        _elseBranch = elseBranch;
        this.branches = new ArrayList<>(branches);
    }

    public IfStatement(Expression condition, Statement thenBranch) {
        branches = new ArrayList<>();
        branches.add(new ConditionBranch(condition, thenBranch));
        _elseBranch = null;
    }

    public List<ConditionBranch> getBranches() {
        return branches;
    }

    public Statement getElseBranch() {
        return Objects.requireNonNull(_elseBranch, "If statement does not have else branch");
    }

    public boolean hasElseBranch() {
        return _elseBranch != null;
    }

    @Override
    public String generateDot() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("%s [label=\"%s\"];\n", _id, getClass().getSimpleName()));

        for (ConditionBranch branch : branches) {
            builder.append(branch.generateDot());
            builder.append(String.format("%s -- %s;\n", _id, branch.getId()));
        }

        if (_elseBranch != null) {
            builder.append(_elseBranch.generateDot());
            builder.append(String.format("%s -- %s [label=\"%s\"];\n", _id, _elseBranch.getId(), "else"));
        }

        return builder.toString();
    }

    public void makeCompoundBranches() {
        for (ConditionBranch branch : branches) {
            branch.makeCompoundBody();
        }
    }
}
