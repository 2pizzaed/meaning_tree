package org.vstu.meaningtree.serializers.model;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

public interface Serializer<T> {
    T serialize(Node node);
    T serialize(MeaningTree mt);
    T serialize(SourceMap map);
    T serialize(TokenList tokenList);
    T serialize(Token token);

    default T serialize(NodeIterable i) {
        if (i instanceof Node node) {
            return serialize(node);
        } else if (i instanceof MeaningTree mt) {
            return serialize(mt);
        }
        return serialize(i);
    }


}
