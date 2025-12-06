package org.vstu.meaningtree.languages.configs.params;

import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.parser.BooleanParser;

import java.util.Optional;

public class CLanguageMode extends ConfigScopedParameter<Boolean> {
    public CLanguageMode(Boolean value, ConfigScope scope) {
        super(value, scope);
        setConflictingParameter(TargetLanguageVersion.class);
    }

    public CLanguageMode(Boolean value) {
        super(value, ConfigScope.VIEWER);
    }

    public static Optional<Boolean> parse(String value) { return BooleanParser.parse(value); }
}
