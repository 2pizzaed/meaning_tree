package org.vstu.meaningtree.languages.configs.parser;

import org.vstu.meaningtree.exceptions.UnsupportedConfigParameterException;
import org.vstu.meaningtree.languages.configs.ConfigParameter;

import java.util.Optional;
import java.util.function.Function;

public record ConfigMapping<T>(
        String name,
        Function<String, Optional<T>> parser,
        Function<T, ConfigParameter<T>> constructor
) {
    public ConfigParameter<?> createParameter(String value) {
        return parser.apply(value)
                .map(constructor)
                .orElseThrow(() -> new UnsupportedConfigParameterException(
                        String.format("Wrong value '%s' for parameter '%s'", value, name)
                ));
    }

}
