package org.vstu.meaningtree.nodes.expressions.calls;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.Callable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConstructorCall extends Expression implements Callable {
    @TreeNode protected List<Expression> arguments;
    @TreeNode protected Type constructorOwner;
    private final boolean isBaseClassCall;

    public ConstructorCall(Type constructorOwner, boolean isBaseClassCall, List<Expression> arguments) {
        this.arguments = arguments;
        this.constructorOwner = constructorOwner;
        this.isBaseClassCall = isBaseClassCall;
    }

    public ConstructorCall(Type constructorOwner, List<Expression> arguments) {
        this(constructorOwner, false, arguments);
    }

    public ConstructorCall(Type constructorOwner, Expression ... arguments) {
        this(constructorOwner, List.of(arguments));
    }

    public ConstructorCall(Type constructorOwner, boolean isBaseClassCall, Expression ... arguments) {
        this(constructorOwner, isBaseClassCall, List.of(arguments));
    }

    public List<Expression> getArguments() {
        return List.copyOf(arguments);
    }

    public Type getOwner() {
        return constructorOwner;
    }

    public boolean isBaseClassCall() {
        return isBaseClassCall;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ConstructorCall that = (ConstructorCall) o;
        return isBaseClassCall == that.isBaseClassCall
                && Objects.equals(arguments, that.arguments)
                && Objects.equals(constructorOwner, that.constructorOwner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), arguments, constructorOwner, isBaseClassCall);
    }

    @Override
    public ConstructorCall clone() {
        ConstructorCall obj = (ConstructorCall) super.clone();
        obj.arguments = new ArrayList<>(arguments.stream().map(Expression::clone).toList());
        obj.constructorOwner = constructorOwner.clone();
        return obj;
    }

    @Override
    public Expression getCallableName() {
        return getOwner();
    }
}
