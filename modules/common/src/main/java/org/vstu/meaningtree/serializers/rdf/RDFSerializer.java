package org.vstu.meaningtree.serializers.rdf;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.serializers.model.*;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

public class RDFSerializer implements Serializer<Model> {
    public static final String NS = "http://vstu.ru/poas/code#";
    public static final String NS_base = "http://vstu.ru/poas/code";
    private int ptr = -1;

    public Model serialize(AbstractSerializedNode node) {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("", NS);
        model.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#");
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        model.setNsPrefix("xml", "http://www.w3.org/XML/1998/namespace");
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");

        Resource root = model.createResource(NS + "MeaningTree");
        serializeNode(node, model, root, "root");
        ptr = 0;
        return model;
    }

    private Literal createValue(Model model, Object obj) {
        return switch (obj) {
            case String v -> model.createTypedLiteral(v, XSDDatatype.XSDstring);
            case Byte v -> model.createTypedLiteral(v, XSDDatatype.XSDinteger);
            case Short v -> model.createTypedLiteral(v, XSDDatatype.XSDinteger);
            case Integer v -> model.createTypedLiteral(v, XSDDatatype.XSDinteger);
            case Long v -> model.createTypedLiteral(v, XSDDatatype.XSDlong);
            case Boolean v -> model.createTypedLiteral(v, XSDDatatype.XSDboolean);
            case Double v -> model.createTypedLiteral(v, XSDDatatype.XSDdouble);
            case Float v -> model.createTypedLiteral(v, XSDDatatype.XSDfloat);
            default -> model.createTypedLiteral(obj.toString());
        };
    }

    private Resource serializeNode(AbstractSerializedNode node, Model model, Resource parentResource, String uniqueName) {
        ptr++;
        Resource nodeResource = model.createResource(NS + "mt_node_" + ptr)
                .addProperty(RDF.type, model.createResource(NS + (node instanceof SerializedListNode ? "ListNode" : "Node")))
                .addProperty(model.createProperty(NS, "fieldName"), model.createResource(NS + uniqueName));

        node.values.forEach((key, value) -> {
            nodeResource.addLiteral(model.createProperty(NS, key), createValue(model, value));
        });

        if (node instanceof SerializedNode serializedNode) {
            nodeResource.addProperty(model.createProperty(NS, "nodeType"), model.createResource(NS + serializedNode.nodeName));

            serializedNode.fields.forEach((fieldName, fieldNode) -> {
                serializeNode(fieldNode, model, nodeResource, fieldName);
            });

        } else if (node instanceof SerializedListNode listNode) {
            RDFList rdfList = model.createList();

            for (AbstractSerializedNode listItem : listNode.nodes) {
                Resource res = serializeNode(listItem, model, null, uniqueName);
                rdfList = rdfList.with(res);
            }

            nodeResource.addProperty(model.createProperty(NS, "hasFieldList"), rdfList);
        } else if (node instanceof SerializedLabel label) {
            nodeResource.addProperty(model.createProperty(NS, "nodeType"), model.createResource(NS + "MeaningTreeLabel"));
        }
        if (parentResource != null
                && !parentResource.hasProperty(model.createProperty(NS, "hasField"), nodeResource)
                && !parentResource.hasProperty(RDF.type, "ListNode")
        ) {
            parentResource.addProperty(model.createProperty(NS, "hasField"), nodeResource);
        }
        return nodeResource;
    }

    @Override
    public Model serialize(Node node) {
        return serialize(new UniversalSerializer().serialize(node));
    }

    @Override
    public Model serialize(MeaningTree mt) {
        return serialize(new UniversalSerializer().serialize(mt));
    }

    @Override
    public Model serialize(SourceMap map) {
        throw new NotImplementedException();
    }

    @Override
    public Model serialize(TokenList tokenList) {
        throw new NotImplementedException();
    }

    @Override
    public Model serialize(Token token) {
        throw new NotImplementedException();
    }
}

