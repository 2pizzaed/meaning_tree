package org.vstu.meaningtree.nodes.expressions.literals;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.other.KeyValuePair;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.stream.Collectors;

public class DictionaryLiteral extends CollectionLiteral {
    @TreeNode private List<KeyValuePair> content;
    @TreeNode @Nullable private Type keyTypeHint;
    @TreeNode @Nullable private Type valueTypeHint;

    public DictionaryLiteral(SequencedMap<Expression, Expression> content) {
        this.content = content.entrySet().stream()
                .map(e -> new KeyValuePair(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    public void setKeyTypeHint(@Nullable Type type) {
        this.keyTypeHint = type;
    }

    @Nullable
    public Type getKeyTypeHint() {
        return keyTypeHint;
    }

    public void setValueTypeHint(@Nullable Type type) {
        this.valueTypeHint = type;
    }

    @Nullable
    public Type getValueTypeHint() {
        return valueTypeHint;
    }

    public SequencedMap<Expression, Expression> getDictionary() {
        var map = new LinkedHashMap<Expression, Expression>();
        for (KeyValuePair kvp : content) {
            map.put(kvp.key(), kvp.value());
        }
        return map;
    }

    public List<KeyValuePair> getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DictionaryLiteral that = (DictionaryLiteral) o;
        return Objects.equals(content, that.content) && Objects.equals(keyTypeHint, that.keyTypeHint) && Objects.equals(valueTypeHint, that.valueTypeHint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), content, keyTypeHint, valueTypeHint);
    }

    @Override
    public DictionaryLiteral clone() {
        DictionaryLiteral obj = (DictionaryLiteral) super.clone();
        obj.content = content.stream().map(KeyValuePair::clone).collect(Collectors.toList());
        if (keyTypeHint != null) obj.keyTypeHint = keyTypeHint.clone();
        if (valueTypeHint != null) obj.valueTypeHint = valueTypeHint.clone();
        return obj;
    }

    public List<UnmodifiableListLiteral> asPairsListLiteral() {
        return getDictionary().entrySet().stream().map(entry ->
                new UnmodifiableListLiteral(entry.getKey(), entry.getValue())).toList();
    }
}
