package org.vstu.meaningtree.serializers.xml;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeSerializationException;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.serializers.json.JsonSerializer;
import org.vstu.meaningtree.serializers.model.Serializer;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

public class XMLSerializer implements Serializer<String> {

    private JsonSerializer jsonSerializer;
    private boolean prettyPrint;

    public XMLSerializer(boolean prettyPrint) {
        jsonSerializer = new JsonSerializer();
        this.prettyPrint = prettyPrint;
    }

    @Override
    public String serialize(Node node) {
        try {
            return JsonXmlConverter.jsonToXml(jsonSerializer.serialize(node), prettyPrint);
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public String serialize(MeaningTree mt) {
        try {
            return JsonXmlConverter.jsonToXml(jsonSerializer.serialize(mt), prettyPrint);
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public String serialize(SourceMap map) {
        try {
            return JsonXmlConverter.jsonToXml(jsonSerializer.serialize(map), prettyPrint);
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public String serialize(TokenList tokenList) {
        try {
            return JsonXmlConverter.jsonToXml(jsonSerializer.serialize(tokenList), prettyPrint);
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public String serialize(Token token) {
        try {
            return JsonXmlConverter.jsonToXml(jsonSerializer.serialize(token), prettyPrint);
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }
}
