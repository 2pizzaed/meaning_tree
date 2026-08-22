package org.vstu.meaningtree.nodes.statements.assignments;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.interfaces.HasAssignmentEffect;
import org.vstu.meaningtree.nodes.interfaces.HasComputedType;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.types.UnknownType;

import java.util.List;
import java.util.Objects;

/**
 * Assignment of one value to several targets, for example {@code a = b = value}.
 * The value is evaluated once before it is assigned to every target.
 */
public class ChainedAssignmentStatement extends Statement implements HasInitialization, HasAssignmentEffect, HasComputedType {
    @TreeNode private List<Expression> targets;
    @TreeNode private Expression value;
    @TreeNode private List<VariableDeclaration> variableDeclarations;

    /** См. {@link HasComputedType}. */
    @TreeNode private Type realType = new UnknownType();

    public ChainedAssignmentStatement(List<Expression> targets, Expression value,
                                     List<VariableDeclaration> variableDeclarations) {
        if (targets.size() < 2) {
            throw new IllegalArgumentException("Chained assignment requires at least two targets");
        }
        this.targets = List.copyOf(targets);
        this.value = value;
        this.variableDeclarations = List.copyOf(variableDeclarations);
    }

    public List<Expression> getTargets() {
        return targets;
    }

    public Expression getValue() {
        return value;
    }

    public List<VariableDeclaration> getVariableDeclarations() {
        return variableDeclarations;
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
    public ChainedAssignmentStatement clone() {
        var clone = (ChainedAssignmentStatement) super.clone();
        clone.targets = targets.stream().map(Expression::clone).toList();
        clone.value = value.clone();
        clone.variableDeclarations = variableDeclarations.stream().map(VariableDeclaration::clone).toList();
        clone.realType = realType.clone();
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ChainedAssignmentStatement that = (ChainedAssignmentStatement) o;
        return Objects.equals(targets, that.targets)
                && Objects.equals(value, that.value)
                && Objects.equals(variableDeclarations, that.variableDeclarations)
                && Objects.equals(realType, that.realType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), targets, value, variableDeclarations, realType);
    }
}
