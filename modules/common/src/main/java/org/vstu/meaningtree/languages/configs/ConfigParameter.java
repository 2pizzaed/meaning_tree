package org.vstu.meaningtree.languages.configs;

import java.util.HashMap;
import java.util.Map;

public abstract class ConfigParameter<T> {
    protected T _value;
    Map<Class<? extends ConfigScopedParameter>, Object> conflictsWith = new HashMap<>();

    public void setConflictingParameter(Class<? extends ConfigScopedParameter> param, Object object) {
        conflictsWith.put(param, object);
    }

    public void setConflictingParameter(Class<? extends ConfigScopedParameter> param) {
        conflictsWith.put(param, null);
    }

    protected ConfigParameter(T value) {
        _value = value;
    }

    public T getValue() { return _value; }

    @Override
    public String toString() {
        return "%s -> %s".formatted(this.getClass().getSimpleName(), _value.toString());
    }
}
