package org.vstu.meaningtree.serializers.xml;

import com.google.gson.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;

/**
 * Двунаправленный конвертер между JSON и XML для MeaningTree.
 * Сохраняет структуру массивов (включая пустые) и использует поле "type" для именования элементов.
 */
public class JsonXmlConverter {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final String ARRAY_WRAPPER = "array";
    private static final String ARRAY_ITEM = "item";

    // ==================== JSON → XML ====================

    /**
     * Конвертирует JSON строку в XML строку
     */
    public static String jsonToXml(String json, boolean pretty) throws Exception {
        JsonElement element = JsonParser.parseString(json);
        Document doc = createDocument();

        Element root = jsonElementToXml(doc, element, "root");
        doc.appendChild(root);

        return documentToString(doc, pretty);
    }

    /**
     * Конвертирует JsonObject в XML строку
     */
    public static String jsonToXml(JsonObject jsonObject, boolean pretty) throws Exception {
        return jsonToXml(gson.toJson(jsonObject), pretty);
    }

    /**
     * Рекурсивное преобразование JsonElement в XML Element
     */
    private static Element jsonElementToXml(Document doc, JsonElement element, String tagName) {
        if (element.isJsonObject()) {
            return jsonObjectToXml(doc, element.getAsJsonObject(), tagName);
        } else if (element.isJsonArray()) {
            return jsonArrayToXml(doc, element.getAsJsonArray(), tagName);
        } else if (element.isJsonPrimitive()) {
            return jsonPrimitiveToXml(doc, element.getAsJsonPrimitive(), tagName);
        } else if (element.isJsonNull()) {
            Element elem = doc.createElement(tagName);
            elem.setAttribute("null", "true");
            return elem;
        }
        return doc.createElement(tagName);
    }

    /**
     * Преобразование JsonObject в XML Element
     */
    private static Element jsonObjectToXml(Document doc, JsonObject obj, String tagName) {
        // Используем поле "type" если есть, иначе переданное имя
        String elementName = obj.has("type") ?
                sanitizeTagName(obj.get("type").getAsString()) : tagName;

        Element element = doc.createElement(elementName);

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if (key.equals("type")) {
                // "type" сохраняем как атрибут
                element.setAttribute("type", value.getAsString());
            } else if (value.isJsonArray()) {
                // Массивы оборачиваем для сохранения структуры
                Element arrayWrapper = doc.createElement(sanitizeTagName(key));
                arrayWrapper.setAttribute("is_array", "true");

                JsonArray array = value.getAsJsonArray();
                for (JsonElement item : array) {
                    Element itemElement = jsonElementToXml(doc, item, ARRAY_ITEM);
                    arrayWrapper.appendChild(itemElement);
                }
                element.appendChild(arrayWrapper);
            } else if (value.isJsonObject()) {
                Element child = jsonElementToXml(doc, value, sanitizeTagName(key));
                element.appendChild(child);
            } else if (value.isJsonPrimitive()) {
                JsonPrimitive primitive = value.getAsJsonPrimitive();
                if (primitive.isString() || primitive.isBoolean()) {
                    element.setAttribute(key, primitive.getAsString());
                } else {
                    // Числа можно хранить как атрибуты или дочерние элементы
                    element.setAttribute(key, primitive.getAsString());
                }
            } else if (value.isJsonNull()) {
                element.setAttribute(key, "null");
            }
        }

        return element;
    }

    /**
     * Преобразование JsonArray в XML Element
     */
    private static Element jsonArrayToXml(Document doc, JsonArray array, String tagName) {
        Element wrapper = doc.createElement(tagName);
        wrapper.setAttribute("is_array", "true");

        for (JsonElement item : array) {
            Element itemElement = jsonElementToXml(doc, item, ARRAY_ITEM);
            wrapper.appendChild(itemElement);
        }

        return wrapper;
    }

    /**
     * Преобразование JsonPrimitive в XML Element
     */
    private static Element jsonPrimitiveToXml(Document doc, JsonPrimitive primitive, String tagName) {
        Element element = doc.createElement(tagName);
        element.setTextContent(primitive.getAsString());
        return element;
    }

    // ==================== XML → JSON ====================

    /**
     * Конвертирует XML строку в JSON строку
     */
    public static String xmlToJson(String xml) throws Exception {
        Document doc = parseXmlString(xml);
        JsonElement element = xmlToJsonElement(doc.getDocumentElement());
        return gson.toJson(element);
    }

    /**
     * Конвертирует XML строку в JsonObject
     */
    public static JsonObject xmlToJsonObject(String xml) throws Exception {
        Document doc = parseXmlString(xml);
        JsonElement element = xmlToJsonElement(doc.getDocumentElement());
        return element.getAsJsonObject();
    }

    /**
     * Рекурсивное преобразование XML Element в JsonElement
     */
    private static JsonElement xmlToJsonElement(Element element) {
        // Проверяем, является ли элемент массивом
        if ("true".equals(element.getAttribute("is_array"))) {
            return xmlArrayToJson(element);
        }

        // Проверяем null
        if ("true".equals(element.getAttribute("null"))) {
            return JsonNull.INSTANCE;
        }

        JsonObject obj = new JsonObject();

        // Добавляем тип из имени элемента (если не root)
        if (!element.getTagName().equals("root") && !element.getTagName().equals(ARRAY_ITEM)) {
            obj.addProperty("type", element.getTagName());
        }

        // Добавляем атрибуты (кроме служебных)
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            String attrName = attr.getNodeName();
            String attrValue = attr.getNodeValue();

            if (!attrName.equals("is_array") && !attrName.equals("null")) {
                if (attrName.equals("type")) {
                    obj.addProperty("type", attrValue);
                } else if (attrValue.equals("null")) {
                    obj.add(attrName, JsonNull.INSTANCE);
                } else if (attrValue.equals("true") || attrValue.equals("false")) {
                    obj.addProperty(attrName, Boolean.parseBoolean(attrValue));
                } else if (isNumeric(attrValue)) {
                    if (attrValue.contains(".")) {
                        obj.addProperty(attrName, Double.parseDouble(attrValue));
                    } else {
                        obj.addProperty(attrName, Long.parseLong(attrValue));
                    }
                } else {
                    obj.addProperty(attrName, attrValue);
                }
            }
        }

        // Обрабатываем дочерние элементы
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String childName = childElement.getTagName();

                // Проверяем, является ли дочерний элемент массивом
                if ("true".equals(childElement.getAttribute("is_array"))) {
                    obj.add(childName, xmlArrayToJson(childElement));
                } else {
                    obj.add(childName, xmlToJsonElement(childElement));
                }
            } else if (child.getNodeType() == Node.TEXT_NODE) {
                String text = child.getTextContent().trim();
                if (!text.isEmpty()) {
                    obj.addProperty("value", text);
                }
            }
        }

        return obj;
    }

    /**
     * Преобразование XML массива в JsonArray
     */
    private static JsonArray xmlArrayToJson(Element arrayElement) {
        JsonArray array = new JsonArray();
        NodeList children = arrayElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                array.add(xmlToJsonElement(childElement));
            }
        }

        return array;
    }

    // ==================== Вспомогательные методы ====================

    private static Document createDocument() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }

    private static Document parseXmlString(String xml) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
    }

    private static String documentToString(Document doc, boolean pretty) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        if (pretty) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        } else {
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
        }
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }

    /**
     * Очищает имя тега от недопустимых символов
     */
    private static String sanitizeTagName(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Проверяет, является ли строка числом
     */
    private static boolean isNumeric(String str) {
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}