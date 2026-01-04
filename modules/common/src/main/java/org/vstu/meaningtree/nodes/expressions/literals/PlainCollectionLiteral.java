package org.vstu.meaningtree.nodes.expressions.literals;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.ArrayInitializer;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class PlainCollectionLiteral extends CollectionLiteral {
    @TreeNode private List<Expression> content;
    @TreeNode private Type typeHint = new UnknownType();

    public PlainCollectionLiteral(Expression ... content) {
        this.content = List.of(content);
    }

    public void setTypeHint(Type type) {
        if (type != null) this.typeHint = type;
    }

    @Nullable
    public Type getTypeHint() {
        return typeHint;
    }

    public PlainCollectionLiteral(List<Expression> exprs) {
        content = new ArrayList<>(exprs);}

    public List<Expression> getList() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlainCollectionLiteral that = (PlainCollectionLiteral) o;
        return Objects.equals(content, that.content) && Objects.equals(typeHint, that.typeHint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), content, typeHint);
    }

    @Override
    public PlainCollectionLiteral clone() {
        PlainCollectionLiteral obj = (PlainCollectionLiteral) super.clone();
        if (typeHint != null) obj.typeHint = typeHint.clone();
        obj.content = new ArrayList<>(content.stream().map(Expression::clone).toList());
        return obj;
    }

    public ArrayNewExpression toArrayNew() {
        int dimensions = 1;
        if (content.size() == 0) {
            return new ArrayNewExpression(typeHint, new Shape(dimensions), new ArrayInitializer(List.of()));
        }
        Expression item = content.getFirst();
        while (item instanceof PlainCollectionLiteral || (item instanceof ArrayNewExpression arr && typeHint.equals(arr.getType()))) {
            if (item instanceof PlainCollectionLiteral list)
            {
                if (list.getList().isEmpty()) {
                    break;
                }
                item = list.getList().getFirst();
                dimensions++;
            } else if (item instanceof ArrayNewExpression arr) {
                dimensions += arr.getShape().getDimensionCount();
                if (arr.getInitializer() == null || arr.getInitializer().getValues().isEmpty()) {
                    break;
                }
                item = arr.getInitializer().getValues().getFirst();
            }
        }
        return new ArrayNewExpression(getTypeHint(), new Shape(dimensions), new ArrayInitializer(getList()));
    }
}
