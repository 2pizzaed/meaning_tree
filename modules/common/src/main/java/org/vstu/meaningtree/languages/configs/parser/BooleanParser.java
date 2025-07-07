package org.vstu.meaningtree.languages.configs.parser;

import java.util.Optional;

public class BooleanParser {
    public static Optional<Boolean> parse(String value) {
        if (value == null) { return Optional.empty(); }

        var sanitized = value.trim().toLowerCase();

        if (sanitized.equals("false") || sanitized.equals("true")) {
            return Optional.of(Boolean.parseBoolean(sanitized));
        }

        return Optional.empty();
    }

}
