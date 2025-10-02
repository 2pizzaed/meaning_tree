package org.vstu.meaningtree.serializers.model;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

public interface Deserializer<T> {
    Node deserialize(T serialized);
    MeaningTree deserializeTree(T serialized);
    SourceMap deserializeSourceMap(T serialized);
    TokenList deserializeTokens(T serialized);
    Token deserializeToken(T token);
}
