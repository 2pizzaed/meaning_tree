package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.languages.helpers.HookUtils;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Hook;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEvaluator;
import org.vstu.meaningtree.utils.analysis.loops.LoopIterationAnalyzer;
import org.vstu.meaningtree.utils.analysis.symbols.SymbolResolver;

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

    protected List<Hook<Pair<TSNode, Node>>> onNodeParsedHooks = new ArrayList<>();
    private final List<HookUtils.NodePreparationEntry<? extends Node>> postParsePreparations = new ArrayList<>();

    public LanguageParser(LanguageTranslator translator, TSLanguage language) {
        super(translator);
        _tsLanguage = language;
        _tsParser = new TSParser();
        _tsParser.setLanguage(language);
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
            Node createdNode = applyPostParsePreparations(fromRegistry.get());
            matchParserNodes(node, createdNode);
            var pair = Pair.of(node, createdNode);
            for (Hook<Pair<TSNode, Node>> hook : onNodeParsedHooks) {
                if (hook.isTriggered(pair)) {
                    hook.accept(pair);
                }
            }
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

    protected final <T extends Node> void registerPostParsePreparation(Class<T> nodeType, UnaryOperator<T> preparation) {
        postParsePreparations.add(new HookUtils.NodePreparationEntry<>(nodeType, preparation));
    }

    protected final Node applyPostParsePreparations(Node node) {
        Objects.requireNonNull(node, "node must not be null");
        Node preparedNode = node;
        for (HookUtils.NodePreparationEntry<? extends Node> preparation : postParsePreparations) {
            if (!preparation.matches(preparedNode)) {
                continue;
            }
            preparedNode = Objects.requireNonNull(
                    preparation.apply(preparedNode),
                    "Post-parse preparation returned null for node type " + preparedNode.getClass().getName()
            );
        }
        return preparedNode;
    }

    public void postProcessTree(MeaningTree meaningTree) {
        new SymbolResolver(meaningTree, ctx.getScopeTable()).resolve();
        ExpressionValueEvaluator expressionValueEvaluator = new ExpressionValueEvaluator(meaningTree, ctx.getScopeTable());
        expressionValueEvaluator.analyze();
        loopIterationAnalyzer.analyze(meaningTree, ctx.getScopeTable());
    }

    boolean registerOnNodeParsedHook(Hook<Pair<TSNode, Node>> hook) {
        return onNodeParsedHooks.add(hook);
    }

    public final <T extends Node> Hook<Pair<TSNode, Node>> registerOnNodeParsedHook(Class<T> nodeType,
                                                                                     BiConsumer<TSNode, T> hookAction) {
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        Objects.requireNonNull(hookAction, "hookAction must not be null");
        Hook<Pair<TSNode, Node>> typedHook = new Hook<>() {
            @Override
            public boolean isTriggered(Pair<TSNode, Node> object) {
                return object != null && object.getRight() != null && nodeType.isAssignableFrom(object.getRight().getClass());
            }

            @Override
            public void accept(Pair<TSNode, Node> object) {
                hookAction.accept(object.getLeft(), nodeType.cast(object.getRight()));
            }
        };
        registerOnNodeParsedHook(typedHook);
        return typedHook;
    }

    boolean removeOnNodeParsedHook(Hook<Pair<TSNode, Node>> hook) {
        return onNodeParsedHooks.remove(hook);
    }

}
