package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.interfaces.HasAssignmentEffect;
import org.vstu.meaningtree.nodes.interfaces.HasComputedType;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;

import java.util.Objects;

public class AssignmentExpression extends BinaryExpression implements HasInitialization, HasAssignmentEffect, HasComputedType {
    private AugmentedAssignmentOperator operatorType;

    /**
     * Фактический тип присваиваемого значения, в отличие от типа переменной он не
     * декларативный, а вычисляется type inferrer'ом по правой части присваивания
     * (полезно для статического анализа, например при полиморфизме).
     */
    @TreeNode private Type realType = new UnknownType();

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
    public Type getRealType() {
        return realType;
    }

    @Override
    public void setRealType(Type realType) {
        this.realType = realType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AssignmentExpression that = (AssignmentExpression) o;
        return Objects.equals(left, that.left) && Objects.equals(right, that.right)
                && operatorType == that.operatorType && Objects.equals(realType, that.realType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), left, right, operatorType, realType);
    }

    @Override
    public AssignmentExpression clone() {
        AssignmentExpression obj = (AssignmentExpression) super.clone();
        obj.left = left.clone();
        obj.right = right.clone();
        obj.realType = realType.clone();
        return obj;
    }
}
