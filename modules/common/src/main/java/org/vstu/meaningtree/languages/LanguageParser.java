package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.treesitter.TSNode;
import org.treesitter.TSTree;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Hook;
import org.vstu.meaningtree.utils.TranslatorComponent;
import org.vstu.meaningtree.utils.TreeSitterUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class LanguageParser extends TranslatorComponent {
    protected String _code = "";
    protected LanguageTranslator translator;
    protected Map<int[], Object> _byteValueTags = new HashMap<>();

    protected List<Hook<Pair<TSNode, Node>>> onNodeParsedHooks = new ArrayList<>();

    public abstract TSTree getTSTree();

    public TSNode getRootNode() {
        return getTSTree().getRootNode();
    }

    public abstract MeaningTree getMeaningTree(String code);

    public abstract MeaningTree getMeaningTree(TSNode node, String code);

    protected synchronized MeaningTree getMeaningTree(String code, Map<int[], Object> values) {
        _byteValueTags = values;
        return getMeaningTree(code);
    }

    protected void assignValue(TSNode originNode, Node createdNode) {
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
