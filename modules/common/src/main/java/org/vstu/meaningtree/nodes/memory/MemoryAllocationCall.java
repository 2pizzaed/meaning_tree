package org.vstu.meaningtree.nodes.memory;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.NewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;

public class MemoryAllocationCall extends FunctionCall {
    private final boolean isClear;

    public MemoryAllocationCall(Type type, Expression objectCount, boolean isClear) {
        super(null, type, objectCount);
        this.isClear = isClear;
    }

    public boolean isClearAllocation() {
        return isClear;
    }

    public NewExpression toNew() {
        if (_arguments.get(1) instanceof IntegerLiteral lit && lit.getLongValue() == 1) {
            return new ObjectNewExpression((Type) _arguments.getFirst());
        } else {
            return new ArrayNewExpression((Type) _arguments.getFirst(), new Shape(1, _arguments.get(1)));
        }
    }

    public Type getType() {
        return (Type) getArguments().getFirst();
    }

    public Expression getCount() {
        return getArguments().get(1);
    }
}
