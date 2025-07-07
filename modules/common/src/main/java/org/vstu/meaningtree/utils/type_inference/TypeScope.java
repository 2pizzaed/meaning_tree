package org.vstu.meaningtree.utils.type_inference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

/**
 * {@code TypeScope} управляет стеком областей видимости типов,
 * поддерживая операции входа и выхода из областей {@link TypedEntities}.
 */
public class TypeScope {
    /**
     * Текущая область сущностей.
     */
    @NotNull
    private TypedEntities current;

    /**
     * Создаёт менеджер областей видимости с корневой областью.
     */
    public TypeScope() {
        this.current = new TypedEntities(null);
    }

    /**
     * Входит в новую область видимости.
     */
    public void enter() {
        current = new TypedEntities(current);
    }

    /**
     * Входит в родительскую область.
     * Если родительской области нет, ничего не происходит.
     *
     * @throws IllegalStateException если текущая область корневая
     */
    public void leave() {
        leave(false);
    }

    /**
     * Входит в родительскую область.
     *
     * @param rootScopeMustExist Когда {@code true}, выбрасывает исключение
     *                           {@code IllegalStateException}, если родительской
     *                           области видимости не существует
     */
    public void leave(boolean rootScopeMustExist) {
        TypedEntities parent = current.getParent();
        if (parent != null) {
            current = parent;
            return;
        }

        if (rootScopeMustExist) {
            throw new IllegalStateException("Cannot leave root scope");
        }
    }

    /** Делегирует добавление переменной текущей области. */
    public void addVariable(@NotNull SimpleIdentifier name, @NotNull Type type) {
        current.addVariable(name, type);
    }

    /** Делегирует добавление метода текущей области. */
    public void addMethod(@NotNull SimpleIdentifier name, @NotNull Type returnType) {
        current.addMethod(name, returnType);
    }

    /** Делегирует поиск типа переменной. */
    @Nullable
    public Type getVariableType(@NotNull SimpleIdentifier name) {
        return current.getVariableType(name);
    }

    /** Делегирует изменение типа переменной. */
    public void changeVariableType(@NotNull SimpleIdentifier name,
                                   @NotNull Type type,
                                   boolean createIfNotExists) {
        current.changeVariableType(name, type, createIfNotExists);
    }

    /** Делегирует изменение или создание переменной. */
    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type) {
        current.changeVariableType(name, type);
    }

    /** Делегирует поиск возвращаемого типа метода. */
    @NotNull
    public Type getMethodReturnType(@NotNull SimpleIdentifier name) {
        return current.getMethodReturnType(name);
    }

    @Override
    public String toString() {
        return current.toString();
    }
}
