package org.vstu.meaningtree.languages.configs;

import java.util.Arrays;
import java.util.function.Predicate;

public abstract class ConfigScopedParameter<T> extends ConfigParameter<T> {
    protected ConfigScope _scope;

    public ConfigScopedParameter(T value) {
        this(value, ConfigScope.ANY);
    }

    public static Predicate<ConfigParameter<?>> forScopes(ConfigScope... scopes) {
        return cfg -> {
            if (cfg instanceof ConfigScopedParameter<?> scopedParameter) {
                return scopedParameter.inAnyScope(scopes);
            }
            return true;
        };
    }

    public ConfigScopedParameter(T value, ConfigScope scope) {
        super(value);
        _scope = scope;
    }

    public boolean inScope(ConfigScope scope) {
        return _scope == ConfigScope.ANY || _scope == scope;
    }

    public boolean inAnyScope(ConfigScope ...scopes) {
        return Arrays.stream(scopes).anyMatch(this::inScope);
    }
}
