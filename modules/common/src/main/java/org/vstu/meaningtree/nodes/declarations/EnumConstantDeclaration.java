package org.vstu.meaningtree.nodes.declarations;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.List;
import java.util.Objects;

/** Константа перечисления с необязательным явно заданным значением. */
public class EnumConstantDeclaration extends Declaration {
    @TreeNode
    private Identifier name;

    @TreeNode
    @Nullable
    private Expression value;

    public EnumConstantDeclaration(Identifier name, @Nullable Expression value) {
        this.name = Objects.requireNonNull(name);
        this.value = value;
        this.annotations = List.of();
    }

    public EnumConstantDeclaration(Identifier name) {
        this(name, null);
    }

    public Identifier getName() {
        return name;
    }

    @Nullable
    public Expression getValue() {
        return value;
    }

    public boolean hasValue() {
        return value != null;
    }

    public void setValue(@Nullable Expression value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof EnumConstantDeclaration constant)) return false;
        return super.equals(other)
                && Objects.equals(name, constant.name)
                && Objects.equals(value, constant.value)
                && Objects.equals(modifiers, constant.modifiers)
                && Objects.equals(annotations, constant.annotations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, value, modifiers, annotations);
    }

    @Override
    public EnumConstantDeclaration clone() {
        EnumConstantDeclaration clone = (EnumConstantDeclaration) super.clone();
        clone.name = name.clone();
        clone.value = value == null ? null : value.clone();
        return clone;
    }
}
