package org.vstu.meaningtree.nodes.expressions.newexpr;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ObjectNewExpression extends NewExpression {
    @TreeNode private List<Expression> constructorArguments;

    public ObjectNewExpression(Type type, Expression... constructorArguments) {
        this(type, List.of(constructorArguments));
    }

    public ObjectNewExpression(Type type, List<Expression> constructorArguments) {
        super(type);
        this.constructorArguments = List.copyOf(constructorArguments);
    }

    public List<Expression> getConstructorArguments() {
        return constructorArguments;
    }

    // anonymous classes unsupported

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ObjectNewExpression that = (ObjectNewExpression) o;
        return Objects.equals(constructorArguments, that.constructorArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), constructorArguments);
    }

    @Override
    public ObjectNewExpression clone() {
        ObjectNewExpression obj = (ObjectNewExpression) super.clone();
        obj.constructorArguments = new ArrayList<>(constructorArguments.stream().map(Expression::clone).toList());
        return obj;
    }
}
