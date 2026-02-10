package org.vstu.meaningtree.languages.configs;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.exceptions.UnsupportedConfigParameterException;
import org.vstu.meaningtree.languages.LanguageTranslator;

import java.util.LinkedList;

public class ConfigBuilder {
    private final LinkedList<ConfigParameter> params;

    public ConfigBuilder() {
        this.params = new LinkedList<>();
    }

    public ConfigBuilder fromJson(@Nullable Class<? extends LanguageTranslator> context, JsonObject json) {
        json.entrySet().forEach(entry -> {
            ConfigParameter param;
            if (context != null) {
                param = ConfigParameters.get(context, entry.getKey());
            } else {
                param = ConfigParameters.get(entry.getKey());
            }

            if (param == null) {
                throw new UnsupportedConfigParameterException("Config parameter %s wasn't not found".formatted(entry.getKey()));
            }
            add(param.withValue(ConfigValue.unwrapJson(entry.getValue())));
        });
        return this;
    }

    public ConfigBuilder add(ConfigParameter parameter) {
        this.params.addFirst(parameter);
        return this;
    }

    public ConfigBuilder add(Class<? extends LanguageTranslator> context, String id, Object value) {
        var param = ConfigParameters.get(context, id);
        this.params.addFirst(param.withValue(value));
        return this;
    }

    public Config toConfig() {
        return new Config(this.params);
    }

}
