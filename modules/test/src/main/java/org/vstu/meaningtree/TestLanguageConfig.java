package org.vstu.meaningtree;

import com.google.gson.JsonObject;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigBuilder;

import java.util.Map;
import java.util.function.Function;

public record TestLanguageConfig(
        Function<Config, LanguageTranslator> translatorFactory,
        Class<? extends LanguageTranslator> translatorClass,
        String languageName,
        boolean indentSensitive,
        Map<String, Object> defaultConfiguration
) {
    public LanguageTranslator createTranslator(JsonObject rawConfiguration) {
        Config defaults = new ConfigBuilder().fromRawMap(translatorClass, defaultConfiguration).toConfig();
        Config overrides = new ConfigBuilder().fromJson(translatorClass, rawConfiguration).toConfig();
        Config configuration = defaults.merge(overrides);
        return translatorFactory.apply(configuration);
    }

}
