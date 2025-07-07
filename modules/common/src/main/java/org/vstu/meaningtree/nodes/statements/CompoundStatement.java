package org.vstu.meaningtree.nodes.statements;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;

import java.util.ArrayList;
import java.util.List;

public class CompoundStatement extends Statement {
    @TreeNode private List<Node> nodes;

    public CompoundStatement(Node... nodes) {
        this(List.of(nodes));
    }

    public CompoundStatement(List<Node> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    @Override
    public String generateDot() {
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("%s [label=\"%s\"];", _id, getClass().getSimpleName()));
        for (Node node : nodes) {
            builder.append(node.generateDot());
            builder.append(String.format("%s -- %s;\n", _id, node.getId()));
        }
        return builder.toString();
    }

    public int getLength() {
        return nodes.size();
    }

    public Node[] getNodes() {
        return nodes.toArray(new Node[0]);
    }

    public void substitute(int index, Node node) {
        nodes.set(index, node);
    }

    public void insert(int index, Node node) {
        nodes.add(index, node);
    }
}
