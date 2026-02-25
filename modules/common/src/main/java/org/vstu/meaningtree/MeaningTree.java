package org.vstu.meaningtree;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.iterators.DFSNodeIterator;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.LabelAttachable;
import org.vstu.meaningtree.utils.ReplaceResult;
import org.vstu.meaningtree.utils.ReplaceStatus;

import java.io.Serializable;
import java.util.*;
import java.util.function.Predicate;

public class MeaningTree implements Serializable, LabelAttachable, Cloneable, NodeIterable {
    @TreeNode private Node rootNode;
    private TreeMap<Long, NodeInfo> _index = null;
    private Set<Label> _labels = new HashSet<>();

    public MeaningTree(Node rootNode) {
        this.rootNode = rootNode;
    }

    public Node getRootNode() {
        return rootNode;
    }

    public void changeRoot(Node node) {
        rootNode = node;
        _index = null;
    }

    public void makeIndex() {
        TreeMap<Long, NodeInfo> treeMap = new TreeMap<>();
        for (NodeInfo node : this) {
            if (node != null) {
                treeMap.put(node.node().getId(), node);
            }
        }
        _index = treeMap;
    }

    public NodeInfo getNodeById(long id) {
        if (_index == null || _index.isEmpty()) {
            makeIndex();
        }
        return _index.getOrDefault(id, null);
    }

    @Override
    @NotNull
    public Iterator<NodeInfo> iterator() {
        if (_index == null) {
            return new DFSNodeIterator(rootNode, true);
        } else {
            return _index.sequencedValues().iterator();
        }
    }

    public Node findParentOfNode(Node node) {
        for (NodeInfo inf : this) {
            if (inf.node().equals(node)) {
                return inf.parent();
            }
        }
        return null;
    }

    public boolean hasNodeType(Class<? extends Node> type) {
        for (NodeInfo inf : this) {
            if (type.isAssignableFrom(inf.node().getClass())) {
                return true;
            }
        }
        return false;
    }

    public boolean anyMatch(Predicate<Node> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        for (NodeInfo inf : this) {
            if (predicate.test(inf.node())) {
                return true;
            }
        }
        return false;
    }

    @Deprecated
    public boolean hasFeature(Class<? extends Node> feature) {
        return hasNodeType(feature);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        MeaningTree that = (MeaningTree) o;
        return Objects.equals(rootNode, that.rootNode);
    }

    @Override
    public int hashCode() {
        List<Object> toHash = new ArrayList<>();
        toHash.add(rootNode);
        toHash.addAll(_labels);
        return Objects.hash(toHash.toArray(new Object[0]));
    }

    @Override
    public MeaningTree clone() {
        MeaningTree mt = new MeaningTree(rootNode.clone());
        mt._labels = new HashSet<>(this._labels);
        return mt;
    }

    @Override
    public void setLabel(Label label) {
        _labels.add(label);
    }

    @Override
    public Label getLabel(short id) {
        return _labels.stream().filter((Label l) -> l.getId() == id).findFirst().orElse(null);
    }

    @Override
    public boolean hasLabel(short id) {
        return _labels.stream().anyMatch((Label l) -> l.getId() == id);
    }

    @Override
    public boolean removeLabel(Label label) {
        return _labels.remove(label);
    }

    @Override
    public Set<Label> getAllLabels() {
        return Set.copyOf(_labels);
    }

    public List<Node> allChildren() {
        ArrayList<Node> children = new ArrayList<>();
        children.addAll(rootNode.allChildren());
        children.add(rootNode);
        return children;
    }

    public List<NodeInfo> iterate() {
        ArrayList<NodeInfo> children = new ArrayList<>();
        children.addAll(rootNode.iterate(true));
        return children;
    }

    public ReplaceResult replace(long id, Node node) {
        if (node == null) {
            return new ReplaceResult(ReplaceStatus.NULL_VALUE, "Replacement node is null", null, null, null);
        }

        NodeInfo nodeInfo = getNodeById(id);
        if (nodeInfo == null) {
            return new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "Node with id `%d` was not found".formatted(id), null, null, node);
        }

        if (rootNode.uniquenessEquals(nodeInfo.node())) {
            Node oldRoot = rootNode;
            changeRoot(node);
            return new ReplaceResult(ReplaceStatus.OK, "Root node replaced", null, oldRoot, node);
        }

        ReplaceResult result = rootNode.replace(nodeInfo, node);
        if (result.isSuccess()) {
            _index = null;
        }
        return result;
    }

    @Deprecated
    public boolean substitute(long id, Node node) {
        return replace(id, node).isSuccess();
    }
}
