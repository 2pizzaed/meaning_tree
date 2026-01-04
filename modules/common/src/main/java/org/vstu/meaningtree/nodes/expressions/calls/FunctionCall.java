package org.vstu.meaningtree.nodes.expressions.calls;

import org.vstu.meaningtree.exceptions.IllegalUsageException;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.interfaces.Callable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FunctionCall extends Expression implements Callable {
    @TreeNode protected Expression function;
    @TreeNode protected List<Expression> arguments;

    public Expression getFunction() {
        return function;
    }

    public List<Expression> getArguments() {
        return List.copyOf(arguments);
    }

    public FunctionCall(Expression function, Expression ... arguments) {
        this(function, List.of(arguments));
    }

    public FunctionCall(Expression function, List<Expression> arguments) {
        if (function instanceof MemberAccess) {
            throw new IllegalUsageException("Use MethodCall instead this node");
        }
        this.function = function;
        this.arguments = arguments;
    }

    public boolean hasFunctionName() {
        return function instanceof SimpleIdentifier || (function instanceof ParenthesizedExpression paren && paren.getExpression() instanceof SimpleIdentifier);
    }

    public SimpleIdentifier getFunctionName() {
        if (hasFunctionName()) {
            if (function instanceof ParenthesizedExpression paren) {
                return (SimpleIdentifier) paren.getExpression();
            }
            return (SimpleIdentifier) function;
        }
        throw new MeaningTreeException("Function does not have identifier of call");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FunctionCall that = (FunctionCall) o;
        return Objects.equals(function, that.function) && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), function, arguments);
    }

    @Override
    public FunctionCall clone() {
        FunctionCall obj = (FunctionCall) super.clone();
        if (function != null) {
            obj.function = function.clone();
        }
        obj.arguments = new ArrayList<>(arguments.stream().map(Expression::clone).toList());
        return obj;
    }

    @Override
    public Expression getCallableName() {
        return function;
    }
}
