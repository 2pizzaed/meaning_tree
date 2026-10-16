package org.vstu.meaningtree.nodes.statements;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Тело, выполняемое во владении ресурсами, которые освобождаются при выходе из него:
 * java-<code>try (R r = ...) { ... }</code> без ветвей <code>catch</code> и
 * <code>finally</code> и python-<code>with ... as ...: ...</code>.
 * <p>
 * Та же конструкция вместе с <code>catch</code> или <code>finally</code> — это не этот узел,
 * а {@link org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement} с
 * заполненным списком ресурсов: в языке они записаны одним оператором, и ветви ловят в том
 * числе ошибку захвата ресурса.
 */
public class ResourceContextStatement extends Statement implements HasBodyStatement {
    @TreeNode private List<Node> resourceDeclarations;

    @TreeNode private Statement body;

    public ResourceContextStatement(@NotNull List<Node> resourceDeclarations, @NotNull Statement body) {
        this.resourceDeclarations = validateResources(resourceDeclarations);
        this.body = body;
    }

    public ResourceContextStatement(@NotNull Node resourceDeclaration, @NotNull Statement body) {
        this(List.of(resourceDeclaration), body);
    }

    /**
     * Ресурсы в порядке захвата. Каждый элемент — либо {@link VariableDeclaration}
     * (<code>try (R r = e)</code>, <code>with e as r</code>), либо {@link Expression}
     * без имени (<code>try (existing)</code>, <code>with lock:</code>).
     */
    public List<Node> getResourceDeclarations() {
        return List.copyOf(resourceDeclarations);
    }

    public void setResourceDeclarations(@NotNull List<Node> resourceDeclarations) {
        this.resourceDeclarations = validateResources(resourceDeclarations);
    }

    @Override
    public Statement getBody() {
        return body;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        body = CompoundStatement.wrap(body);
        return (CompoundStatement) body;
    }

    /**
     * Список допустим только из объявлений переменных и голых выражений: другой узел в этой
     * позиции ни один язык не записывает, а обработчики ресурсов перебирают ровно эти два
     * случая, поэтому третий молча выпал бы из вывода.
     */
    public static List<Node> validateResources(@NotNull List<Node> resourceDeclarations) {
        if (resourceDeclarations.isEmpty()) {
            throw new IllegalArgumentException("Resource context must own at least one resource");
        }
        for (Node resource : resourceDeclarations) {
            if (!(resource instanceof VariableDeclaration) && !(resource instanceof Expression)) {
                throw new IllegalArgumentException(
                        "Resource must be a variable declaration or an expression, found: "
                                + resource.getClass().getName()
                );
            }
        }
        return new ArrayList<>(resourceDeclarations);
    }

    /**
     * Имя, которым ресурс связан в теле, — его нет у ресурса-выражения. У объявления с
     * несколькими декларантами (<code>R a = x, b = y</code>) имени тоже нет: ни один язык
     * такой ресурс не записывает, и освобождать пришлось бы каждое имя отдельно.
     */
    @Nullable
    public static SimpleIdentifier resourceName(@NotNull Node resource) {
        if (!(resource instanceof VariableDeclaration declaration) || declaration.getDeclarators().length != 1) {
            return null;
        }
        return declaration.getFirstDeclarator().getIdentifier();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ResourceContextStatement nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(resourceDeclarations, nodeInfos.resourceDeclarations)
                && Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourceDeclarations, body);
    }

    @Override
    public ResourceContextStatement clone() {
        ResourceContextStatement clone = (ResourceContextStatement) super.clone();
        clone.resourceDeclarations = new ArrayList<>(resourceDeclarations.stream().map(Node::clone).toList());
        clone.body = body.clone();
        return clone;
    }
}
