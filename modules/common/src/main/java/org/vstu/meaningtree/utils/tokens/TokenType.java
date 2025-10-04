package org.vstu.meaningtree.utils.tokens;

public enum TokenType {
    /**
     * Не нужно полагаться в этом перечислении на тип токена - OPERATOR. В данном случае он покрывает не все виды операторов
     * Вместо этого стоит выполнять проверку на тип OperatorToken
     */
    OPERATOR,

    CONST,
    CALLABLE_IDENTIFIER,
    IDENTIFIER,
    KEYWORD,
    CAST,
    COMMENT,

    OPENING_BRACE,
    CLOSING_BRACE,
    SUBSCRIPT_OPENING_BRACE,
    SUBSCRIPT_CLOSING_BRACE,
    CALL_OPENING_BRACE,
    CALL_CLOSING_BRACE,
    COMPOUND_OPENING_BRACE,
    COMPOUND_CLOSING_BRACE,
    INITIALIZER_LIST_OPENING_BRACE,
    INITIALIZER_LIST_CLOSING_BRACE,

    STATEMENT_TOKEN,
    SEPARATOR,
    COMMA,
    UNKNOWN;

    public boolean isBrace() {
        return toString().endsWith("BRACE");
    }

    public boolean isOpeningBrace() {
        return toString().endsWith("OPENING_BRACE");
    }

    public boolean isClosingBrace() {
        return toString().endsWith("CLOSING_BRACE");
    }

    public boolean isOnlyGroupingBrace() {
        return this.equals(OPENING_BRACE) || this.equals(CLOSING_BRACE);
    }
}
