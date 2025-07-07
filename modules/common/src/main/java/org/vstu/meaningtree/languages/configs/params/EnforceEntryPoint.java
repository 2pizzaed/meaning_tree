package org.vstu.meaningtree.languages.configs.params;

import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.parser.BooleanParser;

import java.util.Optional;

public class EnforceEntryPoint extends ConfigScopedParameter<Boolean> {
    public EnforceEntryPoint(Boolean value, ConfigScope scope) {
        super(value, scope);
    }

    public EnforceEntryPoint(Boolean value) {
        this(value, ConfigScope.ANY);
    }

    public static Optional<Boolean> parse(String value) { return BooleanParser.parse(value); }
}
