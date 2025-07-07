package org.vstu.meaningtree.languages.configs;

import java.util.LinkedList;

public class ConfigBuilder {
    private final LinkedList<ConfigParameter<?>> params;

    public ConfigBuilder() {
        this.params = new LinkedList<>();
    }

    public ConfigBuilder add(ConfigParameter<?> parameter) {
        this.params.addFirst(parameter);
        return this;
    }

    public Config toConfig() {
        return new Config(this.params.reversed());
    }

}
