package org.vstu.meaningtree.nodes.definitions;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.util.Objects;

/**
 * Класс-итератор: обычный класс, два метода которого играют роли «есть ли ещё элемент» и
 * «взять следующий элемент».
 * <p>
 * Наследует {@link ClassDefinition}, а не заводит поля под роли, потому что итератор — это
 * действительно класс: у него есть поля состояния, конструктор и произвольные вспомогательные
 * методы, и всё это уже описано телом класса. Узел добавляет только то, чего в теле нет:
 * тип элемента и то, какие из методов исполняют роли протокола. Имена ролей хранятся, а не
 * подразумеваются, потому что в разных языках они разные (<code>hasNext</code>/<code>next</code>
 * в Java), и отрисовщик другого языка должен знать, какой метод во что заворачивать.
 * <p>
 * {@link #getHasNextMethod()} и {@link #getNextMethod()} — самостоятельные идентификаторы, а не
 * ссылки на узлы внутри тела: узел, достижимый через {@code @TreeNode} из двух мест, ломает
 * индексацию дерева (<code>Duplicate node id</code>) и карту исходников.
 * <p>
 * Обратного преобразования в {@link GeneratorDefinition} нет: восстановить структурное тело
 * генератора из машины состояний в общем случае невозможно.
 */
public class IteratorDefinition extends ClassDefinition {
    @TreeNode private Type elementType;

    @TreeNode private SimpleIdentifier hasNextMethod;

    @TreeNode private SimpleIdentifier nextMethod;

    public IteratorDefinition(@NotNull ClassDeclaration declaration,
                              @NotNull CompoundStatement body,
                              @NotNull Type elementType,
                              @NotNull SimpleIdentifier hasNextMethod,
                              @NotNull SimpleIdentifier nextMethod) {
        super(declaration, body);
        this.elementType = Objects.requireNonNull(elementType);
        this.hasNextMethod = Objects.requireNonNull(hasNextMethod);
        this.nextMethod = Objects.requireNonNull(nextMethod);
    }

    /**
     * Тип элемента последовательности — то, что возвращает метод «взять следующий».
     */
    public Type getElementType() {
        return elementType;
    }

    /**
     * Имя метода без аргументов, возвращающего <code>true</code>, пока есть что выдавать.
     */
    public SimpleIdentifier getHasNextMethod() {
        return hasNextMethod;
    }

    /**
     * Имя метода без аргументов, возвращающего очередной элемент и продвигающего состояние.
     */
    public SimpleIdentifier getNextMethod() {
        return nextMethod;
    }

    /**
     * Определение метода, исполняющего роль «есть ли ещё элемент», или <code>null</code>,
     * если тело такого метода не содержит.
     */
    public MethodDefinition findHasNextDefinition() {
        return findMethod(hasNextMethod.getName());
    }

    /**
     * Определение метода, исполняющего роль «взять следующий», или <code>null</code>,
     * если тело такого метода не содержит.
     */
    public MethodDefinition findNextDefinition() {
        return findMethod(nextMethod.getName());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof IteratorDefinition other)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(elementType, other.elementType)
                && Objects.equals(hasNextMethod, other.hasNextMethod)
                && Objects.equals(nextMethod, other.nextMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), elementType, hasNextMethod, nextMethod);
    }

    @Override
    public IteratorDefinition clone() {
        var clone = (IteratorDefinition) super.clone();
        clone.elementType = elementType.clone();
        clone.hasNextMethod = hasNextMethod.clone();
        clone.nextMethod = nextMethod.clone();
        return clone;
    }
}
