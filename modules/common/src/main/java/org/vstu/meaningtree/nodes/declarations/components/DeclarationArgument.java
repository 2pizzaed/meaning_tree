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
import org.vstu.meaningtree.utils.InternalNode;

import java.util.Objects;

@InternalNode
public class DeclarationArgument extends Declaration {
    @TreeNode private Type type;

    private boolean isListUnpacking;
    private boolean isDictUnpacking;

    @Nullable @TreeNode private SimpleIdentifier name;
    @Nullable @TreeNode private Expression initial;

    /**
     * Заменяет тип параметра. Нужен анализу, который уточняет тип уже после разбора — так же,
     * как {@code VariableDeclaration.setType} для переменной.
     */
    public void setType(Type newType) {
        type = newType;
    }

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

    /**
     * Имя параметра.
     * <p>
     * {@code null} — параметр без имени: в прототипе C/C++ ({@code double f(double);}) имя
     * необязательно, потому что обращаться к параметру там негде. Языки, где параметр без имени
     * невыразим, отказываются печатать такое объявление
     * ({@code UnnamedParameterFeature}), а не подставляют выдуманное имя.
     */
    @Nullable
    public SimpleIdentifier getName() {
        return name;
    }

    /** Есть ли у параметра имя; см. {@link #getName()}. */
    public boolean hasName() {
        return name != null;
    }

    /** Параметр, объявленный одним типом: {@code double f(double);}. */
    public static DeclarationArgument unnamed(Type type) {
        return new DeclarationArgument(type, null, null);
    }

    public DeclarationArgument(Type type, @Nullable SimpleIdentifier name, @Nullable Expression initial) {
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
        clone.name = name == null ? null : name.clone();
        clone.initial = initial == null ? null : initial.clone();
        return clone;
    }
}
