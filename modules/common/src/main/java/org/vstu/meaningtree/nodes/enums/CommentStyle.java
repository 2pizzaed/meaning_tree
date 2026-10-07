package org.vstu.meaningtree.nodes.enums;

/**
 * Синтаксическая форма комментария. Не зависит от того, есть ли в тексте перевод строки:
 * {@code /* x *}{@code /} — {@link #BLOCK} без перевода строки.
 */
public enum CommentStyle {
    /**
     * Строчный: {@code //} в C/C++/Java, {@code #} в Python.
     */
    LINE,
    /**
     * Блочный: {@code /* *}{@code /} в C/C++/Java; строка {@code """..."""} отдельным оператором в Python.
     */
    BLOCK,
    /**
     * Документация: {@code /** *}{@code /} в C/C++/Java, docstring в Python. Всегда блочной формы.
     */
    DOCUMENTATION
}
