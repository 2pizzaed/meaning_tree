package org.vstu.meaningtree.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record SourceMap(String code, NodeIterable root,
                        Map<Long, Pair<Integer, Integer>> bytePositions,
                        ScopeTable scopeTable,
                        String language,
                        Map<String, Number> metrics,
                        @Nullable String projectRootPath,
                        @Nullable String projectFileRelPath)
        implements Serializable {
    public static final String CYCLOMATIC_METRIC = "cyclomatic";

    public SourceMap {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(root, "root must not be null");
        Objects.requireNonNull(bytePositions, "bytePositions must not be null");
        Objects.requireNonNull(scopeTable, "scopeTable must not be null");
        Objects.requireNonNull(language, "language must not be null");
        metrics = metrics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(metrics));
        bytePositions = Map.copyOf(bytePositions);
    }

    public SourceMap(String code, NodeIterable root,
                     Map<Long, Pair<Integer, Integer>> bytePositions,
                     ScopeTable scopeTable,
                     String language) {
        this(code, root, bytePositions, scopeTable, language, Map.of(), null, null);
    }

    public SourceMap(String code, NodeIterable root,
                     Map<Long, Pair<Integer, Integer>> bytePositions,
                     ScopeTable scopeTable,
                     String language,
                     Map<String, Number> metrics) {
        this(code, root, bytePositions, scopeTable, language, metrics, null, null);
    }
}
