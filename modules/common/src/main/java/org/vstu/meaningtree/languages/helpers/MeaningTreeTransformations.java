package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Последовательно применяет проходы преобразования к дереву.
 */
public final class MeaningTreeTransformations {
    private MeaningTreeTransformations() {
    }

    public static MeaningTree apply(MeaningTree tree, List<UnaryOperator<MeaningTree>> transformations) {
        MeaningTree result = Objects.requireNonNull(tree, "tree must not be null");
        Objects.requireNonNull(transformations, "transformations must not be null");
        for (int index = 0; index < transformations.size(); index++) {
            UnaryOperator<MeaningTree> transformation = Objects.requireNonNull(
                    transformations.get(index),
                    "transformation at index " + index + " must not be null"
            );
            result = Objects.requireNonNull(
                    transformation.apply(result),
                    "transformation at index " + index + " returned null"
            );
        }
        return result;
    }
}
