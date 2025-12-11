package org.vstu.meaningtree.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.iterators.utils.NodeIterable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record SourceMap(String code, NodeIterable root,
                        Map<Long, Pair<Integer, Integer>> bytePositions,
                        List<DefinitionLink> definitions,
                        List<ImportLink> imports,
                        List<List<String>> userTypeHierarchy,
                        String language)
        implements Serializable {

    public record DefinitionLink(
            String name, long declarationNodeId, Long definitionNodeId,
            String type, Long parentDeclarationId, Long[] relatedTypesId
    ) {};
    public record ImportLink(String libraryName, long nodeId, String type, String[] components,
                             boolean isStatic, boolean allContentInclude
    ) {};
}
