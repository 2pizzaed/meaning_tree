package org.vstu.meaningtree.utils;

public interface Hook<T> {
    boolean isTriggered(T object);
    void accept(T object);
}
