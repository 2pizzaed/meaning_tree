package org.vstu.meaningtree.nodes.statements.conditions.components;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ветка {@code case} со значениями. Значений бывает несколько: ветка выбирается, если
 * выражение switch равно любому из них ({@code case 2, 3:} в Java, {@code case 2 | 3:} в Python).
 */
public abstract class MatchValueCaseBlock extends CaseBlock {
    @TreeNode private List<Expression> matchValues;

    public MatchValueCaseBlock(Expression matchValue, Statement body) {
        this(List.of(matchValue), body);
    }

    public MatchValueCaseBlock(List<Expression> matchValues, Statement body) {
        super(body);
        if (matchValues.isEmpty()) {
            throw new IllegalArgumentException(getClass() + " must have at least one match value");
        }
        this.matchValues = new ArrayList<>(matchValues);
    }

    public List<Expression> getMatchValues() {
        return matchValues;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MatchValueCaseBlock nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(matchValues, nodeInfos.matchValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), matchValues);
    }

    public MatchValueCaseBlock clone() {
        MatchValueCaseBlock clone = (MatchValueCaseBlock) super.clone();
        clone.matchValues = new ArrayList<>(matchValues.stream().map(Expression::clone).toList());
        return clone;
    }
}
