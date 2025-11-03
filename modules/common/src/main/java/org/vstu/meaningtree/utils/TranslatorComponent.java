package org.vstu.meaningtree.utils;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;

import java.util.Optional;

public abstract class TranslatorComponent {
    protected Config _config;

    public void setConfig(Config config) {
        _config = config;
    }

    protected <P, T extends ConfigScopedParameter<P>> Optional<P> getConfigParameter(Class<T> configClass) {
        return Optional.ofNullable(_config).flatMap(config -> config.get(configClass));
    }
}
