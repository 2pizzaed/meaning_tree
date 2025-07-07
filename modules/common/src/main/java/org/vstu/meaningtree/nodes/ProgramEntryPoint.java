package org.vstu.meaningtree.nodes;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ProgramEntryPoint extends Node {
    /**
     * _body не содержит _entryPointNode, если только точка входа - не метод главного класса.
     * Однако _entryPointNode может отсутствовать, тогда точка входа - _body
     * Viewer должен сам подстроиться под эту ситуацию и адаптировать под особенности своего языка
     */
    @TreeNode private List<Node> body;

    /**
     * Может быть функцией, методом главного класса, либо просто составным оператором (например, как в Python)
     */
    @Nullable
    private Node _entryPointNode;

    /**
     * Ссылка на главный класс. Он не исключается из body, нужен для удобства разработчиков поддержки для языков
     */
    @Nullable
    private ClassDefinition _mainClass;

    public ProgramEntryPoint(List<Node> body, ClassDefinition mainClass) {
        this(body, mainClass, null);
    }

    public ProgramEntryPoint(List<Node> body) {
        this(body, null, null);
    }

    public ProgramEntryPoint(List<Node> body, Node entryPoint) {
        this(body, null, entryPoint);
    }

    public ProgramEntryPoint(List<Node> body, @Nullable ClassDefinition mainClass, @Nullable Node entryPoint) {
        this.body = body;
        _mainClass = mainClass;
        _entryPointNode = entryPoint;
    }

    public List<Node> getBody() {
        return body;
    }

    public ClassDefinition getMainClass() {
        return Objects.requireNonNull(_mainClass, "Main class is not present");
    }

    public boolean hasMainClass() {
        return _mainClass != null;
    }

    public boolean hasEntryPoint() {
        return _entryPointNode != null;
    }

    public Node getEntryPoint() {
        return Objects.requireNonNull(_entryPointNode, "Entry point node is not present");
    }

    @Override
    public String generateDot() {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format("%s [label=\"%s\"];\n", _id, getClass().getSimpleName()));
        for (var node : body) {
            builder.append(node.generateDot());
            builder.append(String.format("%s -- %s;\n", _id, node.getId()));
        }

        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ProgramEntryPoint that = (ProgramEntryPoint) o;
        return Objects.equals(body, that.body) && Objects.equals(_entryPointNode, that._entryPointNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), body, _entryPointNode);
    }

    @Override
    public ProgramEntryPoint clone() {
        ProgramEntryPoint obj = (ProgramEntryPoint) super.clone();
        obj.body = new ArrayList<>(body.stream().map(Node::clone).toList());
        for (Node node : obj.body) {
            if (_mainClass != null && node.getId() == obj._mainClass.getId()) {
                obj._mainClass = (ClassDefinition) node;
            } else if (_entryPointNode != null && node.getId() == _entryPointNode.getId()) {
                obj._entryPointNode = node;
            }
        }
        return obj;
    }
}
