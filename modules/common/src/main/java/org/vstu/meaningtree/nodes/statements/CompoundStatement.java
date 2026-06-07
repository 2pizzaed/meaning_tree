package org.vstu.meaningtree.nodes.statements;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.utils.scopes.ScopeTableElement;

import java.util.*;

public class CompoundStatement extends Statement {
    @TreeNode private List<Node> nodes;
    private transient ScopeTableElement scope;

    public CompoundStatement(Node... nodes) {
        this(List.of(nodes));
    }

    public CompoundStatement(List<Node> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    public int getLength() {
        return nodes.size();
    }

    public Node[] getNodes() {
        return nodes.toArray(new Node[0]);
    }

    public List<Node> getNodeList() {
        return List.copyOf(nodes);
    }

    public Optional<ScopeTableElement> getScope() {
        return Optional.ofNullable(scope);
    }

    public OptionalLong getScopeId() {
        return scope == null ? OptionalLong.empty() : OptionalLong.of(scope.getId());
    }

    public void bindScope(ScopeTableElement scope) {
        this.scope = scope;
    }

    public void substitute(int index, Node node) {
        nodes.set(index, node);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CompoundStatement nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(nodes, nodeInfos.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), nodes);
    }

    public void insert(int index, Node node) {
        nodes.add(index, node);
    }

    public CompoundStatement clone() {
        var clone = (CompoundStatement) super.clone();
        clone.nodes = new ArrayList<>(nodes.stream().map(Node::clone).toList());
        clone.scope = null;
        return clone;
    }
}
