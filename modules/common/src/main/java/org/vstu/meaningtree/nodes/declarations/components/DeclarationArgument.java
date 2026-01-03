package org.vstu.meaningtree.nodes.declarations.components;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.containers.ArrayType;
import org.vstu.meaningtree.nodes.types.containers.OrderedDictionaryType;

import java.util.Objects;

public class DeclarationArgument extends Declaration {
    @TreeNode private Type type;

    private boolean isListUnpacking;
    private boolean isDictUnpacking;

    @TreeNode private SimpleIdentifier name;
    @Nullable @TreeNode private Expression initial;

    public Type getType() {
        if (isListUnpacking) {
            return new ArrayType(type, 1);
        } else if (isDictUnpacking) {
            return new OrderedDictionaryType(type, new UnknownType());
        }
        return type;
    }

    public Type getElementType() {
        return type;
    }

    public SimpleIdentifier getName() {
        return name;
    }

    public DeclarationArgument(Type type, SimpleIdentifier name, @Nullable Expression initial) {
        this.type = type;
        this.name = name;
        this.initial = initial;
    }

    public static DeclarationArgument dictUnpacking(Type type, SimpleIdentifier name) {
        var res = new DeclarationArgument(type, name, null);
        res.isDictUnpacking = true;
        return res;
    }

    public static DeclarationArgument listUnpacking(Type type, SimpleIdentifier name) {
        var res = new DeclarationArgument(type, name, null);
        res.isListUnpacking = true;
        return res;
    }

    public Expression getInitialExpression() {
        return initial;
    }

    public boolean hasInitialExpression() {
        return initial != null;
    }

    public boolean isListUnpacking() {
        return isListUnpacking;
    }

    public boolean isDictUnpacking() {
        return isDictUnpacking;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        DeclarationArgument that = (DeclarationArgument) o;
        return isListUnpacking == that.isListUnpacking && isDictUnpacking == that.isDictUnpacking &&
                Objects.equals(type, that.type) && Objects.equals(name, that.name)
                && Objects.equals(initial, that.initial);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type, isListUnpacking, name, initial);
    }

    public DeclarationArgument clone() {
        var clone =  (DeclarationArgument) super.clone();
        clone.type = type.clone();
        clone.isListUnpacking = isListUnpacking;
        clone.isDictUnpacking = isDictUnpacking;
        clone.name = name.clone();
        clone.initial = initial.clone();
        return clone;
    }
}
