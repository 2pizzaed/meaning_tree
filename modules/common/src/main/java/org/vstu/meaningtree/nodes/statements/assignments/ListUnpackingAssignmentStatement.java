package org.vstu.meaningtree.nodes.statements.assignments;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.other.IndexExpression;
import org.vstu.meaningtree.nodes.interfaces.HasAssignmentEffect;

import java.util.ArrayList;
import java.util.List;

public class ListUnpackingAssignmentStatement extends Declaration implements HasAssignmentEffect {
    @TreeNode private List<Identifier> variableNames;
    @TreeNode private Expression value;

    public ListUnpackingAssignmentStatement(Expression value, List<Identifier> variableNames) {
        this.value = value;
        this.variableNames = List.copyOf(variableNames);
    }

    public ListUnpackingAssignmentStatement(Expression value, Identifier ... variableNames) {
        this.value = value;
        this.variableNames = List.of(variableNames);
    }

    public List<Identifier> getVariableNames() {
        return variableNames;
    }

    public Expression getValue() {
        return value;
    }

    public MultipleAssignmentStatement toMultipleAssignmentStstement() {
        List<AssignmentStatement> assignmentStatements = new ArrayList<>();
        for (int i = 0; i < variableNames.size(); i++) {
            assignmentStatements.add(new AssignmentStatement(variableNames.get(i), new IndexExpression(value, new IntegerLiteral(i))));
        }
        return new MultipleAssignmentStatement(assignmentStatements);
    }
}
