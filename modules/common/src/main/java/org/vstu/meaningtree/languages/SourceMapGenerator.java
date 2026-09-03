package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.analysis.AnalysisPipeline;
import org.vstu.meaningtree.utils.analysis.CyclomaticComplexityAnalyzer;
import org.vstu.meaningtree.utils.analysis.ScopeTableBuilder;
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

    /**
     * Транслятор языка, из которого получено дерево, либо {@code null}, если он вызывающему
     * неизвестен: дерево могло прийти из JSON или быть собрано программно. Нужен только для
     * {@code originScopeTable} — построить её правилами целевого языка нельзя, это и был прежний
     * дефект.
     */
    @Nullable
    protected LanguageTranslator originTranslator;

    protected ScopeTable renderScope;

    @Nullable
    protected ScopeTable originScope;
    private final CyclomaticComplexityAnalyzer cyclomaticComplexityAnalyzer = new CyclomaticComplexityAnalyzer();

    // Начальный и конечный маркеры для ID
    private static final String START_TAG = "\u2060AST_START_"; // \u2060 = word joiner (невидимый)
    private static final String END_TAG = "\u2060AST_END";

    /** Разметка узла собственным рендерингом. */
    private static final char OWN_MARK = 'N';
    /** Разметка узла через {@link Label#REMAPPED}: отрисован не он сам, а его замена. */
    private static final char REMAPPED_MARK = 'R';

    /**
     * Обёртывает каждый отрисованный узел невидимыми маркерами с его id, чтобы затем
     * восстановить байтовые границы узлов в готовом коде.
     * <p>
     * Узлы, созданные во время отрисовки, несут {@link Label#REMAPPED} и размечаются id своего
     * прообраза. Такая разметка помечается отдельным символом: прообраз может отрисовываться и
     * сам (например, вызов {@code print(x)} снаружи созданного при отрисовке идентификатора
     * {@code print}), и тогда его собственные границы точнее — см. {@link #buildSourceMap}.
     * <p>
     * Состояние (какие узлы уже размечены) живёт в экземпляре, а не в статике: экземпляр
     * создаётся на один прогон, поэтому два генератора не мешают друг другу.
     */
    private static final class Watermarker implements Interceptor<Node, String> {
        private final Set<Long> watermarked = new HashSet<>();

        @Override
        public String intercept(Node node, String rendered, HookContext context) {
            boolean isRemapped = node.hasLabel(Label.REMAPPED);
            long id = isRemapped ? node.getLabel(Label.REMAPPED).attributeAsLong() : node.getId();
            // Повторно отрисованный узел размечается один раз; замены не считаем повтором,
            // иначе первая же из них закрыла бы прообразу собственную разметку
            if (!isRemapped && !watermarked.add(id)) {
                return rendered;
            }
            char mark = isRemapped ? REMAPPED_MARK : OWN_MARK;
            return START_TAG + mark + id + END_TAG + rendered + START_TAG + '/' + mark + id + END_TAG;
        }
    }

    public SourceMapGenerator(LanguageTranslator translator) {
        this(translator, null);
    }

    /**
     * @param originTranslator транслятор языка-источника дерева. Без него
     *                         {@code SourceMap.originScopeTable()} остаётся пустой: правила
     *                         видимости и перегрузок у языка-источника свои, и подставить вместо
     *                         них целевые — значит описать программу, которой нет.
     */
    public SourceMapGenerator(LanguageTranslator translator, @Nullable LanguageTranslator originTranslator) {
        this.originTranslator = originTranslator;
        this.translator = translator.clone();
        // clone() переносит только конфигурацию, а привязка к файлу проекта должна попасть
        // в готовую карту (см. buildSourceMap), поэтому копируем её отдельно
        if (translator.hasSourceContext()) {
            this.translator.withSourceContext(
                    translator.getProjectRootPath().orElseThrow(),
                    translator.getCurrentFileRelPath().orElseThrow()
            );
        }
    }

    public SourceMap process(MeaningTree meaningTree) {
        return process(meaningTree, meaningTree);
    }

    public SourceMap process(Node root) {
        return process(root, new MeaningTree(root));
    }

    /**
     * Таблица источника строится до генерации, а целевая — после: оптимизации целевого языка
     * дорабатывают дерево, и после них это уже другая программа. Порядок здесь и есть разница
     * между двумя таблицами.
     */
    private SourceMap process(NodeIterable root, MeaningTree tree) {
        originScope = originTranslator == null ? null : analyzedScope(tree, originTranslator);
        String code = instrumentedCode(() -> root instanceof MeaningTree
                ? translator.getCode((MeaningTree) root)
                : translator.getCode((Node) root));
        renderScope = translator.isSkipOptimizations()
                ? translator.getLatestScopeTable()
                : analyzedScope(tree, translator);
        return buildSourceMap(root, code);
    }

    /**
     * Строит {@link ScopeTable} для дерева правилами указанного языка и дополняет её
     * метаданными {@link AnalysisPipeline} (перегрузки, преобразования типов и т.д.).
     * <p>
     * Правила берутся у того транслятора, чью сторону перевода таблица описывает: границы
     * областей и семантику перегрузок нельзя подставить от другого языка, иначе таблица опишет
     * программу, которой нет. Резолвинг импортов здесь не выполняется — это единственная стадия
     * с обходом файловой системы, и карте кода она ничего не добавляет.
     */
    private ScopeTable analyzedScope(MeaningTree tree, LanguageTranslator rules) {
        ScopeTable scope = ScopeTableBuilder.build(tree, rules.getScopePolicy(), rules.getAssignmentBinding());
        new AnalysisPipeline(tree, scope, rules).run(false);
        return scope;
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
        // Собственная разметка узла точнее разметки его замен, поэтому копим их раздельно
        Map<Long, Pair<Integer, Integer>> ownPositions = new HashMap<>();
        Map<Long, Pair<Integer, Integer>> remappedPositions = new HashMap<>();

        Pattern tagPattern = Pattern.compile(START_TAG + "(/?)([NR])(\\d+)" + END_TAG);
        Matcher matcher = tagPattern.matcher(instrumentedCode);

        StringBuilder cleanCode = new StringBuilder();
        int lastEnd = 0;

        // стек открытых узлов: id, начало в байтах и вид разметки
        Deque<long[]> stack = new ArrayDeque<>();

        while (matcher.find()) {
            // добавляем кусок до тэга
            cleanCode.append(instrumentedCode, lastEnd, matcher.start());
            lastEnd = matcher.end();

            boolean isClose = matcher.group(1).equals("/");
            char mark = matcher.group(2).charAt(0);
            long nodeId = Long.parseLong(matcher.group(3));

            if (!isClose) {
                // начало узла в байтах
                stack.push(new long[] {nodeId, utf8Length(cleanCode), mark});
            } else {
                // конец узла в байтах
                long[] open = stack.pop();
                int start = (int) open[1];
                int length = utf8Length(cleanCode) - start;
                Map<Long, Pair<Integer, Integer>> target =
                        open[2] == OWN_MARK ? ownPositions : remappedPositions;
                // У замен побеждает самая внешняя: она покрывает весь отрисованный ими текст
                target.put(open[0], Pair.of(start, length));
            }
        }

        cleanCode.append(instrumentedCode.substring(lastEnd));

        Map<Long, Pair<Integer, Integer>> result = new HashMap<>(remappedPositions);
        result.putAll(ownPositions);

        Map<String, Number> metrics = new LinkedHashMap<>();
        metrics.put("cyclomatic", cyclomaticComplexityAnalyzer.analyze(root));

        String projectRootPath = translator.getProjectRootPath().map(path -> path.toString()).orElse(null);
        String projectFileRelPath = translator.getCurrentFileRelPath().map(path -> path.toString()).orElse(null);

        return new SourceMap(cleanCode.toString(), root, result,
                renderScope,
                originScope,
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
