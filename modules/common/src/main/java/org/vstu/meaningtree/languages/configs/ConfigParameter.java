package org.vstu.meaningtree.languages.configs;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class ConfigParameter {
    protected String _id;
    protected ConfigScope _scope;

    protected ConfigValue _defaultValue;
    protected ConfigValue _value;

    protected ConfigParameter(String id, ConfigValue value, ConfigScope scope) {
        _id = id;
        _scope = scope;
        _value = value;
        _defaultValue = value;
    }

    protected ConfigParameter(String id, ConfigValue value) {
        this(id, value, ConfigScope.ANY);
    }

    public static Predicate<ConfigParameter> forScopes(ConfigScope... scopes) {
        return cfg -> {
            if (cfg instanceof ConfigParameter scopedParameter) {
                return scopedParameter.inAnyScope(scopes);
            }
            return true;
        };
    }

    public ConfigValue getValue() {
        return _value;
    }

    public boolean asBoolean() {
        return _value.asBoolean();
    }

    public int asInt() {
        return _value.asInt();
    }

    public long asLong() {
        return _value.asLong();
    }

    public double asDouble() {
        return _value.asDouble();
    }

    public String asString() {
        return _value.asString();
    }

    public List<?> asList() {
        return _value.asList();
    }

    public Map<?, ?> asMap() {
        return _value.asMap();
    }

    public void reset() {
        _value = _defaultValue;
    }

    public String getId() {
        return _id;
    }

    public boolean equalsValue(Object object) {
        return _value.getValue().equals(object);
    }

    public ConfigParameter withValue(Object value) {
        var clone = new ConfigParameter(_id, _value.change(value), _scope);
        clone._defaultValue =  _defaultValue;
        return clone;
    }

    public boolean isSetToDefault() {
        return _defaultValue.equals(_value);
    }

    public boolean inScope(ConfigScope scope) {
        return _scope == ConfigScope.ANY || _scope == scope;
    }

    public boolean inAnyScope(ConfigScope ...scopes) {
        return Arrays.stream(scopes).anyMatch(this::inScope);
    }

    @Override
    public String toString() {
        return _id;
    }
}
