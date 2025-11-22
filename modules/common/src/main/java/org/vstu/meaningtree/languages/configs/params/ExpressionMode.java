package org.vstu.meaningtree.languages.configs.params;

import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.parser.BooleanParser;

import java.util.Optional;

/**
 * Конфигурационный параметр, управляющий режимом вывода программы.
 * <p>
 * Если значение {@code true}, то при генерации кода будет выведено только одно выражение.
 * Если значение {@code false}, то будет выведена полная программа.
 * <p>
 */
public class ExpressionMode extends ConfigScopedParameter<Boolean> {
    public ExpressionMode(Boolean value, ConfigScope scope) {
        super(value, scope);
        if (value.booleanValue()) {
            setConflictingParameter(TranslationUnitMode.class);
        }
    }

    public ExpressionMode(Boolean value) {
        this(value, ConfigScope.TRANSLATOR);
    }

    public static Optional<Boolean> parse(String value) { return BooleanParser.parse(value); }

}
