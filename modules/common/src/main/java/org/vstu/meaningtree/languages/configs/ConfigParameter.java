package org.vstu.meaningtree.languages.configs;

public abstract class ConfigParameter<T> {
    protected T _value;

    protected ConfigParameter(T value) {
        _value = value;
    }

    public T getValue() { return _value; }
}
