package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSQuery;
import org.treesitter.TSTree;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.query.CompiledTSQuery;
import org.vstu.meaningtree.languages.query.ParseSession;
import org.vstu.meaningtree.languages.query.QueryResult;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Hook;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

abstract public class LanguageParser extends TranslatorComponent implements QueryableParser {
    private String _code = "";
    protected Map<int[], Object> _byteValueTags = new HashMap<>();

    protected TSParser _tsParser;
    protected TSLanguage _tsLanguage;
    private TSTree _tsTreeCache = null;

    private ParseSession _activeParseSession = null;
    private ParseSession _latestParseSession = null;
    private Hook<Pair<TSNode, Node>> _parseSessionHook = null;
    private final Map<String, CompiledTSQuery> _queryCacheByText = new HashMap<>();
    private final Map<String, CompiledTSQuery> _queryCacheById = new HashMap<>();

    protected List<Hook<Pair<TSNode, Node>>> onNodeParsedHooks = new ArrayList<>();

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

        if (_parseSessionHook != null) {
            removeOnNodeParsedHook(_parseSessionHook);
            _parseSessionHook = null;
        }

        _activeParseSession = null;
        _latestParseSession = null;
        rollbackContext();
    }

    public void setCode(String code) {
        resetParserState();
        _code = code;
        _activeParseSession = new ParseSession(code);
        _parseSessionHook = _activeParseSession;
        registerOnNodeParsedHook(_parseSessionHook);
    }

    public TSTree getTSTree() {
        if (_tsTreeCache == null) {
            _tsTreeCache = _tsParser.parseString(null, _code);
        }
        return _tsTreeCache;
    }

    public TSNode getRootNode() {
        TSNode root = getTSTree().getRootNode();
        if (_activeParseSession != null) {
            _activeParseSession.setRootTsNode(root);
        }
        return root;
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
        var result = fromTSNode(node);
        var pair = Pair.of(node, result);

        for (Hook<Pair<TSNode, Node>> hook : onNodeParsedHooks) {
            if (hook.isTriggered(pair)) {
                hook.accept(pair);
            }
        }

        return result;
    }

    /***
     * This method may be called only in `fromTSNode` method! In other places, use parseTSNode
     * @param node raw Tree-sitter node
     * @return Meaning Tree Node
     */
    protected abstract Node fromTSNode(TSNode node);

    public boolean registerOnNodeParsedHook(Hook<Pair<TSNode, Node>> hook) {
        return onNodeParsedHooks.add(hook);
    }

    public boolean removeOnNodeParsedHook(Hook<Pair<TSNode, Node>> hook) {
        return onNodeParsedHooks.remove(hook);
    }

    void commitParseSession(MeaningTree meaningTree) {
        if (_activeParseSession != null) {
            _activeParseSession.setMeaningTree(meaningTree);
            _latestParseSession = _activeParseSession;
            _activeParseSession = null;

            if (_parseSessionHook != null) {
                removeOnNodeParsedHook(_parseSessionHook);
                _parseSessionHook = null;
            }
        }
    }

    public ParseSession getLatestParseSession() {
        if (_latestParseSession == null) {
            throw new IllegalStateException("No parse session available. Parse source code first.");
        }
        return _latestParseSession;
    }

    public synchronized CompiledTSQuery compileQuery(String queryText) {
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("Query text cannot be null or blank");
        }
        return _queryCacheByText.computeIfAbsent(queryText,
                key -> new CompiledTSQuery(null, key, new TSQuery(_tsLanguage, key)));
    }

    public synchronized CompiledTSQuery compileQuery(String queryId, String queryText) {
        if (queryId == null || queryId.isBlank()) {
            return compileQuery(queryText);
        }

        CompiledTSQuery alreadyCompiled = _queryCacheById.get(queryId);
        if (alreadyCompiled != null) {
            if (!alreadyCompiled.getQueryText().equals(queryText)) {
                throw new IllegalArgumentException("Query id `%s` is already bound to another query text".formatted(queryId));
            }
            return alreadyCompiled;
        }

        CompiledTSQuery compiled = compileQuery(queryText);
        CompiledTSQuery namedCompiled = new CompiledTSQuery(queryId, compiled.getQueryText(), compiled.getQuery());
        _queryCacheById.put(queryId, namedCompiled);
        return namedCompiled;
    }

    public QueryResult query(String queryText) {
        return query(compileQuery(queryText));
    }

    public QueryResult query(CompiledTSQuery compiledTSQuery) {
        return getLatestParseSession().execute(Objects.requireNonNull(compiledTSQuery));
    }
}
