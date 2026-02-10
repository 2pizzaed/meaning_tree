package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.treesitter.TSLanguage;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Hook;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.TreeSitterUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class LanguageParser extends TranslatorComponent {
    private String _code = "";
    protected Map<int[], Object> _byteValueTags = new HashMap<>();

    protected TSParser _tsParser;
    protected TSLanguage _tsLanguage;

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
        rollbackContext();
    }

    public void setCode(String code) {
        resetParserState();
        _code = code;
    }

    public TSTree getTSTree() {
        return _tsParser.parseString(null, _code);
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
        var result = fromTSNode(node);

        for (Hook<Pair<TSNode, Node>> hook : onNodeParsedHooks) {
            var pair = Pair.of(node, result);
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
}
