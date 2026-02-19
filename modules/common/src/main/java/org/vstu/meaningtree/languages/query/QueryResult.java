package org.vstu.meaningtree.languages.query;

import org.treesitter.TSNode;
import org.vstu.meaningtree.nodes.Node;

import java.util.*;

public final class QueryResult {
    private final Map<String, List<TSNode>> tsNodesByCapture;
    private final Map<String, List<Node>> mtNodesByCapture;

    public QueryResult(Map<String, List<TSNode>> tsNodesByCapture, Map<String, List<Node>> mtNodesByCapture) {
        this.tsNodesByCapture = toImmutable(tsNodesByCapture);
        this.mtNodesByCapture = toImmutable(mtNodesByCapture);
    }

    public Set<String> captureNames() {
        return Collections.unmodifiableSet(tsNodesByCapture.keySet());
    }

    public List<TSNode> tsNodes(String captureName) {
        return tsNodesByCapture.getOrDefault(captureName, List.of());
    }

    public List<Node> mtNodes(String captureName) {
        return mtNodesByCapture.getOrDefault(captureName, List.of());
    }

    public <T extends Node> List<T> mtNodes(String captureName, Class<T> nodeType) {
        return mtNodes(captureName).stream()
                .filter(nodeType::isInstance)
                .map(nodeType::cast)
                .toList();
    }

    private static <T> Map<String, List<T>> toImmutable(Map<String, List<T>> source) {
        Map<String, List<T>> copy = new HashMap<>();
        for (Map.Entry<String, List<T>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }
}
