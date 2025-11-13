package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;

import java.util.Optional;

public abstract class TranslatorComponent {
    private Config config;
    protected TranslatorContext ctx;
    protected LanguageTranslator translator;

    public void setConfig(Config config) {
        this.config = config;
    }

    public TranslatorComponent(LanguageTranslator translator) {
        this.translator = translator;
        this.ctx = new TranslatorContext(this, translator);
    }

    protected <P, T extends ConfigScopedParameter<P>> Optional<P> getConfigParameter(Class<T> configClass) {
        return Optional.ofNullable(config).flatMap(config -> config.get(configClass));
    }
}
