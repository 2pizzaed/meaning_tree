package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.nodes.enums.CommentStyle;

import java.util.Objects;

public class Comment extends Node {
    protected final String _content;
    // Комментарии хранятся аналогично строковым литералам в неэкранированном виде
    protected final CommentStyle _style;

    private Comment(String content, CommentStyle style) {
        _content = content;
        _style = Objects.requireNonNull(style);
    }

    public static Comment fromUnescaped(String codePiece, CommentStyle style) {
        return new Comment(codePiece, style);
    }

    public String getUnescapedContent() {
        return _content;
    }

    public CommentStyle getStyle() {
        return _style;
    }

    public boolean hasNewline() {
        return getUnescapedContent().contains("\n");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        Comment comment = (Comment) o;
        return Objects.equals(_content, comment._content) && _style == comment._style;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _content, _style);
    }
}
