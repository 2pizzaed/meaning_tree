package org.vstu.meaningtree.serializers.model;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.io.Serializable;

public interface Serializer<T> {
    T serialize(Node node);
    T serialize(MeaningTree mt);
    T serialize(SourceMap map);
    T serialize(TokenList tokenList);
    T serialize(Token token);

    default T serialize(Serializable serializable) {
        if (serializable instanceof Node node) {
            return serialize(node);
        } else if (serializable instanceof MeaningTree mt) {
            return serialize(mt);
        } else if (serializable instanceof SourceMap map) {
            return serialize(map);
        } else if (serializable instanceof TokenList tokenList) {
            return serialize(tokenList);
        } else if (serializable instanceof Token token) {
            return serialize(token);
        } else {
            throw new UnsupportedOperationException("Not supported format for serialization");
        }
    }

    default T serialize(NodeIterable i) {
        if (i instanceof Node node) {
            return serialize(node);
        } else if (i instanceof MeaningTree mt) {
            return serialize(mt);
        }
        return serialize(i);
    }


}
