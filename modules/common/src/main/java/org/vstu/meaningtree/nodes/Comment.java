package org.vstu.meaningtree.nodes;

import java.util.Objects;

public class Comment extends Node {
    protected final String _content;
    // Комментарии хранятся аналогично строковым литералам в неэкранированном виде

    private Comment(String content) {
        _content = content;
    }

    public static Comment fromUnescaped(String codePiece) {
        return new Comment(codePiece);
    }

    public String getUnescapedContent() {
        return _content;
    }

    public boolean isMultiline() {
        return getUnescapedContent().contains("\n");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Comment comment = (Comment) o;
        return Objects.equals(_content, comment._content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _content);
    }
}
