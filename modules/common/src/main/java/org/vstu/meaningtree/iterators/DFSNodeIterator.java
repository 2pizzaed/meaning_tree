package org.vstu.meaningtree.iterators;

import org.vstu.meaningtree.iterators.utils.*;
import org.vstu.meaningtree.nodes.Node;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class DFSNodeIterator extends AbstractNodeIterator {

    private static class Frame {
        Node node;
        FieldDescriptor parentField;
        Iterator<FieldDescriptor> fieldIterator;
        Iterator<Node> nodeIterator;
        int fieldIndex = -1;
        FieldDescriptor currentField;
        boolean visitedChildren = false;

        Frame(Node node, FieldDescriptor parentField) {
            this.node = node;
            this.parentField = parentField;
            this.fieldIterator = node.getFieldDescriptors().values().iterator();
        }
    }

    private final Deque<Frame> stack = new ArrayDeque<>();
    private final boolean includeRoot;

    public DFSNodeIterator(Node root) {
        this(root, true);
    }

    public DFSNodeIterator(Node root, boolean includeRoot) {
        this.includeRoot = includeRoot;
        if (includeRoot) {
            stack.push(new Frame(root, null));
        } else {
            // если корень пропускаем — сразу добавляем его детей
            Frame rootFrame = new Frame(root, null);
            prePushChildren(rootFrame);
        }
    }

    @Override
    public boolean hasNext() {
        return !stack.isEmpty();
    }

    @Override
    public NodeInfo next() {
        while (!stack.isEmpty()) {
            Frame frame = stack.peek();
            Node parentNode = frame.parentField == null ? null : frame.parentField.getOwner();

            if (!frame.visitedChildren) {
                while (frame.fieldIterator.hasNext()) {
                    FieldDescriptor fd = frame.fieldIterator.next();
                    frame.currentField = fd;
                    frame.fieldIndex = -1;

                    try {
                        if (fd instanceof NodeFieldDescriptor nfd) {
                            Node child = nfd.get();
                            if (!checkEnterCondition(child, parentNode)) {
                                break;
                            }
                            stack.push(new Frame(child, fd));
                            return next(); // углубляемся дальше
                        } else if (fd instanceof ArrayFieldDescriptor afd) {
                            frame.nodeIterator = afd.iterator();
                            frame.fieldIndex = -1;
                            break;
                        } else if (fd instanceof CollectionFieldDescriptor cfd) {
                            frame.nodeIterator = cfd.iterator();
                            frame.fieldIndex = -1;
                            break;
                        }
                    } catch (IllegalAccessException e) {
                        // пропускаем поле
                    }
                }

                if (frame.nodeIterator != null) {
                    while (frame.nodeIterator.hasNext()) {
                        Node child = frame.nodeIterator.next();
                        frame.fieldIndex++;
                        if (!checkEnterCondition(child, parentNode)) {
                            break;
                        }
                        stack.push(new Frame(child, frame.currentField.withIndex(frame.fieldIndex)));
                        return next();
                    }
                }

                if (!(frame.fieldIterator != null && frame.fieldIterator.hasNext())) {
                    frame.visitedChildren = true;
                }
                continue;
            }

            stack.pop();
            int depth = stack.size();
            return new NodeInfo(frame.node, parentNode, frame.parentField, depth);
        }

        throw new NoSuchElementException();
    }

    private void prePushChildren(Frame frame) {
        while (frame.fieldIterator.hasNext()) {
            FieldDescriptor fd = frame.fieldIterator.next();
            frame.currentField = fd;
            frame.fieldIndex = -1;

            try {
                if (fd instanceof NodeFieldDescriptor nfd) {
                    Node child = nfd.get();
                    stack.push(new Frame(child, fd));
                    prePushChildren(stack.peek());
                } else if (fd instanceof ArrayFieldDescriptor afd) {
                    Iterator<Node> iter = afd.iterator();
                    int idx = -1;
                    while (iter.hasNext()) {
                        Node child = iter.next();
                        idx++;
                        stack.push(new Frame(child, fd.withIndex(idx)));
                        prePushChildren(stack.peek());
                    }
                } else if (fd instanceof CollectionFieldDescriptor cfd) {
                    Iterator<Node> iter = cfd.iterator();
                    int idx = -1;
                    while (iter.hasNext()) {
                        Node child = iter.next();
                        idx++;
                        stack.push(new Frame(child, fd.withIndex(idx)));
                        prePushChildren(stack.peek());
                    }
                }
            } catch (IllegalAccessException e) {
                // пропускаем поле
            }
        }
        frame.visitedChildren = true;
    }
}
