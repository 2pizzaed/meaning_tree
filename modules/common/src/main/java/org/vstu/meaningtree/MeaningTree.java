package org.vstu.meaningtree;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.iterators.DFSNodeIterator;
import org.vstu.meaningtree.iterators.utils.FieldDescriptor;
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
import java.util.function.Function;
import java.util.function.Predicate;

public class MeaningTree implements Serializable, LabelAttachable, Cloneable, NodeIterable {
    @TreeNode private Node rootNode;
    private LinkedHashMap<Long, NodeInfo> _index = null;
    private Set<Label> _labels = new HashSet<>();

    public MeaningTree(Node rootNode) {
        this.rootNode = rootNode;
    }

    public Node getRootNode() {
        return rootNode;
    }

    public void changeRoot(Node node) {
        rootNode = node;
        invalidateCache();
    }

    public void invalidateCache() {
        _index = null;
    }

    public void makeIndex() {
        LinkedHashMap<Long, NodeInfo> index = new LinkedHashMap<>();
        Iterator<NodeInfo> iterator = new DFSNodeIterator(rootNode, true);
        while (iterator.hasNext()) {
            NodeInfo node = iterator.next();
            if (node != null) {
                NodeInfo previous = index.put(node.node().getId(), node);
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate node id `%d` found for `%s` and `%s`"
                                    .formatted(node.node().getId(), previous.path(), node.path())
                    );
                }
            }
        }
        _index = index;
    }

    public NodeInfo getNodeById(long id) {
        if (_index == null) {
            makeIndex();
        }
        return _index.get(id);
    }

    @Override
    @NotNull
    public Iterator<NodeInfo> iterator() {
        return new DFSNodeIterator(rootNode, true);
    }

    public Node findParentOfNode(Node node) {
        if (node == null) {
            return null;
        }
        NodeInfo info = getNodeById(node.getId());
        return info == null ? null : info.parentNode();
    }

    public boolean hasNodeType(Class<? extends Node> type) {
        Objects.requireNonNull(type, "type must not be null");
        if (_index == null) {
            makeIndex();
        }
        for (NodeInfo inf : _index.values()) {
            if (type.isAssignableFrom(inf.node().getClass())) {
                return true;
            }
        }
        return false;
    }

    public boolean anyMatch(Predicate<Node> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        if (_index == null) {
            makeIndex();
        }
        for (NodeInfo inf : _index.values()) {
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
    public MeaningTree setLabel(Label label) {
        _labels.add(label);
        return this;
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
        if (_index == null) {
            makeIndex();
        }
        return _index.values().stream().map(NodeInfo::node).toList();
    }

    public List<NodeInfo> iterate() {
        if (_index == null) {
            makeIndex();
        }
        return List.copyOf(_index.values());
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
            invalidateCache();
        }
        return result;
    }

    public ReplaceResult replace(FieldDescriptor slot, Node newNode) {
        ReplaceResult result = rootNode.replace(slot, newNode);
        if (result.isSuccess()) {
            invalidateCache();
        }
        return result;
    }

    public ReplaceResult replace(NodeInfo target, Node newNode) {
        if (target == null) {
            return new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "NodeInfo is null", null, null, newNode);
        }
        if (newNode == null) {
            return new ReplaceResult(ReplaceStatus.NULL_VALUE, "Replacement node is null", target.field(), null, null);
        }
        if (rootNode != null && rootNode.uniquenessEquals(target.node())) {
            Node oldRoot = rootNode;
            changeRoot(newNode);
            return new ReplaceResult(ReplaceStatus.OK, "Root node replaced", null, oldRoot, newNode);
        }

        ReplaceResult result = rootNode.replace(target, newNode);
        if (result.isSuccess()) {
            invalidateCache();
        }
        return result;
    }

    public ReplaceResult replaceFirst(Predicate<NodeInfo> matcher, Function<Node, Node> replacer) {
        Objects.requireNonNull(matcher, "matcher is required");
        Objects.requireNonNull(replacer, "replacer is required");

        ReplaceResult lastFailure = new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "No matched nodes to replace", null, null, null);
        Iterator<NodeInfo> iterator = new DFSNodeIterator(rootNode, true);
        while (iterator.hasNext()) {
            NodeInfo info = iterator.next();
            if (!matcher.test(info)) {
                continue;
            }
            ReplaceResult result = replace(info, replacer.apply(info.node()));
            if (result.isSuccess()) {
                return result;
            }
            lastFailure = result;
        }
        return lastFailure;
    }

    public List<ReplaceResult> replaceAll(Predicate<NodeInfo> matcher, Function<Node, Node> replacer) {
        Objects.requireNonNull(matcher, "matcher is required");
        Objects.requireNonNull(replacer, "replacer is required");

        List<NodeInfo> matches = new ArrayList<>();
        Iterator<NodeInfo> iterator = new DFSNodeIterator(rootNode, true);
        while (iterator.hasNext()) {
            NodeInfo info = iterator.next();
            if (matcher.test(info)) {
                matches.add(info);
            }
        }

        matches.sort((left, right) -> {
            int byDepth = Integer.compare(right.depth(), left.depth());
            if (byDepth != 0) {
                return byDepth;
            }
            if (left.field() != null && right.field() != null
                    && Objects.equals(left.field().getOwner(), right.field().getOwner())
                    && Objects.equals(left.field().getName(), right.field().getName())
                    && left.field().isIndexed() && right.field().isIndexed()) {
                return Integer.compare(right.field().getIndex(), left.field().getIndex());
            }
            return 0;
        });

        List<ReplaceResult> results = new ArrayList<>();
        boolean changed = false;
        for (NodeInfo info : matches) {
            ReplaceResult result = replace(info, replacer.apply(info.node()));
            results.add(result);
            changed |= result.isSuccess();
        }
        if (changed) {
            invalidateCache();
        }
        return List.copyOf(results);
    }
}
