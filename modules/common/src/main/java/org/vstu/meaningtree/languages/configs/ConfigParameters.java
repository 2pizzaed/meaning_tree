package org.vstu.meaningtree.languages.configs;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.languages.LanguageTranslator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ConfigParameters {
    protected final static Map<String, ConfigParameter> builtinRegistry = new HashMap<>();
    protected final static Map<Class<? extends LanguageTranslator>, Map<String, ConfigParameter>> langRegistry = new HashMap<>();

    public static final ConfigParameter translationUnitMode = register("translationUnitMode",
            ConfigValue.ofPossible(String.class, "full", List.of("expression", "simple", "procedural", "full")),
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
    /**
     * Отключает конвейер анализа, выполняемый после построения дерева
     * ({@code SymbolResolver}, {@code ExpressionValueEvaluator}, {@code LoopIterationAnalyzer}).
     * <p>
     * При {@code true} соответствующий хук не регистрируется вовсе, то есть проходы не
     * просто пропускаются на каждом вызове, а отсутствуют. Результаты анализа
     * (выведенные типы полей, оценки значений и числа итераций циклов) при этом на дереве
     * не появятся — включать стоит только тогда, когда они заведомо не нужны.
     */
    public static final ConfigParameter skipOptimizations = register("skipOptimizations",
            new ConfigValue(false),
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
            return Objects.requireNonNull(configVal, "`%s` config key wasn't found".formatted(id));
        }
    }

    public static boolean exists(Class<? extends LanguageTranslator> translator, String id) {
        var registry = langRegistry.getOrDefault(translator, null);
        if (registry == null) {
            return builtinRegistry.containsKey(id);
        } else {
            return registry.containsKey(id) || builtinRegistry.containsKey(id);
        }
    }

    protected static ConfigParameter get(String id) {
        var configVal = builtinRegistry.getOrDefault(id, null);
        return Objects.requireNonNull(configVal, "`%s` config key wasn't found".formatted(id));
    }

    public static ConfigParameter registerIfNotExists(LanguageTranslator translator, String id, @NotNull ConfigValue defaultValue, ConfigScope scope) {
        if (builtinRegistry.containsKey(id) || langRegistry.getOrDefault(translator.getClass(), Map.of()).containsKey(id)) {
            return get(translator.getClass(), id);
        }
        if (!langRegistry.containsKey(translator.getClass())) {
            langRegistry.put(translator.getClass(), new HashMap<>());
        }
        var param = makeParam(id, defaultValue, scope);
        langRegistry.get(translator.getClass()).put(id, param);
        return param;
    }

    public static ConfigParameter registerReadonlyIfNotExists(LanguageTranslator translator, String id, @NotNull ConfigValue defaultValue, ConfigScope scope) {
        var param = registerIfNotExists(translator, id, defaultValue, ConfigScope.ANY);
        param.readOnly = true;
        return param;
    }

    protected static ConfigParameter makeParam(String id, ConfigValue defaultValue, ConfigScope scope) {
        return new ConfigParameter(
                id, defaultValue,
                scope
        );
    }

    protected static ConfigParameter register(String id, @NotNull ConfigValue defaultValue, @NotNull ConfigScope scope) {
        if (builtinRegistry.containsKey(id)) {
            throw new IllegalArgumentException("Config with id " + id + " already exists");
        }
        var param = makeParam(id, defaultValue, scope);
        builtinRegistry.put(id, param);
        return param;
    }

    protected static ConfigParameter registerReadonly(String id, @NotNull ConfigValue defaultValue, @NotNull ConfigScope scope) {
        var param = register(id, defaultValue, scope);
        param.readOnly = true;
        return param;
    }

    public static Config defaultConfig() {
        return new Config(builtinRegistry.values());
    }
}
