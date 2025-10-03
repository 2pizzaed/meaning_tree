package org.vstu.meaningtree.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.iterators.utils.NodeIterable;

import java.io.Serializable;
import java.util.Map;

public record SourceMap(String code, NodeIterable root,
                        Map<Long, Pair<Integer, Integer>> map,
                        String language)
        implements Serializable {}
