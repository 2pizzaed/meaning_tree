package org.vstu.meaningtree.languages.configs.parser;

import org.vstu.meaningtree.languages.configs.ConfigParameter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ConfigParser {
    private final Map<String, ConfigMapping<?>> configMappings = new HashMap<>();

    public ConfigParser(ConfigMapping<?> ... mappings) {
        for (var mapping : mappings) {
            configMappings.put(mapping.name(), mapping);
        }
    }

    public ConfigParser(Collection<ConfigMapping<?>> mappings) {
        for (var mapping : mappings) {
            configMappings.put(mapping.name(), mapping);
        }
    }

    public static ConfigParser fromParser(ConfigParser parser, ConfigMapping<?> ... mappings) {
        var list = new ArrayList<>(parser.configMappings.values());
        for (var mapping : mappings) {
            list.add(mapping);
        }
        return new ConfigParser(list);
    }

    public ConfigParameter<?> parse(String key, String value) {
        var mapping = configMappings.getOrDefault(key, null);

        if (mapping == null) {
            return null;
        }

        return mapping.createParameter(value);
    }

}
