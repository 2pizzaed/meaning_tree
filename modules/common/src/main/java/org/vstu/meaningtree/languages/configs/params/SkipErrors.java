package org.vstu.meaningtree.languages.configs.params;

import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.parser.BooleanParser;

import java.util.Optional;

/**
 * Конфигурационный параметр, управляющий режимом обработки ошибок.
 * <p>
 * Если значение {@code true}, то при ошибках все равно попытаться построить дерево.
 * Если значение {@code false}, то при ошибках трансляция будет прервана.
 * <p>
 */
public class SkipErrors extends ConfigScopedParameter<Boolean> {
    public SkipErrors(Boolean value, ConfigScope scope) {
        super(value, scope);
    }

    public SkipErrors(Boolean value) {
        this(value, ConfigScope.ANY);
    }

    public static Optional<Boolean> parse(String value) { return BooleanParser.parse(value); }

}
