package org.vstu.meaningtree.nodes.expressions.other;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.statements.loops.LoopIterationEstimate;
import org.vstu.meaningtree.utils.InternalNode;

import java.util.Objects;
import java.util.Optional;

@InternalNode
public class Range extends Expression {
    @TreeNode @Nullable private Expression start;
    @TreeNode @Nullable private Expression stop;
    @TreeNode @Nullable private Expression step;

    private boolean isExcludingStart;
    private boolean isExcludingEnd;

    public enum Direction {
        UP,
        DOWN,
        UNKNOWN
    }

    private Direction rangeDirection;
    private LoopIterationEstimate iterationEstimate;

    public Range(@Nullable Expression start,
                 @Nullable Expression stop,
                 @Nullable Expression step,
                 boolean isExcludingStart,
                 boolean isExcludingEnd,
                 Direction rangeDirection
    ) {
        this.start = start;
        this.stop = stop;
        this.step = step;
        this.isExcludingStart = isExcludingStart;
        this.isExcludingEnd = isExcludingEnd;
        this.rangeDirection = rangeDirection;
    }

    public Range(Expression start, Expression stop) {
        this(start, stop, null, false, true, Direction.UNKNOWN);
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

    public Direction getDirection() {
        if (rangeDirection != Direction.UNKNOWN) {
            return rangeDirection;
        }

        try {
            long start = getStartValueAsLong();
            long stop = getStopValueAsLong();

            if (start < stop) {
                rangeDirection = Direction.UP;
            }
            else if (start > stop) {
                rangeDirection = Direction.DOWN;
            }
            else {
                rangeDirection = Direction.UNKNOWN;
            }
        }
        catch (IllegalStateException exception) {
            rangeDirection = Direction.UNKNOWN;
        }

        return rangeDirection;
    }

    public Direction getType() {
        return getDirection();
    }

    public void setDirection(Direction direction) {
        rangeDirection = direction == null ? Direction.UNKNOWN : direction;
    }

    public void setType(Direction direction) {
        setDirection(direction);
    }

    public Optional<LoopIterationEstimate> getIterationEstimate() {
        return Optional.ofNullable(iterationEstimate);
    }

    public void setIterationEstimate(LoopIterationEstimate iterationEstimate) {
        this.iterationEstimate = iterationEstimate;
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
