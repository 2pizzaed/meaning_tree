package org.vstu.meaningtree.nodes.declarations.components;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.interfaces.HasComputedType;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.utils.InternalNode;

import java.util.Objects;

@InternalNode
public class VariableDeclarator extends Node implements HasComputedType {
    @TreeNode private SimpleIdentifier identifier;
    @TreeNode @Nullable private Expression rvalue;

    /**
     * Фактический тип инициализирующего выражения, в отличие от декларированного типа
     * переменной (который хранится в {@link org.vstu.meaningtree.nodes.declarations.VariableDeclaration})
     * вычисляется type inferrer'ом по rvalue (полезно для статического анализа, например при полиморфизме).
     */
    @TreeNode private Type realType = new UnknownType();

    public VariableDeclarator(SimpleIdentifier identifier, @Nullable Expression rvalue) {
        this.identifier = identifier;
        this.rvalue = rvalue;
    }

    public VariableDeclarator(SimpleIdentifier identifier) {
        this(identifier, null);
    }

    @Nullable
    public Expression getRValue() {
        return rvalue;
    }

    public boolean hasInitialization() {
        return rvalue != null;
    }

    public SimpleIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public Type getRealType() {
        return realType;
    }

    @Override
    public void setRealType(Type realType) {
        this.realType = realType;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VariableDeclarator that = (VariableDeclarator) o;
        return Objects.equals(identifier, that.identifier) && Objects.equals(rvalue, that.rvalue)
                && Objects.equals(realType, that.realType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), identifier, rvalue, realType);
    }

    public VariableDeclarator clone() {
        var clone = (VariableDeclarator) super.clone();
        clone.identifier = identifier.clone();
        clone.rvalue = rvalue == null ? null : rvalue.clone();
        clone.realType = realType.clone();
        return clone;
    }
}
