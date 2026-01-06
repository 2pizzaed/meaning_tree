package org.vstu.meaningtree.serializers.dot;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedSerializationException;
import org.vstu.meaningtree.iterators.utils.*;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.CharacterLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.NumericLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;
import org.vstu.meaningtree.serializers.model.Serializer;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class GraphvizDotSerializer implements Serializer<String> {

    private final StringBuilder dot = new StringBuilder();
    private final Set<Long> processedNodes = new HashSet<>();

    @Override
    public String serialize(Node node) {
        reset();
        dot.append("digraph AST {\n");
        dot.append("  node [shape=box, style=rounded];\n");
        dot.append("  rankdir=TB;\n\n");

        serializeNode(node);

        dot.append("}\n");
        return dot.toString();
    }

    @Override
    public String serialize(MeaningTree mt) {
        return serialize(mt.getRootNode());
    }

    private void serializeNode(Node root) {
        // Обходим дерево и создаем узлы и рёбра
        for (NodeInfo nodeInfo : root.iterate(true)) {
            Node current = nodeInfo.node();

            // Создаём узел только один раз
            if (!processedNodes.contains(current.getId())) {
                createDotNode(current);
                processedNodes.add(current.getId());
            }

            // Создаём рёбра к дочерним узлам
            Map<String, FieldDescriptor> fields = current.getFieldDescriptors();
            for (Map.Entry<String, FieldDescriptor> entry : fields.entrySet()) {
                String fieldName = entry.getKey();
                FieldDescriptor descriptor = entry.getValue();

                try {
                    if (descriptor instanceof NodeFieldDescriptor nfd) {
                        Node child = nfd.get();
                        if (child != null) {
                            createEdge(current, child, fieldName, -1);
                        }
                    } else if (descriptor instanceof ArrayFieldDescriptor afd) {
                        Iterator<Node> iter = afd.iterator();
                        int index = 0;
                        while (iter.hasNext()) {
                            Node child = iter.next();
                            if (child != null) {
                                createEdge(current, child, fieldName, index);
                            }
                            index++;
                        }
                    } else if (descriptor instanceof CollectionFieldDescriptor cfd) {
                        Iterator<Node> iter = cfd.iterator();
                        int index = 0;
                        while (iter.hasNext()) {
                            Node child = iter.next();
                            if (child != null) {
                                createEdge(current, child, fieldName, index);
                            }
                            index++;
                        }
                    }
                } catch (IllegalAccessException e) {
                    // Пропускаем недоступные поля
                }
            }
        }
    }

    private void createDotNode(Node node) {
        String nodeId = getNodeId(node);
        String label = getNodeLabel(node);
        dot.append("  ").append(nodeId).append(" [label=< ").append(escapeLabel(label)).append(" >];\n");
    }

    private void createEdge(Node parent, Node child, String fieldName, int index) {
        String parentId = getNodeId(parent);
        String childId = getNodeId(child);
        String label = index >= 0 ? fieldName + "[" + index + "]" : fieldName;

        dot.append("  ").append(parentId).append(" -> ").append(childId)
                .append(" [label=\"").append(escapeLabel(label)).append("\"];\n");
    }

    private String getNodeId(Node node) {
        return "node_" + node.getId();
    }

    private String getNodeLabel(Node node) {
        StringBuilder label = new StringBuilder();
        label.append(node.getClass().getSimpleName());
        label.append("<br/>");
        label.append("<i>id:</i> %d".formatted(node.getId()));

        String additional = getSpecializedNodeLabelInfo(node);
        if (additional != null && !additional.isEmpty()) {
            label.append("<br/>");
            label.append(additional);
        }

        // Добавляем значение, если есть
        Object value = node.getAssignedValueTag();
        if (value != null) {
            label.append("<br/><i>value:</i> ").append(value.toString());
        }
        return label.toString();
    }

    private String getSpecializedNodeLabelInfo(Node node) {
        return switch (node) {
            case NumericLiteral i -> "<b>%s</b>".formatted(i.getStringValue(true));
            case StringLiteral s -> "<b>%s</b>".formatted(s.getEscapedValue());
            case BoolLiteral b -> "<b>%s</b>".formatted(Boolean.toString(b.getValue()));
            case CharacterLiteral c -> "<b>%s</b>".formatted(Character.toString((char) c.getValue()));
            case Type ignored -> "";
            case Identifier i -> "<b>%s</b>".formatted(i.internalRepresentation());
            default -> "";
        };
    }

    private String escapeLabel(String label) {
        return label.replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private void reset() {
        dot.setLength(0);
        processedNodes.clear();
    }

    @Override
    public String serialize(SourceMap map) {
        throw new UnsupportedSerializationException();
    }

    @Override
    public String serialize(TokenList tokenList) {
        throw new UnsupportedSerializationException();
    }

    @Override
    public String serialize(Token token) {
        throw new UnsupportedSerializationException();
    }
}
