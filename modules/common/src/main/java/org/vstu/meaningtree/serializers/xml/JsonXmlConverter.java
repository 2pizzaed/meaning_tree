package org.vstu.meaningtree.serializers.xml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Iterator;
import java.util.Map;


public final class JsonXmlConverter {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final XmlMapper xmlMapper = new XmlMapper();
    private static final Gson gson = new Gson();

    // --- name converters ---
    public static String snakeToUpperCamel(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        boolean up = true;
        for (char c : s.toCharArray()) {
            if (c == '_' || c == '-') {
                up = true;
            } else {
                out.append(up ? Character.toUpperCase(c) : c);
                up = false;
            }
        }
        return out.toString();
    }

    public static String upperCamelToSnake(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i != 0) out.append('_');
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // --- main flow: Gson JsonObject -> XML ---
    public static String gsonJsonObjectToXml(JsonObject gsonObj, boolean pretty) throws JsonProcessingException {

        if (pretty) {
            xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        } else {
            xmlMapper.disable(SerializationFeature.INDENT_OUTPUT);
        }

        // 1) convert Gson JsonObject -> Jackson JsonNode
        JsonNode jacksonNode = jsonMapper.readTree(gson.toJson(gsonObj));

        // 2) transform to "xml-ready" node (attributes prefixed with '@', element names converted)
        ObjectNode xmlReady = transformToXmlReady(jacksonNode);

        // 3) write XML
        return xmlMapper.writeValueAsString(xmlReady);
    }

    private static ObjectNode transformToXmlReady(JsonNode node) {
        ObjectNode result = jsonMapper.createObjectNode();

        if (!node.isObject()) {
            // wrap non-object into generic element
            result.set("Item", node);
            return result;
        }

        ObjectNode obj = (ObjectNode) node;

        // decide element name (from "type" if present)
        JsonNode typeNode = obj.get("type");
        String elemName = null;
        if (typeNode != null && typeNode.isTextual()) {
            elemName = snakeToUpperCamel(typeNode.asText());
        }

        ObjectNode content = jsonMapper.createObjectNode();

        Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> e = it.next();
            String key = e.getKey();
            JsonNode val = e.getValue();
            if ("type".equals(key)) continue;

            String xmlKey = snakeToUpperCamel(key);

            if (val.isValueNode()) {
                // scalar -> attribute (prefix with '@')
                content.put("@" + xmlKey, val.asText());
            } else if (val.isArray()) {
                // array -> sequence of child elements named xmlKey
                ArrayNode arr = jsonMapper.createArrayNode();
                for (JsonNode item : val) {
                    if (item.isValueNode()) {
                        // wrap scalar items into element nodes
                        ObjectNode itemNode = jsonMapper.createObjectNode();
                        itemNode.put(xmlKey, item.asText());
                        arr.add(itemNode);
                    } else {
                        // complex item: transform recursively; ensure resulting node is an element
                        ObjectNode childReady = transformToXmlReady(item);
                        arr.add(childReady);
                    }
                }
                content.set(xmlKey, arr);
            } else if (val.isObject()) {
                // nested object: transform recursively
                ObjectNode childReady = transformToXmlReady(val);
                // if childReady contains a single top-level element, attach it under xmlKey,
                // else attach the whole object under xmlKey
                content.set(xmlKey, childReady);
            } else {
                content.set(xmlKey, val);
            }
        }

        if (elemName != null) {
            result.set(elemName, content);
        } else {
            // no type -> use content fields as-is
            result.setAll(content);
        }

        return result;
    }

    // --- reverse: XML -> Gson JsonObject ---
    public static JsonObject xmlToGsonJsonObject(String xml) throws JsonProcessingException {
        JsonNode root = xmlMapper.readTree(xml);

        // xmlMapper.readTree usually gives an ObjectNode whose single field is the root element
        JsonNode converted = transformXmlNodeToJson(root);
        String jsonString = jsonMapper.writeValueAsString(converted);
        return gson.fromJson(jsonString, JsonObject.class);
    }

    private static JsonNode transformXmlNodeToJson(JsonNode node) {
        // if node is not object, return as-is
        if (!node.isObject()) return node;

        ObjectNode obj = jsonMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();

        // If this object has exactly one named child (common for xml root), treat that child as the element
        if (node.size() == 1) {
            Map.Entry<String, JsonNode> only = node.fields().next();
            String elementName = only.getKey();
            JsonNode content = only.getValue();

            ObjectNode out = jsonMapper.createObjectNode();
            // put type = elementName (converted to snake_case)
            out.put("type", upperCamelToSnake(elementName));

            if (content.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> cfields = content.fields();
                while (cfields.hasNext()) {
                    Map.Entry<String, JsonNode> ce = cfields.next();
                    String k = ce.getKey();
                    JsonNode v = ce.getValue();

                    if (k.startsWith("@")) {
                        // attribute -> scalar property
                        String propName = upperCamelToSnake(k.substring(1));
                        out.put(propName, v.asText());
                    } else {
                        // child element(s)
                        String propName = upperCamelToSnake(k);
                        if (v.isArray()) {
                            // reconstruct array: each array element may be an element-wrapped object or scalar wrapper
                            ArrayNode arr = jsonMapper.createArrayNode();
                            for (JsonNode item : v) {
                                // if item contains a single field that's an element wrapper, recurse
                                if (item.isObject() && item.size() == 1) {
                                    arr.add(transformXmlNodeToJson(item));
                                } else {
                                    arr.add(transformXmlNodeToJson(item));
                                }
                            }
                            out.set(propName, arr);
                        } else if (v.isObject()) {
                            // nested object or wrapper
                            out.set(propName, transformXmlNodeToJson(v));
                        } else {
                            out.set(propName, v);
                        }
                    }
                }
            } else {
                // content is scalar -> set some default field or value
                out.set("value", content);
            }

            return out;
        } else {
            // multiple fields: convert each field name -> snake_case and recurse
            ObjectNode out = jsonMapper.createObjectNode();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                String k = e.getKey();
                JsonNode v = e.getValue();
                String propName = upperCamelToSnake(k);
                if (v.isObject() || v.isArray()) {
                    out.set(propName, transformXmlNodeToJson(v));
                } else {
                    out.set(propName, v);
                }
            }
            return out;
        }
    }
}
