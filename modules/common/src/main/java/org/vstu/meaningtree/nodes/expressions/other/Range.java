package org.vstu.meaningtree.nodes.expressions.other;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryMinusOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryPlusOp;
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

        return literalValue(start, "Start value cannot be interpreted as long");
    }

    public long getStopValueAsLong() throws IllegalStateException {
        if (stop == null) {
            throw new IllegalStateException("Stop value is not specified");
        }

        return literalValue(stop, "Stop value cannot be interpreted as long");
    }

    public long getStepValueAsLong() throws IllegalStateException {
        if (step == null) {
            throw new IllegalStateException("Step value is not specified");
        }

        return literalValue(step, "Step value cannot be interpreted as long");
    }

    /**
     * Значение целочисленной константы, в том числе со знаком: {@code -3} в дереве — это
     * {@link UnaryMinusOp} над литералом, а не отрицательный литерал, и парсеры C++ и Java
     * строят шаг {@code i -= 3} именно так. Шаг диапазона хранится со знаком: у убывающего
     * диапазона он отрицателен.
     */
    private static long literalValue(Expression expression, String failureMessage) throws IllegalStateException {
        if (expression instanceof ParenthesizedExpression parenthesized) {
            return literalValue(parenthesized.getExpression(), failureMessage);
        }
        if (expression instanceof UnaryMinusOp minus) {
            return Math.negateExact(literalValue(minus.getArgument(), failureMessage));
        }
        if (expression instanceof UnaryPlusOp plus) {
            return literalValue(plus.getArgument(), failureMessage);
        }
        if (expression instanceof IntegerLiteral literal) {
            return literal.getLongValue();
        }
        throw new IllegalStateException(failureMessage);
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
