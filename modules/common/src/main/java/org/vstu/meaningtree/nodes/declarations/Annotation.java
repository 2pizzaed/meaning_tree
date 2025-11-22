package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.List;
import java.util.Objects;

public class Annotation extends Node {
    @TreeNode private List<Expression> arguments;
    @TreeNode private final Expression function;

    public Annotation(Expression function, Expression... arguments) {
        this.function = function;
        this.arguments = List.of(arguments);
    }

    public Expression[] getArguments() {
        return arguments.toArray(new Expression[0]);
    }

    public Expression getFunctionExpression() {
        return function;
    }

    public boolean hasName() {
        return function instanceof Identifier;
    }

    public Identifier getName() {
        return (Identifier) function;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Annotation nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(arguments, nodeInfos.arguments) && Objects.equals(function, nodeInfos.function);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), arguments, function);
    }

    public Annotation clone() {
        var clone = (Annotation) super.clone();
        clone.arguments = arguments.stream().map(Expression::clone).toList();
        return clone;
    }
}
