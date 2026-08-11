package org.vstu.meaningtree.languages;

import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEvaluator;
import org.vstu.meaningtree.utils.analysis.loops.LoopIterationAnalyzer;
import org.vstu.meaningtree.utils.analysis.symbols.SymbolResolver;
import org.vstu.meaningtree.utils.hooks.HookHandle;
import org.vstu.meaningtree.utils.hooks.HookOrder;
import org.vstu.meaningtree.utils.hooks.HookPhase;

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

    private final Map<String, Function<TSNode, Node>> tsNodeHandlers = new LinkedHashMap<>();
    private final LoopIterationAnalyzer loopIterationAnalyzer = new LoopIterationAnalyzer();

    public LanguageParser(LanguageTranslator translator, TSLanguage language) {
        super(translator);
        _tsLanguage = language;
        _tsParser = new TSParser();
        _tsParser.setLanguage(language);
        registerAnalysisPasses();
    }

    /**
     * Регистрирует проходы анализа, выполняемые после построения дерева.
     * <p>
     * Порядок между ними существенен и раньше держался только на порядке строк в методе:
     * {@code SymbolResolver} дописывает типы полей, которыми затем пользуется
     * {@code ExpressionValueEvaluator}, а вычисленные им значения нужны
     * {@code LoopIterationAnalyzer}. Через {@link HookOrder} эта зависимость выражена явно.
     */
    private void registerAnalysisPasses() {
        hooks.intercept(HookPhase.AFTER_TREE_PARSE, HookOrder.EARLY, (tree, value, context) -> {
            new SymbolResolver(value, context.scope()).resolve();
            return value;
        });
        hooks.intercept(HookPhase.AFTER_TREE_PARSE, HookOrder.NORMAL, (tree, value, context) -> {
            new ExpressionValueEvaluator(value, context.scope()).analyze();
            return value;
        });
        hooks.intercept(HookPhase.AFTER_TREE_PARSE, HookOrder.LATE, (tree, value, context) -> {
            loopIterationAnalyzer.analyze(value, context.scope());
            return value;
        });
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

    protected final Node parseTSNode(TSNode node) {
        if (node.isNull()) {
            return null;
        }
        Optional<Node> fromRegistry = parseWithRegistry(node);
        if (fromRegistry.isPresent()) {
            Node parsed = fromRegistry.get();
            Node createdNode = hooks.run(HookPhase.AFTER_NODE_PARSE, parsed, parsed, node);
            matchParserNodes(node, createdNode);
            return createdNode;
        }
        throw new UnsupportedParsingException(String.format("Can't parse %s", node.getType()));
    }

    protected final void registerTSNodeHandler(String tsNodeType, Function<TSNode, Node> handler) {
        Objects.requireNonNull(tsNodeType, "tsNodeType must not be null");
        Objects.requireNonNull(handler, "handler must not be null");
        tsNodeHandlers.put(tsNodeType, handler);
    }

    protected final void registerTSNodeHandler(Collection<String> tsNodeTypes, Function<TSNode, Node> handler) {
        Objects.requireNonNull(tsNodeTypes, "tsNodeTypes must not be null");
        for (String tsNodeType : tsNodeTypes) {
            registerTSNodeHandler(tsNodeType, handler);
        }
    }

    protected final Optional<Function<TSNode, Node>> resolveTsNodeHandler(String tsNodeType) {
        return Optional.ofNullable(tsNodeHandlers.get(tsNodeType));
    }

    protected final Optional<Node> parseWithRegistry(TSNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        Function<TSNode, Node> handler = tsNodeHandlers.get(node.getType());
        if (handler == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(handler.apply(node));
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
