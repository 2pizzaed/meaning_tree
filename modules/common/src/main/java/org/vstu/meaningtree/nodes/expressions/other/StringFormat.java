package org.vstu.meaningtree.nodes.expressions.other;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.InterpolatedStringLiteral;

import java.util.List;

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

    public InterpolatedStringLiteral toInterpolatedString() {
        // TODO: needs implementation
        return null;
    }
}
