package org.vstu.meaningtree.nodes.statements.conditions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.DefaultCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.FallthroughCaseBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class SwitchStatement extends Statement {
    @TreeNode private Expression targetExpression;
    @TreeNode private List<CaseBlock> cases;
    @TreeNode private DefaultCaseBlock defaultCase;

    public SwitchStatement(@NotNull Expression targetExpression,
                           @NotNull List<CaseBlock> cases) {
        this.targetExpression = targetExpression;

        defaultCase = findDefaultCase(cases);

        this.cases = new ArrayList<>(cases);
        this.cases.remove(defaultCase);
    }

    public SwitchStatement(@NotNull Expression targetExpression,
                           @NotNull List<CaseBlock> cases,
                           @Nullable DefaultCaseBlock defaultCaseBlock) {
        this(targetExpression, Stream.concat(cases.stream(), Stream.of(defaultCaseBlock)).toList());
    }

    @Nullable
    private DefaultCaseBlock findDefaultCase(@NotNull List<CaseBlock> cases) {
        DefaultCaseBlock defaultCaseBlock = null;

        boolean found = false;
        for (CaseBlock caseBlock : cases) {
            if (found && caseBlock instanceof DefaultCaseBlock) {
                throw new IllegalArgumentException(this.getClass() + " cannot have several default branches");
            }

            if (caseBlock instanceof DefaultCaseBlock dcb) {
                defaultCaseBlock = dcb;
                found = true;
            }
        }

        return defaultCaseBlock;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    public Expression getTargetExpression() {
        return targetExpression;
    }

    @NotNull
    public List<CaseBlock> getCases() {
        return cases;
    }

    @Nullable
    public DefaultCaseBlock getDefaultCase() {
        return defaultCase;
    }

    public boolean hasDefaultCase() {
        return defaultCase != null;
    }

    public List<FallthroughCaseBlock> getFallthroughCaseBlocks() {
        return cases
                .stream()
                .filter(caseBlock -> caseBlock instanceof FallthroughCaseBlock)
                .map(caseBlock -> (FallthroughCaseBlock) caseBlock)
                .toList();
    }

    public void makeCompoundBranches() {
        for (CaseBlock branch : cases) {
            branch.makeCompoundBody();
        }
    }
}
