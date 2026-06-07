package org.vstu.meaningtree.iterators.utils;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Node;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ArrayFieldDescriptor extends FieldDescriptor implements Iterable<Node> {
    public ArrayFieldDescriptor(Node owner, String fieldName, Field field, boolean readOnly) {
        super(owner, fieldName, field, readOnly);
    }

    @Override
    public FieldDescriptor clone() {
        return new ArrayFieldDescriptor(owner, name, field, readOnly);
    }

    public int length() throws IllegalAccessException {
        return getArray().length;
    }

    public Node[] getArray() throws IllegalAccessException {
        return (Node[]) field.get(owner);
    }

    @Override
    public @NotNull Iterator<Node> iterator() {
        try {
            return Arrays.stream(getArray()).iterator();
        } catch (IllegalAccessException e) {
            return new ArrayList<Node>().iterator();
        }
    }

    public List<? extends Node> getAsCopiedList() throws IllegalAccessException {
        return Arrays.asList(getArray());
    }
}
