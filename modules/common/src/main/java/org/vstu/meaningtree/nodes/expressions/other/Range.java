package org.vstu.meaningtree.nodes.expressions.other;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;

import java.util.Objects;

public class Range extends Expression {
    @TreeNode @Nullable private Expression start;
    @TreeNode @Nullable private Expression stop;
    @TreeNode @Nullable private Expression step;

    private boolean isExcludingStart;
    private boolean isExcludingEnd;

    private Range.Type rangeType;

    public enum Type {
        UP,
        DOWN,
        UNKNOWN,
    }

    public Range(@Nullable Expression start,
                 @Nullable Expression stop,
                 @Nullable Expression step,
                 boolean isExcludingStart,
                 boolean isExcludingEnd,
                 Range.Type rangeType
    ) {
        this.start = start;
        this.stop = stop;
        this.step = step;
        this.isExcludingStart = isExcludingStart;
        this.isExcludingEnd = isExcludingEnd;
        this.rangeType = rangeType;
    }

    public Range(Expression start, Expression stop) {
        this(start, stop, null, false, true, Type.UNKNOWN);
    }

    public static Range fromStart(Expression start) {
        return new Range(start, null);
    }

    public static Range untilStop(Expression stop) {
        return new Range(null, stop);
    }

    @Nullable
    public Expression getStart() {
        return start;
    }

    @Nullable
    public Expression getStop() {
        return stop;
    }

    @Nullable
    public Expression getStep() {
        return step;
    }

    public boolean isExcludingStart() {
        return isExcludingStart;
    }

    public boolean isExcludingEnd() {
        return isExcludingEnd;
    }

    public Type getType() {
        if (rangeType != Type.UNKNOWN) {
            return rangeType;
        }

        try {
            long start = getStartValueAsLong();
            long stop = getStopValueAsLong();

            if (start < stop) {
                rangeType = Type.UP;
            }
            else if (start > stop) {
                rangeType = Type.DOWN;
            }
            else {
                rangeType = Type.UNKNOWN;
            }
        }
        catch (IllegalStateException exception) {
            rangeType = Type.UNKNOWN;
        }

        return rangeType;
    }

    public long getStartValueAsLong() throws IllegalStateException {
        if (start == null) {
            throw new IllegalStateException("Start value is not specified");
        }

        if (!(start instanceof IntegerLiteral)) {
            throw new IllegalStateException("Start value cannot be interpreted as long");
        }

        return ((IntegerLiteral) start).getLongValue();
    }

    public long getStopValueAsLong() throws IllegalStateException {
        if (stop == null) {
            throw new IllegalStateException("Stop value is not specified");
        }

        if (!(stop instanceof IntegerLiteral)) {
            throw new IllegalStateException("Stop value cannot be interpreted as long");
        }

        return ((IntegerLiteral) stop).getLongValue();
    }

    public long getStepValueAsLong() throws IllegalStateException {
        if (step == null) {
            throw new IllegalStateException("Step value is not specified");
        }

        if (!(step instanceof IntegerLiteral)) {
            throw new IllegalStateException("Step value cannot be interpreted as long");
        }

        return ((IntegerLiteral) step).getLongValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Range range = (Range) o;
        return Objects.equals(start, range.start) && Objects.equals(stop, range.stop) && Objects.equals(step, range.step);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), start, stop, step);
    }

    @Override
    public Range clone() {
        Range obj = (Range) super.clone();
        if (start != null) obj.start = start.clone();
        if (stop != null) obj.stop = stop.clone();
        if (step != null) obj.step = step.clone();
        return obj;
    }
}
