package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.comprehensions.Comprehension;
import org.vstu.meaningtree.utils.InternalNode;

import java.util.Objects;

@InternalNode
public class KeyValuePair extends Node implements Comprehension.ComprehensionItem {
    @TreeNode private Expression key;
    @TreeNode private Expression value;

    public KeyValuePair(Expression key, Expression value) {
        this.key = key;
        this.value = value;
    }

    public Expression key() {
        return key;
    }

    public Expression value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof KeyValuePair nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(key, nodeInfos.key) && Objects.equals(value, nodeInfos.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), key, value);
    }

    @Override
    public KeyValuePair clone() {
        return new KeyValuePair(key.clone(), value.clone());
    }

    @Override
    public Comprehension.ComprehensionItem cloneItem() {
        return clone();
    }
}
