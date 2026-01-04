package org.vstu.meaningtree.nodes.expressions.comparison;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class CompoundComparison extends Expression {
    @TreeNode private List<BinaryComparison> comparisons;

    /**
     * @param comparisons - отсортированный список сравнений (example of view: {a < b, b < c} => a < b < c)
     */
    public CompoundComparison(BinaryComparison ... comparisons) {
        for (int i = 0; i < comparisons.length - 1; i++) {
            if (!comparisons[i].getRight().equals(comparisons[i+1].getLeft())) {
                throw new IllegalArgumentException("Comparisons must be sorted. In pair of comparisons right operand of first must be equal to left operand of second comparison");
            }
        }
        this.comparisons = List.of(comparisons);
    }

    public CompoundComparison(List<BinaryComparison> members) {
        this(members.toArray(new BinaryComparison[0]));
    }

    public List<BinaryComparison> getComparisons() {
        return new ArrayList<>(comparisons);
    }

    @Override
    public boolean evaluatesToBoolean() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CompoundComparison that = (CompoundComparison) o;
        return Objects.equals(comparisons, that.comparisons);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), comparisons);
    }
}
