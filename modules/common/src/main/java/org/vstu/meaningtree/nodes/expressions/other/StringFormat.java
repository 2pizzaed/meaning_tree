package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.InterpolatedStringLiteral;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class StringFormat extends Expression {
    @TreeNode private Expression template;
    @TreeNode private Expression[] substitutions;

    public StringFormat(Expression template, Expression[] substitutions) {
        this.template = template;
        this.substitutions = substitutions;
    }

    public StringFormat(Expression template) {
        this(template, new Expression[0]);
    }

    public Expression[] getSubstitutions() {
        return this.substitutions;
    }

    public Expression getTemplate() {
        return this.template;
    }

    public List<Expression> getSubstitutionList() {
        return List.of(this.substitutions);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        StringFormat that = (StringFormat) o;
        return Objects.equals(template, that.template) && Arrays.equals(substitutions, that.substitutions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), template, substitutions);
    }

    @Override
    public StringFormat clone() {
        StringFormat obj = (StringFormat) super.clone();
        obj.template = template.clone();
        obj.substitutions = Arrays.copyOf(substitutions, substitutions.length);
        for (int i = 0; i < obj.substitutions.length; i++) {
            obj.substitutions[i] = substitutions[i].clone();
        }
        return obj;
    }

    public InterpolatedStringLiteral toInterpolatedString() {
        // TODO: needs implementation
        return null;
    }
}
