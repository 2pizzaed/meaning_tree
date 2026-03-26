package org.vstu.meaningtree.languages.utils;

public class Tab {
    private int level;
    private int whitespaceCount;

    public Tab() {
        level = 0;
        whitespaceCount = 4;
    }

    public Tab(int level, int whitespaces) {
        this.level = level;
        whitespaceCount = whitespaces;
    }

    public Tab(int level) {
        whitespaceCount = 4;
        this.level = level;
    }

    public Tab up() {
        return new Tab(level+1, whitespaceCount);
    }

    public Tab down() {
        return new Tab(level-1, whitespaceCount);
    }

    public String concat(String other) {
        return toString().concat(other);
    }

    @Override
    public String toString() {
        if (level <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            for (int j = 0; j < whitespaceCount; j++) {
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
