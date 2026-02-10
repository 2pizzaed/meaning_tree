package org.vstu.meaningtree.languages.configs;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.languages.LanguageTranslator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigParameters {
    protected final static Map<String, ConfigParameter> builtinRegistry = new HashMap<>();
    protected final static Map<Class<? extends LanguageTranslator>, Map<String, ConfigParameter>> langRegistry = new HashMap<>();

    public static final ConfigParameter translationUnitMode = register("translationUnitMode",
            ConfigValue.ofPossible(String.class, "full", List.of("expression", "short", "full")),
            ConfigScope.ANY
    );
    public static final ConfigParameter skipErrors = register("skipErrors",
            new ConfigValue(false),
            ConfigScope.ANY
    );
    public static final ConfigParameter targetLanguageVersion = register("targetLanguageVersion",
            ConfigValue.nullable(String.class),
            ConfigScope.ANY
    );
    public static final ConfigParameter bytePositionAnnotations = register("bytePositionAnnotations",
            new ConfigValue(true),
            ConfigScope.ANY
    );

    public static ConfigParameter get(Class<? extends LanguageTranslator> translator, String id) {
        var registry = langRegistry.getOrDefault(translator, null);
        if (registry == null) {
            return get(id);
        } else {
            var configVal = registry.getOrDefault(id, null);
            if (configVal == null) {
                return get(id);
            }
            return configVal;
        }
    }

    protected static ConfigParameter get(String id) {
        return builtinRegistry.getOrDefault(id, null);
    }

    public static ConfigParameter register(LanguageTranslator translator, String id, @NotNull ConfigValue defaultValue, ConfigScope scope) {
        if (builtinRegistry.containsKey(id) || langRegistry.get(translator.getClass()).containsKey(id)) {
            throw new IllegalArgumentException("Config with id " + id + " already exists");
        }
        if (!langRegistry.containsKey(translator.getClass())) {
            langRegistry.put(translator.getClass(), new HashMap<>());
        }
        var param = makeParam(id, defaultValue, scope);
        langRegistry.get(translator.getClass()).put(id, param);
        return param;
    }

    protected static ConfigParameter makeParam(String id, ConfigValue defaultValue, ConfigScope scope) {
        return new ConfigParameter(
                id, defaultValue,
                scope
        );
    }

    protected static ConfigParameter register(String id, @NotNull ConfigValue defaultValue, @NotNull ConfigScope scope) {
        var param = makeParam(id, defaultValue, scope);
        builtinRegistry.put(id, param);
        return param;
    }

    public static Config defaultConfig() {
        return new Config(builtinRegistry.values());
    }
}
