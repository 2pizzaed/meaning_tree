package org.vstu.meaningtree.nodes.expressions.comprehensions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;

import java.util.Objects;

public abstract class Comprehension extends Expression {
    @TreeNode @NotNull protected ComprehensionItem item;

    @TreeNode @Nullable protected Expression condition;

    protected Comprehension(@NotNull ComprehensionItem item, @Nullable Expression condition) {
        this.item = item;
        this.condition = condition;
    }

    public interface ComprehensionItem {
        ComprehensionItem cloneItem();
    }


    public record ListItem(Expression value) implements ComprehensionItem {
        public ListItem cloneItem() {
            return new ListItem(value.clone());
        }
    };

    public record SetItem(Expression value) implements ComprehensionItem {
        public SetItem cloneItem() {
            return new SetItem(value.clone());
        }
    };

    public ComprehensionItem getItem() {
        return item;
    }

    public boolean hasCondition() {
        return condition != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comprehension that = (Comprehension) o;
        return Objects.equals(item, that.item) && Objects.equals(condition, that.condition);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), item, condition);
    }

    @NotNull
    public Expression getCondition() {
        return Objects.requireNonNull(condition, "Comprehension does not have condition");
    }

    @Override
    public Comprehension clone() {
        Comprehension obj = (Comprehension) super.clone();
        obj.condition = condition == null ? null : condition.clone();
        obj.item = item.cloneItem();
        return obj;
    }
}
