package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;

import java.util.Objects;

public class AssignmentExpression extends BinaryExpression implements HasInitialization {
    private AugmentedAssignmentOperator operatorType;

    public AssignmentExpression(Expression id, Expression value, AugmentedAssignmentOperator op) {
        super(id, value);
        operatorType = op;
    }

    public AssignmentStatement toStatement() {
        return new AssignmentStatement(left, right, operatorType);
    }

    public AssignmentExpression(Expression id, Expression value) {
        this(id, value, AugmentedAssignmentOperator.NONE);
    }

    public AugmentedAssignmentOperator getAugmentedOperator() {
        return operatorType;
    }

    public Expression getLValue() {
        return left;
    }

    public Expression getRValue() {
        return right;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssignmentExpression that = (AssignmentExpression) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right) && operatorType == that.operatorType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), left, right, operatorType);
    }

    @Override
    public AssignmentExpression clone() {
        AssignmentExpression obj = (AssignmentExpression) super.clone();
        obj.left = left.clone();
        obj.right = right.clone();
        return obj;
    }
}
