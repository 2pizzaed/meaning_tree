package org.vstu.meaningtree.languages.configs.params;

import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.parser.BooleanParser;

import java.util.Optional;

public class TargetLanguageVersion extends ConfigScopedParameter<String> {
    public TargetLanguageVersion(String value, ConfigScope scope) {
        super(value, scope);
    }

    public TargetLanguageVersion(String value) {
        super(value, ConfigScope.TRANSLATOR);
    }

    public static Optional<Boolean> parse(String value) { return BooleanParser.parse(value); }
}
