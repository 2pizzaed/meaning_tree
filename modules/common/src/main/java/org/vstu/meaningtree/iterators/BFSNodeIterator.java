package org.vstu.meaningtree.iterators;

import org.vstu.meaningtree.iterators.utils.*;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Experimental;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

@Experimental
public class BFSNodeIterator extends AbstractNodeIterator {

    private static class Frame {
        Node node;
        FieldDescriptor parentField;
        int depth;
        NodeInfo info;

        Frame(Node node, FieldDescriptor parentField, NodeInfo parentInfo, int depth) {
            this.node = node;
            this.parentField = parentField;
            this.depth = depth;
            this.info = new NodeInfo(node, parentInfo, parentField, depth);
        }
    }

    private final Queue<Frame> queue = new ArrayDeque<>();
    private final boolean includeRoot;

    public BFSNodeIterator(Node root) {
        this(root, true);
    }

    public BFSNodeIterator(Node root, boolean includeRoot) {
        this.includeRoot = includeRoot;
        if (root == null) {
            return;
        }
        if (includeRoot) {
            queue.offer(new Frame(root, null, null, 0));
        } else {
            // если корень пропускаем — сразу добавляем его детей
            NodeInfo rootInfo = new NodeInfo(root, null, null, -1);
            enqueueChildren(root, rootInfo, 0);
        }
    }

    @Override
    public boolean hasNext() {
        return !queue.isEmpty();
    }

    @Override
    public NodeInfo next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        Frame frame = queue.poll();

        // Добавляем детей текущего узла в очередь
        enqueueChildren(frame.node, frame.info, frame.depth + 1);

        return frame.info;
    }

    private void enqueueChildren(Node node, NodeInfo currentInfo, int depth) {
        if (node == null) {
            return;
        }
        Node parentNode = currentInfo == null ? null : currentInfo.parentNode();
        for (FieldDescriptor fd : node.getFieldDescriptors().values()) {
            try {
                if (fd instanceof NodeFieldDescriptor nfd) {
                    Node child = nfd.get();
                    if (child != null && checkEnterCondition(child, parentNode)) {
                        queue.offer(new Frame(child, fd, currentInfo, depth));
                    }
                } else if (fd instanceof ArrayFieldDescriptor afd) {
                    Iterator<Node> iter = afd.iterator();
                    int index = 0;
                    while (iter.hasNext()) {
                        Node child = iter.next();
                        if (child != null && checkEnterCondition(child, parentNode)) {
                            queue.offer(new Frame(child, fd.withIndex(index), currentInfo, depth));
                        }
                        index++;
                    }
                } else if (fd instanceof CollectionFieldDescriptor cfd) {
                    Iterator<Node> iter = cfd.iterator();
                    int index = 0;
                    while (iter.hasNext()) {
                        Node child = iter.next();
                        if (child != null && checkEnterCondition(child, parentNode)) {
                            queue.offer(new Frame(child, fd.withIndex(index), currentInfo, depth));
                        }
                        index++;
                    }
                }
            } catch (IllegalAccessException e) {
                // пропускаем поле
            }
        }
    }
}
