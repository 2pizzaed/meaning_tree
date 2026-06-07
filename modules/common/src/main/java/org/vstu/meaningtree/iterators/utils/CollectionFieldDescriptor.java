package org.vstu.meaningtree.iterators.utils;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Node;

import java.lang.reflect.Field;
import java.util.*;

public class CollectionFieldDescriptor extends FieldDescriptor implements Iterable<Node> {
    public CollectionFieldDescriptor(Node owner, String fieldName, Field field, boolean readOnly) {
        super(owner, fieldName, field, readOnly);
    }

    @Override
    public boolean ensureWritable() {
        super.ensureWritable();
        try {
            Object val = field.get(owner);
            if (val instanceof SequencedSet) {
                field.set(owner, new LinkedHashSet<>(get()));
                return true;
            } else if (val instanceof List) {
                field.set(owner, new ArrayList<>(get()));
                return true;
            }
        } catch (IllegalAccessException e) {}
        return false;
    }

    @Override
    public FieldDescriptor clone() {
        return new CollectionFieldDescriptor(owner, name, field, readOnly);
    }

    public boolean substituteCollection(Collection<?> values) {
        try {
            field.set(owner, values);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    public boolean canModifyCollection() throws IllegalAccessException {
        return field.get(owner).getClass().getSimpleName().toLowerCase().startsWith("unmodifiable");
    }

    public boolean isList() throws IllegalAccessException {
        return List.class.isInstance(field.get(owner));
    }

    public List<? extends Node> asList() throws IllegalAccessException {
        return (List<? extends Node>) field.get(owner);
    }

    public Collection<? extends Node> get() throws IllegalAccessException {
        return (Collection<? extends Node>) field.get(owner);
    }

    @Override
    public @NotNull Iterator<Node> iterator() {
        try {
            return (Iterator<Node>) get().iterator();
        } catch (IllegalAccessException e) {
            return new ArrayList<Node>().iterator();
        }
    }
}
