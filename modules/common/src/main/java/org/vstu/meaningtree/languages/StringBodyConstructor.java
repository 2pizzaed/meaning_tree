package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.nodes.Node;

import java.util.ArrayList;
import java.util.List;

public class StringBodyConstructor extends BodyConstructor {
    private List<String> stringBuffer = new ArrayList<>();
    private int indentCount = 0;
    private String indentString = " ";

    public StringBodyConstructor(TranslatorContext ctx, boolean newScope) {
        super(ctx, newScope);
        if (!(ctx.owner instanceof LanguageViewer)) {
            throw new IllegalStateException("Cannot create StringBodyConstructor when owner is not a LanguageViewer");
        }
    }

    public StringBodyConstructor indent(int count) {
        return indent(count, "    ");
    }

    public String withIndent(String str) {
        if (indentCount <= 0 || str.isEmpty()) {
            return str;
        }

        String indent = indentString.repeat(indentCount);
        String[] lines = str.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                lines[i] = indent + lines[i];
            }
        }
        return String.join(System.lineSeparator(), lines);
    }

    public StringBodyConstructor indent(int count, String indent) {
        this.indentString = indent;
        this.indentCount = count;
        return this;
    }

    private LanguageViewer getViewer() {
        return (LanguageViewer) ctx.owner;
    }

    public List<String> stringBuffer() {
        return List.copyOf(stringBuffer);
    }

    public void appendString(String string) {
        stringBuffer.add(string);
    }

    public void appendStringWithIndent(String string) {
        stringBuffer.add(withIndent(string));
    }

    public String getStringAt(int index) {
        return stringBuffer.get(index);
    }

    public void substituteString(int index, String string) {
        stringBuffer.set(index, string);
    }

    public void substituteStringWithIndent(int index, String string) {
        stringBuffer.set(index, withIndent(string));
    }

    private int normalizeStringBufferIndex(int index) {
        return Math.max(0, Math.min(index, stringBuffer.size()));
    }

    @Override
    protected void afterInsert(int index) {
        if (iterator != null && index <= iterator.index + 1) {
            iterator.index++;
        }
    }

    @Override
    protected void afterRemove(int index) {
        if (iterator != null && index <= iterator.index) {
            iterator.index--;
        }
    }

    public StringBodyConstructor add(Node node) {
        int before = nodes.size();
        super.add(node);
        if (nodes.size() > before) {
            stringBuffer.add(withIndent(getViewer().toString(node)));
        }
        return this;
    }

    public StringBodyConstructor insert(int index, Node node) {
        int before = nodes.size();
        super.insert(index, node);
        if (nodes.size() > before) {
            stringBuffer.add(normalizeStringBufferIndex(index), withIndent(getViewer().toString(node)));
        }
        return this;
    }

    public StringBodyConstructor substitute(int index, Node node) {
        super.substitute(index, node);
        // substitute применился, только если это тот же объект: reject() оставляет старый узел на месте
        if (nodes.get(index) == node) {
            stringBuffer.set(index, withIndent(getViewer().toString(node)));
        }
        return this;
    }

    public StringBodyConstructor insertBeforeLast(int index, Node node) {
        int insertIndex = currentNodeIndex() + index;
        int before = nodes.size();
        super.insert(insertIndex, node);
        if (nodes.size() > before) {
            stringBuffer.add(
                    normalizeStringBufferIndex(insertIndex),
                    withIndent(getViewer().toString(node))
            );
        }
        return this;
    }

    public static StringBodyConstructor createFrom(TranslatorContext ctx, boolean newScope, List<Node> nodes) {
        var result = new StringBodyConstructor(ctx, newScope);
        result.nodes = new ArrayList<>(nodes);
        return result;
    }

    @Override
    public BodyConstructor remove(int index) {
        super.remove(index);
        stringBuffer.remove(index);
        return this;
    }
}
