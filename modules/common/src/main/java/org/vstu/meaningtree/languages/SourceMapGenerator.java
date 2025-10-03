package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.SourceMap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceMapGenerator {
    /***
     * Данный класс необходим для получения из Viewer не только строки исходного кода, но также разметки этого кода
     * Разметка включает в себя список айди узлов, а также их байтовая позиция в полученном коде
     */
    protected LanguageTranslator translator;

    // Начальный и конечный маркеры для ID
    private static final String START_TAG = "\u2060AST_START_"; // \u2060 = word joiner (невидимый)
    private static final String END_TAG = "\u2060AST_END";

    private static final BiFunction<Node, String, String> watermarkingHook = (node, string) -> {
        long id = node.getId(); // допустим, у Node есть getId()
        var stringBuffer = new StringBuilder();
        stringBuffer.append(START_TAG);
        stringBuffer.append(id);
        stringBuffer.append(END_TAG);
        stringBuffer.append(string);
        stringBuffer.append(START_TAG);
        stringBuffer.append('/');
        stringBuffer.append(id);
        stringBuffer.append(END_TAG);
        return stringBuffer.toString();
    };

    public SourceMapGenerator(LanguageTranslator translator) {
        this.translator = translator.clone();
        this.translator._viewer.registerPostprocessFunction(watermarkingHook);
    }

    public SourceMap process(MeaningTree meaningTree) {
        String code = translator.getCode(meaningTree);
        return buildSourceMap(meaningTree, code);
    }

    public SourceMap process(Node root) {
        String code = translator.getCode(root);
        return buildSourceMap(root, code);
    }

    /**
     * Убирает watermark-теги и строит карту позиций.
     * @param root узел или дерево, для которой строится карта
     * @param instrumentedCode текст с тегами
     * @return SourceMap с байтовыми смещениями
     */
    private SourceMap buildSourceMap(NodeIterable root, String instrumentedCode) {
        Map<Long, Pair<Integer, Integer>> result = new HashMap<>();

        Pattern tagPattern = Pattern.compile(START_TAG + "(/?)(\\d+)" + END_TAG);
        Matcher matcher = tagPattern.matcher(instrumentedCode);

        StringBuilder cleanCode = new StringBuilder();
        int lastEnd = 0;

        // стек для открытых узлов
        Deque<Long> stack = new ArrayDeque<>();
        // начало узла (в байтах)
        Map<Long, Integer> startOffsets = new HashMap<>();

        while (matcher.find()) {
            // добавляем кусок до тэга
            cleanCode.append(instrumentedCode, lastEnd, matcher.start());
            lastEnd = matcher.end();

            boolean isClose = matcher.group(1).equals("/");
            long nodeId = Long.parseLong(matcher.group(2));

            if (!isClose) {
                // начало узла в байтах
                int byteOffset = utf8Length(cleanCode);
                startOffsets.put(nodeId, byteOffset);
                stack.push(nodeId);
            } else {
                // конец узла в байтах
                Long openId = stack.pop();
                int start = startOffsets.get(openId);
                int end = utf8Length(cleanCode);
                result.put(openId, Pair.of(start, end - start));
            }
        }

        cleanCode.append(instrumentedCode.substring(lastEnd));

        return new SourceMap(cleanCode.toString(), root, result, translator.getLanguageName());
    }

    /**
     * Возвращает количество байтов в UTF-8 для текущего буфера
     */
    private static int utf8Length(CharSequence seq) {
        return seq.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
