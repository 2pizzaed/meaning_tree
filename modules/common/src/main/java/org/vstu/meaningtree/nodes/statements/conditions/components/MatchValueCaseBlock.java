package org.vstu.meaningtree.nodes.statements.conditions.components;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;

import java.util.Objects;

public abstract class MatchValueCaseBlock extends CaseBlock {
    @TreeNode private Expression matchValue;

    public MatchValueCaseBlock(Expression matchValue, Statement body) {
        super(body);
        this.matchValue = matchValue;
    }

    public Expression getMatchValue() {
        return matchValue;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MatchValueCaseBlock nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(matchValue, nodeInfos.matchValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), matchValue);
    }

    public MatchValueCaseBlock clone() {
        MatchValueCaseBlock clone = (MatchValueCaseBlock) super.clone();
        clone.matchValue = matchValue.clone();
        return clone;
    }
}
