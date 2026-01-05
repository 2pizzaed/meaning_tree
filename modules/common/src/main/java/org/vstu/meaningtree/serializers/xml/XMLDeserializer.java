package org.vstu.meaningtree.serializers.xml;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeSerializationException;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.serializers.json.JsonDeserializer;
import org.vstu.meaningtree.serializers.model.Deserializer;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

public class XMLDeserializer implements Deserializer<String> {

    private JsonDeserializer jsonDeserializer;

    public XMLDeserializer()
    {
        this.jsonDeserializer = new JsonDeserializer();
    }

    @Override
    public Node deserialize(String serialized) {
        try {
            return jsonDeserializer.deserialize(JsonXmlConverter.xmlToJsonObject(serialized));
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public MeaningTree deserializeTree(String serialized) {
        try {
            return jsonDeserializer.deserializeTree(JsonXmlConverter.xmlToJsonObject(serialized));
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public SourceMap deserializeSourceMap(String serialized) {
        try {
            return jsonDeserializer.deserializeSourceMap(JsonXmlConverter.xmlToJsonObject(serialized));
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public TokenList deserializeTokens(String serialized) {
        try {
            return jsonDeserializer.deserializeTokens(JsonXmlConverter.xmlToJsonObject(serialized));
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }

    @Override
    public Token deserializeToken(String token) {
        try {
            return jsonDeserializer.deserializeToken(JsonXmlConverter.xmlToJsonObject(token));
        } catch (Exception e) {
            throw new MeaningTreeSerializationException(e);
        }
    }
}
