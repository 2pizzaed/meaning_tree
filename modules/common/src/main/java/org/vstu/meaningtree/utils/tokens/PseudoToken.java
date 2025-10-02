package org.vstu.meaningtree.utils.tokens;

public class PseudoToken extends Token {
    protected Object attribute;

    public PseudoToken(String value, TokenType type) {
        super(value, type);
    }

    public void setAttribute(Object o) {
        this.attribute = o;
    }

    public Object getAttribute() {
        return this.attribute;
    }

    // Специальный расширяемый класс для токенов, которые несут служебную информацию
    // Например, для аннотирования кода
}
