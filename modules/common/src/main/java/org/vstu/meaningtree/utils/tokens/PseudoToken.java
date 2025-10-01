package org.vstu.meaningtree.utils.tokens;

public class PseudoToken extends Token {
    public PseudoToken(String value, TokenType type) {
        super(value, type);
    }

    // Специальный расширяемый класс для токенов, которые несут служебную информацию
    // Например, для аннотирования кода
}
