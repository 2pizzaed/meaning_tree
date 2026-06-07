package org.vstu.meaningtree.nodes;

import java.io.Serializable;
import java.util.*;

public record ExpressionValueEstimate<T>(
        Optional<T> exactValue,
        Set<T> possibleValues,
        boolean reliable
) implements Serializable {
    public ExpressionValueEstimate {
        exactValue = exactValue == null ? Optional.empty() : exactValue;
        possibleValues = Set.copyOf(possibleValues == null ? Set.of() : possibleValues);
        if (exactValue.isPresent() && !possibleValues.contains(exactValue.get())) {
            throw new IllegalArgumentException("Exact value must be present in possible values");
        }
    }

    public static <T> ExpressionValueEstimate<T> exact(T value) {
        return new ExpressionValueEstimate<>(Optional.ofNullable(value), Set.of(value), true);
    }

    public static <T> ExpressionValueEstimate<T> possible(Collection<T> values, boolean reliable) {
        return new ExpressionValueEstimate<>(Optional.empty(), new LinkedHashSet<>(values), reliable);
    }

    public static <T> ExpressionValueEstimate<T> unknown() {
        return new ExpressionValueEstimate<>(Optional.empty(), Set.of(), false);
    }

    public static <T> ExpressionValueEstimate<T> of(Optional<T> exactValue,
                                                    Collection<T> possibleValues,
                                                    boolean reliable) {
        return new ExpressionValueEstimate<>(
                exactValue == null ? Optional.empty() : exactValue,
                new LinkedHashSet<>(Objects.requireNonNullElse(possibleValues, Set.of())),
                reliable
        );
    }
}
