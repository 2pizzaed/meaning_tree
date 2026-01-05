package org.vstu.meaningtree.nodes.statements.loops;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.Range;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.util.Objects;

/**
 * Цикл по диапазону целых чисел (начало и конец являются частью диапазона) с заданным шагом.
 */
public class RangeForLoop extends ForLoop {
    @TreeNode private Range range;
    @TreeNode private SimpleIdentifier identifier;
    @TreeNode private Statement body;

    /**
     * Создает цикл по диапазону.
     * @param range - выражение диапазона
     * @param identifier - идентификатор диапазона
     * @param body тело цикла
     */
    public RangeForLoop(Range range, SimpleIdentifier identifier, Statement body) {
        this.range = range;
        this.identifier = identifier;
        this.body = body;
    }

    /**
     * Создает цикл по диапазону.
     * @param start начало диапазона (включительно)
     * @param end конец диапазона (не включительно)
     * @param step _identifier
     * @param body тело цикла
     */
    public RangeForLoop(Expression start,
                        Expression end,
                        Expression step,
                        boolean isExcludingStart,
                        boolean isExcludingEnd,
                        SimpleIdentifier identifier,
                        Statement body) {
        this(new Range(start, end, step, isExcludingStart, isExcludingEnd, Range.Type.UNKNOWN), identifier, body);
    }

    public Range getRange() {
        return range;
    }

    public SimpleIdentifier getIdentifier() {
        return identifier;
    }

    public Statement getBody() { return body; }

    public Range.Type getRangeType() {
        return range.getType();
    }

    public Expression getStart() {
        return range.getStart();
    }

    public Expression getStop() {
        return range.getStop();
    }

    public Expression getStep() {
        return range.getStep();
    }

    public long getStartValueAsLong() throws IllegalStateException {
        return range.getStartValueAsLong();
    }

    public long getStopValueAsLong() throws IllegalStateException {
        return range.getStopValueAsLong();
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        if (!(body instanceof CompoundStatement)) {
            body = new CompoundStatement(getBody());
        }
        return (CompoundStatement) body;
    }

    public long getStepValueAsLong() throws IllegalStateException {
        return range.getStepValueAsLong();
    }

    public boolean isExcludingStop() {
        return range.isExcludingEnd();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RangeForLoop nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(range, nodeInfos.range) && Objects.equals(identifier, nodeInfos.identifier) && Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), range, identifier, body);
    }

    public RangeForLoop clone() {
        var clone = (RangeForLoop) super.clone();
        clone.body = body.clone();
        clone.identifier = identifier.clone();
        clone.range = range.clone();
        return clone;
    }
}
