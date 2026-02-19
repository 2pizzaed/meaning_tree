package org.vstu.meaningtree.languages.query;

import org.apache.commons.lang3.tuple.Pair;
import org.treesitter.*;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Hook;

import java.util.*;

public final class ParseSession implements Hook<Pair<TSNode, Node>> {
    private final String sourceCode;
    private final Map<ByteRange, List<Node>> mtNodeIndexByRange = new HashMap<>();

    private TSNode rootTsNode;
    private MeaningTree meaningTree;

    public ParseSession(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String sourceCode() {
        return sourceCode;
    }

    public TSNode rootTsNode() {
        return rootTsNode;
    }

    public MeaningTree meaningTree() {
        return meaningTree;
    }

    public void setRootTsNode(TSNode rootTsNode) {
        this.rootTsNode = rootTsNode;
    }

    public void setMeaningTree(MeaningTree meaningTree) {
        this.meaningTree = meaningTree;
    }

    @Override
    public boolean isTriggered(Pair<TSNode, Node> object) {
        return object != null && object.getLeft() != null && object.getRight() != null;
    }

    @Override
    public void accept(Pair<TSNode, Node> object) {
        registerParsedNode(object.getLeft(), object.getRight());
    }

    private void registerParsedNode(TSNode tsNode, Node mtNode) {
        if (rootTsNode == null) {
            rootTsNode = tsNode;
        }

        ByteRange range = ByteRange.from(tsNode.getStartByte(), tsNode.getEndByte());
        mtNodeIndexByRange.computeIfAbsent(range, key -> new ArrayList<>()).add(mtNode);
    }

    public QueryResult execute(CompiledTSQuery compiledQuery) {
        if (rootTsNode == null) {
            throw new IllegalStateException("Parse session does not contain a root Tree-sitter node");
        }

        TSQuery query = compiledQuery.getQuery();
        TSQueryCursor cursor = new TSQueryCursor();
        cursor.exec(query, rootTsNode);

        Map<String, List<TSNode>> tsNodesByCapture = new LinkedHashMap<>();
        Map<String, List<Node>> mtNodesByCapture = new LinkedHashMap<>();
        Map<String, Set<Node>> uniqueNodesByCapture = new HashMap<>();

        TSQueryMatch match = new TSQueryMatch();
        while (cursor.nextMatch(match)) {
            TSQueryCapture[] captures = match.getCaptures();
            for (TSQueryCapture capture : captures) {
                String captureName = query.getCaptureNameForId(capture.getIndex());
                TSNode tsNode = capture.getNode();
                ByteRange captureRange = ByteRange.from(tsNode.getStartByte(), tsNode.getEndByte());

                tsNodesByCapture.computeIfAbsent(captureName, key -> new ArrayList<>()).add(tsNode);

                Set<Node> seen = uniqueNodesByCapture.computeIfAbsent(captureName, key -> new LinkedHashSet<>());
                for (Node node : resolveMtNodes(captureRange)) {
                    if (seen.add(node)) {
                        mtNodesByCapture.computeIfAbsent(captureName, key -> new ArrayList<>()).add(node);
                    }
                }
            }
        }

        return new QueryResult(tsNodesByCapture, mtNodesByCapture);
    }

    private List<Node> resolveMtNodes(ByteRange captureRange) {
        List<Node> exact = mtNodeIndexByRange.get(captureRange);
        if (exact != null && !exact.isEmpty()) {
            return exact;
        }

        List<Node> nested = new ArrayList<>();
        for (Map.Entry<ByteRange, List<Node>> entry : mtNodeIndexByRange.entrySet()) {
            if (entry.getKey().contains(captureRange)) {
                nested.addAll(entry.getValue());
            }
        }
        return nested;
    }
}
