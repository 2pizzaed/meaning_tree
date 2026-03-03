package org.vstu.meaningtree.nodes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.DFSNodeIterator;
import org.vstu.meaningtree.iterators.utils.*;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.LabelAttachable;
import org.vstu.meaningtree.utils.ReplaceResult;
import org.vstu.meaningtree.utils.ReplaceStatus;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

abstract public class Node implements Serializable, Cloneable, LabelAttachable, NodeIterable {
    protected static AtomicLong _id_generator = new AtomicLong();
    protected long _id = _id_generator.incrementAndGet();

    /**
     * Внимание! После вызова этого метода, все новые узлы дерева начнут нумерацию своего id с нуля.
     * Это может привести к конфликтам. Убедитесь, что новые узлы не будут сравниваться по id с предыдущими узлами
     */
    public static void resetIdCounter() {
        System.err.println("Warning! Node counter was reset. It may cause conflicts");
        _id_generator = new AtomicLong();
    }

    public static void setupId(long startId) {
        _id_generator = new AtomicLong(startId);
    }

    @Override
    public @NotNull Iterator<NodeInfo> iterator() {
        return new DFSNodeIterator(this, false);
    }

    public List<NodeInfo> iterate(boolean includeSelf) {
        ArrayList<NodeInfo> list = new ArrayList<>();
        var iterator = new DFSNodeIterator(this, includeSelf);
        while (iterator.hasNext()) {
            list.add(iterator.next());
        }
        return list.reversed();
    }

    private Set<Label> _labels = new HashSet<>();

    /**
     * Проверяет значение узлов по значению
     * @param o другой объект
     * @return результат эквивалентности
     */
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return Objects.equals(_labels, node._labels);
    }

    /**
     * Уникальный хэш-код узла, исходя из его содержимого и типа
     * @return хэш-код
     */
    @Override
    public int hashCode() {
        List<Object> toHash = new ArrayList<>();
        toHash.add(getClass().getSimpleName());
        // toHash.addAll(_labels); WARNING: don't enable this! it may cause bugs with finding node in hash maps
        return Objects.hash(toHash.toArray(new Object[0]));
    }

    @Override
    public Node clone() {
        try {
            Node clone = (Node) super.clone();
            clone._id = getId();
            clone._labels = new HashSet<>(_labels);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public long getId() {
        return _id;
    }

    public boolean uniquenessEquals(Node other) {
        return this.getId() == other.getId();
    }

    private static Field[] getAllFields(Node instance) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = instance.getClass(); c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields.toArray(new Field[0]);
    }

    /**
     * Установить привязанный тег значения к узлу. Может быть полезен для внешней модификации дерева
     * @param obj - любой объект
     */
    public void setAssignedValueTag(@Nullable Object obj) {
        removeLabel(Label.VALUE);
        _labels.add(new Label(Label.VALUE, obj));
    }

    /**
     * Привязанный тег значения к узлу
     * @return любой объект, привязанный ранее или null
     */
    @Nullable
    public Object getAssignedValueTag() {
        Label label = getLabel(Label.VALUE);
        if (label != null) {
            return label.getAttribute();
        } else {
            return null;
        }
    }

    public String getNodeUniqueName() {
        return this.getClass().getName();
    }

    @Override
    public Node setLabel(Label label) {
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

    public Node remap(Node other) {
        return setLabel(new Label(Label.REMAPPED, other.getId()));
    }

    @Override
    public boolean removeLabel(Label label) {
        return _labels.remove(label);
    }

    @Override
    public Set<Label> getAllLabels() {
        return Set.copyOf(_labels);
    }

    public FieldDescriptor getFieldDescriptor(String fieldName) {
        return getFieldDescriptors().getOrDefault(fieldName, null);
    }

    public List<Node> allChildren() {
        return StreamSupport.stream(
                Spliterators.spliteratorUnknownSize(iterator(), Spliterator.ORDERED),
                false // параллельный ли стрим (false = последовательный)
        ).map(NodeInfo::node).toList();
    }

    public Map<String, FieldDescriptor> getFieldDescriptors() {
        Map<String, FieldDescriptor> result = new HashMap<>();
        for (Field field : getAllFields(this)) {
            TreeNode treeNode = (TreeNode) Arrays.stream(field.getAnnotations())
                    .filter((ann) -> ann instanceof TreeNode)
                    .findFirst().orElse(null);
            if (treeNode != null) {
                String name = treeNode.alias() != null && !treeNode.alias().isEmpty() ? treeNode.alias() : field.getName();
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(this);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (value instanceof Collection<?>) {
                    result.put(name, new CollectionFieldDescriptor(this, name, field, treeNode.readOnly()));
                } else if (value instanceof Node[]) {
                    result.put(name, new ArrayFieldDescriptor(this, name, field, treeNode.readOnly()));
                } else if (value instanceof Node) {
                    result.put(name, new NodeFieldDescriptor(this, name, field, treeNode.readOnly()));
                } else if (value instanceof Optional<?> opt && (opt.isPresent() ? opt.get() instanceof Node : true)) {
                    result.put(name, new NodeFieldDescriptor(this, name, field, treeNode.readOnly()));
                }
            }
        }
        return result;
    }

    public ReplaceResult replace(FieldDescriptor slot, Node newNode) {
        return doReplace(slot, newNode);
    }

    public ReplaceResult replace(NodeInfo target, Node newNode) {
        if (target == null || target.field() == null) {
            return new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "NodeInfo does not contain replaceable field", null, null, newNode);
        }
        return replace(target.field(), newNode);
    }

    public ReplaceResult replaceFirst(Predicate<NodeInfo> matcher, Function<Node, Node> replacer) {
        Objects.requireNonNull(matcher, "matcher is required");
        Objects.requireNonNull(replacer, "replacer is required");

        ReplaceResult lastFailure = new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "No matched nodes to replace", null, null, null);
        Iterator<NodeInfo> iterator = new DFSNodeIterator(this, true);
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
        Iterator<NodeInfo> iterator = new DFSNodeIterator(this, true);
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
        for (NodeInfo info : matches) {
            results.add(replace(info, replacer.apply(info.node())));
        }
        return List.copyOf(results);
    }

    private ReplaceResult doReplace(FieldDescriptor slot, Node newNode) {
        if (slot == null) {
            return new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "Field descriptor is null", null, null, newNode);
        }
        if (newNode == null) {
            return new ReplaceResult(ReplaceStatus.NULL_VALUE, "Replacement node is null", slot, null, null);
        }
        if (slot.getOwner() == null) {
            return new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "Field descriptor owner is null", slot, null, newNode);
        }
        if (slot.isReadOnly()) {
            return new ReplaceResult(ReplaceStatus.READ_ONLY, "Field is read-only", slot, null, newNode);
        }

        FieldDescriptor baseDescriptor = slot.getOwner().getFieldDescriptor(slot.getName());
        if (baseDescriptor == null) {
            return new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "Field does not exist in owner", slot, null, newNode);
        }

        if (slot instanceof NodeFieldDescriptor) {
            if (slot.isIndexed()) {
                return new ReplaceResult(ReplaceStatus.TYPE_MISMATCH, "Node field cannot have index", slot, null, newNode);
            }
            if (!slot.getType().isAssignableFrom(newNode.getClass())) {
                return new ReplaceResult(ReplaceStatus.TYPE_MISMATCH, "Replacement type is incompatible with field type", slot, null, newNode);
            }
            try {
                slot.ensureWritable();
                Node oldNode = ((NodeFieldDescriptor) slot).get();
                slot.getRawField().set(slot.getOwner(), newNode);
                return new ReplaceResult(ReplaceStatus.OK, "Node replaced", slot, oldNode, newNode);
            } catch (IllegalAccessException e) {
                return new ReplaceResult(ReplaceStatus.ILLEGAL_ACCESS, e.getMessage(), slot, null, newNode);
            }
        }

        if (slot instanceof ArrayFieldDescriptor arraySlot) {
            if (!slot.isIndexed()) {
                return new ReplaceResult(ReplaceStatus.INDEX_OUT_OF_BOUNDS, "Array replacement requires element index", slot, null, newNode);
            }
            Class<?> elementType = slot.elementTypeHint();
            if (elementType != null && !elementType.isAssignableFrom(newNode.getClass())) {
                return new ReplaceResult(ReplaceStatus.TYPE_MISMATCH, "Replacement type is incompatible with array element type", slot, null, newNode);
            }
            try {
                Node[] array = arraySlot.getArray();
                int index = slot.getIndex();
                if (index < 0 || index >= array.length) {
                    return new ReplaceResult(ReplaceStatus.INDEX_OUT_OF_BOUNDS, "Array index is out of bounds", slot, null, newNode);
                }
                Node oldNode = array[index];
                array[index] = newNode;
                return new ReplaceResult(ReplaceStatus.OK, "Array element replaced", slot, oldNode, newNode);
            } catch (IllegalAccessException e) {
                return new ReplaceResult(ReplaceStatus.ILLEGAL_ACCESS, e.getMessage(), slot, null, newNode);
            }
        }

        if (slot instanceof CollectionFieldDescriptor collectionSlot) {
            if (!slot.isIndexed()) {
                return new ReplaceResult(ReplaceStatus.INDEX_OUT_OF_BOUNDS, "Collection replacement requires element index", slot, null, newNode);
            }
            Class<?> elementType = slot.elementTypeHint();
            if (elementType != null && Node.class.isAssignableFrom(elementType) && !elementType.isAssignableFrom(newNode.getClass())) {
                return new ReplaceResult(ReplaceStatus.TYPE_MISMATCH, "Replacement type is incompatible with collection element type", slot, null, newNode);
            }
            try {
                Collection<? extends Node> sourceCollection = collectionSlot.get();
                if (!(sourceCollection instanceof List<?> sourceList)) {
                    return new ReplaceResult(ReplaceStatus.TYPE_MISMATCH, "Indexed replacement is supported only for list-based collections", slot, null, newNode);
                }
                int index = slot.getIndex();
                if (index < 0 || index >= sourceList.size()) {
                    return new ReplaceResult(ReplaceStatus.INDEX_OUT_OF_BOUNDS, "Collection index is out of bounds", slot, null, newNode);
                }
                List<Node> mutableCopy = new ArrayList<>(sourceList.size());
                for (Object element : sourceList) {
                    mutableCopy.add((Node) element);
                }
                Node oldNode = mutableCopy.set(index, newNode);
                slot.ensureWritable();
                slot.getRawField().set(slot.getOwner(), mutableCopy);
                return new ReplaceResult(ReplaceStatus.OK, "Collection element replaced", slot, oldNode, newNode);
            } catch (IllegalAccessException e) {
                return new ReplaceResult(ReplaceStatus.ILLEGAL_ACCESS, e.getMessage(), slot, null, newNode);
            }
        }

        return new ReplaceResult(ReplaceStatus.FIELD_NOT_FOUND, "Unsupported field descriptor type: " + slot.getClass().getSimpleName(), slot, null, newNode);
    }

    @Deprecated
    public boolean substituteField(String name, Object value) {
        if (!(value instanceof Node node)) {
            return false;
        }
        FieldDescriptor descr = getFieldDescriptor(name);
        if (descr == null) {
            return false;
        }
        return replace(descr, node).isSuccess();
    }

    @Deprecated
    public boolean substituteCollectionField(String name, Node value, int index) {
        FieldDescriptor descr = getFieldDescriptor(name);
        if (descr == null) {
            return false;
        }
        if (descr instanceof CollectionFieldDescriptor || descr instanceof ArrayFieldDescriptor) {
            descr = descr.withIndex(index);
        }
        return replace(descr, value).isSuccess();
    }

    @Override
    public String toString() {
        return "Node<type: %s, id: %d>".formatted(getClass().getSimpleName(), getId());
    }
}
