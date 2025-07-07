package org.vstu.meaningtree.nodes.io;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;

import java.util.List;
import java.util.Objects;

public class FormatInput extends InputCommand {
    @TreeNode private Expression formatString;

    public FormatInput(Expression formatString, Expression... values) {
        super(List.of(values));
        this.formatString = formatString;
    }

    public FormatInput(Expression formatString, List<Expression> values) {
        super(List.copyOf(values));
        this.formatString = formatString;
    }

    public Expression getFormatString() {
        return formatString;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FormatInput that = (FormatInput) o;
        return Objects.equals(formatString, that.formatString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), formatString);
    }
}

