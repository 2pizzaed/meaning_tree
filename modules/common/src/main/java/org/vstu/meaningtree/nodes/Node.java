package org.vstu.meaningtree.nodes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.DFSNodeIterator;
import org.vstu.meaningtree.iterators.utils.*;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.LabelAttachable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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
        toHash.addAll(_labels);
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

    public boolean substituteField(String name, Object value) {
        FieldDescriptor descr = getFieldDescriptor(name);
        if (descr == null) {
            return false;
        }
        if (descr instanceof CollectionFieldDescriptor colDescr && value instanceof Collection<?>) {
            return colDescr.substituteCollection((Collection<?>) value);
        } else if (value instanceof Node node && descr instanceof NodeFieldDescriptor) {
            return descr.substitute(node);
        }
        return false;
    }

    public boolean substituteCollectionField(String name, Node value, int index) {
        FieldDescriptor descr = getFieldDescriptor(name);
        if (descr instanceof CollectionFieldDescriptor || descr instanceof ArrayFieldDescriptor) {
            descr = descr.withIndex(index);
        }
        return descr.substitute(value);
    }

    @Override
    public String toString() {
        return "Node<type: %s, id: %d>".formatted(getClass().getSimpleName(), getId());
    }
}
