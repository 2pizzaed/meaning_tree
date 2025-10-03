package org.vstu.meaningtree.utils.tokens;

public class Whitespace extends PseudoToken {
    public Whitespace(String value, TokenType type) {
        super(value, type);
    }

    public boolean hasNewLines() {
        return value.contains("\n");
    }
}
