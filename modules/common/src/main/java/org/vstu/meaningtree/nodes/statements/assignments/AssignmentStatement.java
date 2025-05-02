package org.vstu.meaningtree.nodes.statements.assignments;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;

import java.util.Objects;

public class AssignmentStatement extends Statement implements HasInitialization {
    @TreeNode private Expression lvalue;
    @TreeNode private Expression rvalue;
    @TreeNode private AugmentedAssignmentOperator operatorType;

    public AssignmentStatement(Expression id, Expression value, AugmentedAssignmentOperator op) {
        lvalue = id;
        rvalue = value;
        operatorType = op;
    }

    public AssignmentStatement(Expression id, Expression value) {
        this(id, value, AugmentedAssignmentOperator.NONE);
    }

    public AugmentedAssignmentOperator getAugmentedOperator() {
        return operatorType;
    }

    public Expression getLValue() {
        return lvalue;
    }

    public Expression getRValue() {
        return rvalue;
    }

    public AssignmentExpression toExpression() {
        return new AssignmentExpression(lvalue, rvalue, operatorType);
    }

    @Override
    public String generateDot() {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%s [label=\"%s\"];\n", _id, operatorType.toString()));

        builder.append(lvalue.generateDot());
        builder.append(rvalue.generateDot());

        builder.append(String.format("%s -- %s;\n", _id, lvalue.getId()));
        builder.append(String.format("%s -- %s;\n", _id, rvalue.getId()));

        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        AssignmentStatement that = (AssignmentStatement) o;
        return Objects.equals(lvalue, that.lvalue) && Objects.equals(rvalue, that.rvalue) && operatorType == that.operatorType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lvalue, rvalue, operatorType);
    }
}
