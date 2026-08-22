package org.vstu.meaningtree.languages;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.languages.configs.ConfigParameters;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEvaluator;
import org.vstu.meaningtree.utils.analysis.loops.LoopIterationAnalyzer;
import org.vstu.meaningtree.utils.analysis.symbols.SymbolResolver;
import org.vstu.meaningtree.utils.hooks.HookHandle;
import org.vstu.meaningtree.utils.hooks.HookOrder;
import org.vstu.meaningtree.utils.hooks.HookPhase;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

abstract public class LanguageParser extends TranslatorComponent {

    private String _code = "";
    protected Map<int[], Object> _byteValueTags = new HashMap<>();

    protected TSParser _tsParser;
    protected TSLanguage _tsLanguage;
    private TSTree _tsTreeCache = null;

    /**
     * Обработчик узла tree-sitter вместе с типом узла, который он строит.
     * <p>
     * Тип объявляется в точке регистрации, а не выводится из сигнатуры метода: только так он
     * проверяется компилятором.
     */
    protected record HandlerEntry(Class<? extends Node> produces, Function<TSNode, Node> handler) {
        public HandlerEntry {
            Objects.requireNonNull(produces, "produces must not be null");
            Objects.requireNonNull(handler, "handler must not be null");
        }
    }

    private final Map<String, HandlerEntry> tsNodeHandlers = new LinkedHashMap<>();
    private final LoopIterationAnalyzer loopIterationAnalyzer = new LoopIterationAnalyzer();

    public LanguageParser(LanguageTranslator translator, TSLanguage language) {
        super(translator);
        _tsLanguage = language;
        _tsParser = new TSParser();
        _tsParser.setLanguage(language);
        registerAnalysisPasses();
    }

    /**
     * Регистрирует конвейер анализа, выполняемый после построения дерева.
     * <p>
     * Конвейер зарегистрирован одним хуком, а не тремя с разными {@link HookOrder}:
     * приоритет означал бы, что порядок — это политика, которую можно переназначить, а
     * здесь он жёсткая зависимость по данным (см. {@link #runAnalysisPipeline}). Три
     * прохода — это одна неделимая единица работы, и включается-выключается она целиком.
     * <p>
     * При {@link ConfigParameters#skipOptimizations} хук не регистрируется вовсе, поэтому
     * фаза остаётся пустой и {@code run} выходит из неё без единой аллокации.
     */
    private void registerAnalysisPasses() {
        if (translator.isSkipOptimizations()) {
            return;
        }
        hooks.intercept(HookPhase.AFTER_TREE_PARSE, (tree, value, context) -> {
            runAnalysisPipeline(value, context.scope());
            return value;
        });
    }

    /**
     * Проходы анализа в единственно допустимом порядке.
     * <p>
     * {@code SymbolResolver} дописывает типы полей, найденных по присваиваниям вида
     * {@code self.x = ...} в любом методе класса; этими типами пользуется
     * {@code ExpressionValueEvaluator}; вычисленные им оценки нужны
     * {@code LoopIterationAnalyzer}. Каждому следующему нужен <b>полный</b> результат
     * предыдущего по всему дереву, поэтому проходы нельзя ни переставить, ни слить в один
     * обход.
     * <p>
     * Вычислитель выражений создаётся один раз и передаётся дальше: иначе
     * {@code LoopIterationAnalyzer} заводит на то же дерево второй независимый экземпляр.
     */
    private void runAnalysisPipeline(MeaningTree tree, ScopeTable scope) {
        new SymbolResolver(tree, scope).resolve();
        ExpressionValueEvaluator evaluator = new ExpressionValueEvaluator(tree, scope);
        evaluator.analyze();
        loopIterationAnalyzer.analyze(tree, evaluator);
    }

    public String getCode() {
        return _code;
    }

    public void resetParserState() {
        _code = "";
        _byteValueTags.clear();
        _tsTreeCache = null;
        rollbackContext();
    }

    public void setCode(String code) {
        resetParserState();
        _code = code;
    }

    public TSTree getTSTree() {
        if (_tsTreeCache == null) {
            _tsTreeCache = _tsParser.parseString(null, _code);
        }
        return _tsTreeCache;
    }

    public TSNode getRootNode() {
        return getTSTree().getRootNode();
    }

    public abstract MeaningTree getMeaningTree(String code);

    public abstract MeaningTree getMeaningTree(TSNode node, String code);

    protected synchronized MeaningTree getMeaningTree(String code, Map<int[], Object> values) {
        _byteValueTags = values;
        return getMeaningTree(code);
    }

    protected void matchParserNodes(TSNode originNode, Node createdNode) {
        int start = originNode.getStartByte();
        int end = originNode.getEndByte();
        List<int[]> toDelete = new ArrayList<>();
        for (int[] indexes : _byteValueTags.keySet()) {
            if (indexes[0] >= start && indexes[1] <= end) {
                createdNode.setAssignedValueTag(_byteValueTags.get(indexes));
                toDelete.add(indexes);
            }
        }
        for (int[] indexes : toDelete) {
            _byteValueTags.remove(indexes);
        }
        if (getConfigParameter("bytePositionAnnotations").asBoolean()) {
            createdNode.setLabel(new Label(Label.BYTEPOS_ANNOTATED, new int[] {
                    start,
                    end - start}));
        }
    }

    protected List<String> lookupErrors(TSNode node) {
        ArrayList<String> result = new ArrayList<>();
        _lookupErrors(node, result);
        return result;
    }

    public String getCodePiece(TSNode node) {
        return TreeSitterUtils.getCodePiece(_code, node);
    }

    private void _lookupErrors(TSNode node, List<String> list) {
        if (node.isNull()) {
            return;
        }
        if (node.isError()) {
            list.add(getCodePiece(node));
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            _lookupErrors(node.getChild(i), list);
        }
    }

    /**
     * Единственная точка диспетчеризации разбора — через неё проходит каждый узел tree-sitter.
     * Поэтому именно здесь ведётся стек кадров ({@link TranslatorContext#callFrames()}):
     * {@code try/finally} гарантирует, что контекст не протечёт при исключении.
     * <p>
     * Тип узла известен <b>до</b> вызова handler'а — он объявлен при регистрации; сам
     * построенный узел появляется в кадре перед снятием, поэтому его видят хуки
     * {@link HookPhase#AFTER_NODE_PARSE}, но не дети (на спуске узла ещё не существует).
     */
    protected final Node parseTSNode(TSNode node) {
        if (node.isNull()) {
            return null;
        }
        HandlerEntry entry = tsNodeHandlers.get(node.getType());
        if (entry == null) {
            throw new UnsupportedParsingException(String.format("Can't parse %s", node.getType()));
        }
        ctx.enterSource(node, entry.produces());
        try {
            Node parsed = entry.handler().apply(node);
            if (parsed == null) {
                // Сохраняем прежнее поведение: handler, вернувший null, означает «не разобрал»
                throw new UnsupportedParsingException(String.format("Can't parse %s", node.getType()));
            }
            Node createdNode = hooks.run(HookPhase.AFTER_NODE_PARSE, parsed, parsed, node);
            ctx.completeFrame(createdNode);
            matchParserNodes(node, createdNode);
            return createdNode;
        } finally {
            ctx.leaveFrame();
        }
    }

    /**
     * @param produces тип узла, который строит handler. Объявляется обязательно: на нём
     *                 построен контекст разбора (см. {@link HandlerEntry}).
     */
    protected final void registerTSNodeHandler(String tsNodeType, Class<? extends Node> produces,
                                               Function<TSNode, Node> handler) {
        Objects.requireNonNull(tsNodeType, "tsNodeType must not be null");
        tsNodeHandlers.put(tsNodeType, new HandlerEntry(produces, handler));
    }

    protected final void registerTSNodeHandler(Collection<String> tsNodeTypes, Class<? extends Node> produces,
                                               Function<TSNode, Node> handler) {
        Objects.requireNonNull(tsNodeTypes, "tsNodeTypes must not be null");
        for (String tsNodeType : tsNodeTypes) {
            registerTSNodeHandler(tsNodeType, produces, handler);
        }
    }

    protected final Optional<HandlerEntry> resolveTsNodeHandler(String tsNodeType) {
        return Optional.ofNullable(tsNodeHandlers.get(tsNodeType));
    }

    public final boolean supportsTSNodeType(String tsNodeType) {
        return tsNodeHandlers.containsKey(tsNodeType);
    }

    public final Set<String> getRegisteredTSNodeTypes() {
        return Set.copyOf(tsNodeHandlers.keySet());
    }

    /**
     * Сахар над {@link HookPhase#AFTER_NODE_PARSE} для языковых хуков: доработка узла
     * заданного типа сразу после его построения.
     */
    public final <T extends Node> HookHandle registerPostParsePreparation(Class<T> nodeType, UnaryOperator<T> preparation) {
        Objects.requireNonNull(preparation, "preparation must not be null");
        return hooks.intercept(HookPhase.AFTER_NODE_PARSE, nodeType,
                (node, value, context) -> Objects.requireNonNull(
                        preparation.apply(node),
                        "Post-parse preparation returned null for node type " + nodeType.getName()
                ));
    }

    /**
     * Сахар над {@link HookPhase#AFTER_NODE_PARSE} для наблюдателей: узнать о построенном
     * узле вместе с исходным узлом tree-sitter, ничего не меняя.
     */
    public final <T extends Node> HookHandle registerOnNodeParsedHook(Class<T> nodeType,
                                                                      BiConsumer<TSNode, T> hookAction) {
        Objects.requireNonNull(hookAction, "hookAction must not be null");
        return hooks.observe(HookPhase.AFTER_NODE_PARSE, nodeType,
                (node, value, context) -> context.source(TSNode.class)
                        .ifPresent(tsNode -> hookAction.accept(tsNode, node)));
    }

    /**
     * Постобработка построенного дерева. Проходы анализа зарегистрированы на
     * {@link HookPhase#AFTER_TREE_PARSE}; внешний потребитель может добавить свои туда же.
     */
    public void postProcessTree(MeaningTree meaningTree) {
        hooks.run(HookPhase.AFTER_TREE_PARSE, meaningTree, meaningTree);
    }
}
