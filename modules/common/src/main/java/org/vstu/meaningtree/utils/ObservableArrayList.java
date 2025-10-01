package org.vstu.meaningtree.utils;

import org.apache.commons.lang3.tuple.Triple;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ObservableArrayList<T> extends ArrayList<T> {
    private List<Hook<Triple<Integer, T, ListModificationType>>> modificationHooks = new ArrayList<>();

    public ObservableArrayList() {
        super();
    }

    public ObservableArrayList(Collection<? extends T> c) {
        super(c);
    }

    protected void triggerHook(int index, T element, ListModificationType modificationType) {
        for (var hook : modificationHooks) {
            var triplet = Triple.of(index, element, modificationType);
            if (hook.isTriggered(triplet)) {
                hook.accept(triplet);
            }
        }
    }

    public void registerHook(Hook<Triple<Integer, T, ListModificationType>> hook) {
        modificationHooks.add(hook);
    }

    public void removeHook(Hook<Triple<Integer, T, ListModificationType>> hook) {
        modificationHooks.remove(hook);
    }

    @Override
    public void add(int index, T element) {
        super.add(index, element);
        triggerHook(index, element, ListModificationType.ADD);
    }

    @Override
    public boolean add(T t) {
        var val = super.add(t);
        triggerHook(size(), t, ListModificationType.ADD);
        return val;
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean val = super.addAll(c);
        int i = 0;
        for (var element : c) {
            triggerHook(size() + i, element, ListModificationType.ADD);
            i++;
        }
        return val;
    }

    @Override
    public boolean addAll(int index, Collection<? extends T> c) {
        boolean val = super.addAll(index, c);
        int i = 0;
        for (var element : c) {
            triggerHook(size() + i, element, ListModificationType.ADD);
            i++;
        }
        return val;
    }

    @Override
    public boolean remove(Object o) {
        int index = indexOf(o);
        if (index == -1) {
            return false;
        }
        T element = get(index);
        boolean val = super.remove(o);
        triggerHook(index, element, ListModificationType.REMOVE);
        return val;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return super.removeAll(c);
    }

    @Override
    public T remove(int index) {
        T element = super.remove(index);
        triggerHook(index, element, ListModificationType.REMOVE);
        return element;
    }
}
