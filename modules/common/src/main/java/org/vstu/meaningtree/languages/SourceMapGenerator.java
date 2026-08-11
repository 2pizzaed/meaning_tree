package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.analysis.CyclomaticComplexityAnalyzer;
import org.vstu.meaningtree.utils.hooks.*;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceMapGenerator {
    /***
     * Данный класс необходим для получения из Viewer не только строки исходного кода, но также разметки этого кода
     * Разметка включает в себя список ID узлов, а также их байтовая позиция в полученном коде
     */
    protected LanguageTranslator translator;
    protected ScopeTable globalScope;
    private final CyclomaticComplexityAnalyzer cyclomaticComplexityAnalyzer = new CyclomaticComplexityAnalyzer();

    // Начальный и конечный маркеры для ID
    private static final String START_TAG = "\u2060AST_START_"; // \u2060 = word joiner (невидимый)
    private static final String END_TAG = "\u2060AST_END";

    /**
     * Обёртывает каждый отрисованный узел невидимыми маркерами с его id, чтобы затем
     * восстановить байтовые границы узлов в готовом коде.
     * <p>
     * Состояние (какие узлы уже размечены) живёт в экземпляре, а не в статике: экземпляр
     * создаётся на один прогон, поэтому два генератора не мешают друг другу.
     */
    private static final class Watermarker implements Interceptor<Node, String> {
        private final Set<Long> watermarked = new HashSet<>();

        @Override
        public String intercept(Node node, String rendered, HookContext context) {
            long id = node.hasLabel(Label.REMAPPED) ? node.getLabel(Label.REMAPPED).attributeAsLong() : node.getId();
            if (!watermarked.add(id)) {
                return rendered;
            }
            return START_TAG + id + END_TAG + rendered + START_TAG + '/' + id + END_TAG;
        }
    }

    public SourceMapGenerator(LanguageTranslator translator) {
        this.translator = translator.clone();
    }

    public SourceMap process(MeaningTree meaningTree) {
        String code = instrumentedCode(() -> translator.getCode(meaningTree));
        globalScope = translator.getLatestScopeTable();
        return buildSourceMap(meaningTree, code);
    }

    public SourceMap process(Node root) {
        String code = instrumentedCode(() -> translator.getCode(root));
        globalScope = translator.getLatestScopeTable();
        return buildSourceMap(root, code);
    }

    /**
     * Выполняет генерацию кода с включённой разметкой узлов.
     * <p>
     * Разметка регистрируется как хук прогона с порядком {@link HookOrder#LATE}: она обязана
     * быть самым внешним слоем, иначе маркеры окажутся внутри результата работы языковых
     * хуков и границы узлов посчитаются неверно.
     */
    private String instrumentedCode(Supplier<String> generation) {
        try (HookScope scope = translator._viewer.hooks().openScope()) {
            scope.intercept(HookPhase.AFTER_NODE_RENDER, Node.class, HookOrder.LATE, new Watermarker());
            return generation.get();
        }
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

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("cyclomatic", cyclomaticComplexityAnalyzer.analyze(root));

        String projectRootPath = translator.getProjectRootPath().map(path -> path.toString()).orElse(null);
        String projectFileRelPath = translator.getCurrentFileRelPath().map(path -> path.toString()).orElse(null);

        return new SourceMap(cleanCode.toString(), root, result,
                globalScope,
                translator.getLanguageName(),
                metrics,
                projectRootPath,
                projectFileRelPath
        );
    }

    /**
     * Возвращает количество байтов в UTF-8 для текущего буфера
     */
    private static int utf8Length(CharSequence seq) {
        return seq.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
