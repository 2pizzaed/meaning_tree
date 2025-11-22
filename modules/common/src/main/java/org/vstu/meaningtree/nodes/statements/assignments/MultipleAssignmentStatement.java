package org.vstu.meaningtree.nodes.statements.assignments;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;

import java.util.List;
import java.util.Objects;

public class MultipleAssignmentStatement extends Statement implements HasInitialization {
    // Множественное присваивание (применяется в Python)
    @TreeNode private List<AssignmentStatement> statements;

    public MultipleAssignmentStatement(AssignmentStatement ... stmts) {
        this(List.of(stmts));
    }

    public MultipleAssignmentStatement(List<AssignmentStatement> stmts) {
        statements = List.copyOf(stmts);
    }

    public List<AssignmentStatement> getStatements() {
        return statements;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        MultipleAssignmentStatement that = (MultipleAssignmentStatement) o;
        return Objects.equals(statements, that.statements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), statements);
    }

    public MultipleAssignmentStatement clone() {
        var clone = (MultipleAssignmentStatement) super.clone();
        clone.statements = statements.stream().map(AssignmentStatement::clone).toList();
        return clone;
    }
}
