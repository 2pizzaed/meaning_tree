package org.vstu.meaningtree.nodes.statements.assignments;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;

import java.util.List;
import java.util.Objects;

public class CompoundAssignmentStatement extends Node implements HasInitialization {
    /**
     * This node used only in case of compound assignment in for header
     * Example: for(a = 1, b = 3; i < 3; i++)
     */

    @TreeNode private List<AssignmentStatement> assignments;

    public CompoundAssignmentStatement(AssignmentStatement ... assignments) {
        this.assignments = List.of(assignments);
    }

    public AssignmentStatement[] getAssignments() {
        return assignments.toArray(new AssignmentStatement[0]);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CompoundAssignmentStatement that = (CompoundAssignmentStatement) o;
        return Objects.equals(assignments, that.assignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), assignments);
    }

    public CompoundAssignmentStatement clone() {
        var clone = (CompoundAssignmentStatement) super.clone();
        clone.assignments = assignments.stream().map(AssignmentStatement::clone).toList();
        return clone;
    }
}
