package org.vstu.meaningtree.languages.configs.params;

import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.parser.BooleanParser;

import java.util.Optional;

/**
 * Конфигурационный параметр, управляющий режимом вывода программы.
 * <p>
 * Если значение {@code true}, то при генерации кода будет выведен полный текст программы,
 * Если значение {@code false}, то может быть выведена только часть программы — например, одно выражение.
 * <p>
 */
public class TranslationUnitMode extends ConfigScopedParameter<Boolean> {
    public TranslationUnitMode(Boolean value, ConfigScope scope) {
        super(value, scope);
    }

    public TranslationUnitMode(Boolean value) {
        this(value, ConfigScope.ANY);
    }

    public static Optional<Boolean> parse(String value) { return BooleanParser.parse(value); }

}
