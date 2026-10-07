package org.vstu.meaningtree.utils;

import org.treesitter.TSNode;

import java.nio.charset.StandardCharsets;

public class TreeSitterUtils {
    public static String getCodePiece(String sourceCode, TSNode node) {
        byte[] code = sourceCode.getBytes(StandardCharsets.UTF_8);
        int start = node.getStartByte();
        int end = node.getEndByte();
        return new String(code, start, end - start);
    }

    /**
     * Число именованных детей узла без extra-узлов.
     * <p>
     * tree-sitter вставляет extra-узлы (комментарии, в Python ещё и {@code \}-продолжение строки)
     * дочерними в любое место дерева, и {@link TSNode#getNamedChildCount()}/
     * {@link TSNode#getNamedChild(int)} их возвращают: обход, ждущий выражения, получает
     * комментарий, а позиционное обращение сдвигается.
     */
    public static int getNamedChildCountWithoutExtras(TSNode node) {
        int count = 0;
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            if (!node.getNamedChild(i).isExtra()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Именованный ребёнок узла с номером {@code index} среди детей без extra-узлов
     * (см. {@link #getNamedChildCountWithoutExtras(TSNode)}). За пределами диапазона, как и
     * {@link TSNode#getNamedChild(int)}, возвращает нулевой узел ({@link TSNode#isNull()}).
     */
    public static TSNode getNamedChildWithoutExtras(TSNode node, int index) {
        int seen = 0;
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode child = node.getNamedChild(i);
            if (!child.isExtra() && seen++ == index) {
                return child;
            }
        }
        return node.getNamedChild(node.getNamedChildCount());
    }
}
