package org.vstu.meaningtree.nodes.statements.loops;

import org.vstu.meaningtree.nodes.expressions.other.Range;

import java.io.Serializable;
import java.util.OptionalLong;

public record LoopIterationEstimate(
        LoopIterationCount kind,
        OptionalLong exactIterations,
        boolean reliable,
        Range.Direction direction
) implements Serializable {
    public static LoopIterationEstimate exact(long exactIterations) {
        return new LoopIterationEstimate(
                classifyExact(exactIterations),
                OptionalLong.of(exactIterations),
                true,
                Range.Direction.UNKNOWN
        );
    }

    public static LoopIterationEstimate fixed(long exactIterations, boolean reliable, Range.Direction direction) {
        return new LoopIterationEstimate(
                classifyExact(exactIterations),
                OptionalLong.of(exactIterations),
                reliable,
                direction
        );
    }

    public static LoopIterationEstimate unknown() {
        return new LoopIterationEstimate(
                LoopIterationCount.UNDEFINED,
                OptionalLong.empty(),
                false,
                Range.Direction.UNKNOWN
        );
    }

    public static LoopIterationEstimate ofKind(LoopIterationCount kind, boolean reliable) {
        return ofKind(kind, reliable, Range.Direction.UNKNOWN);
    }

    public static LoopIterationEstimate ofKind(LoopIterationCount kind,
                                               boolean reliable,
                                               Range.Direction direction) {
        return new LoopIterationEstimate(kind, OptionalLong.empty(), reliable, direction);
    }

    private static LoopIterationCount classifyExact(long exactIterations) {
        if (exactIterations <= 0) {
            return LoopIterationCount.ZERO;
        }
        if (exactIterations == 1) {
            return LoopIterationCount.ONE;
        }
        return LoopIterationCount.FIXED;
    }
}
