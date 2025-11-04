package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;

/**
 * {@code TypeScope} управляет стеком областей видимости типов,
 * поддерживая операции входа и выхода из областей {@link ScopeTableElement}.
 */
public class ScopeTable {
    /**
     * Текущая область сущностей.
     */
    @NotNull
    private ScopeTableElement current;

    /**
     * Создаёт менеджер областей видимости с корневой областью.
     */
    public ScopeTable() {
        this.current = new ScopeTableElement(null);
    }

    /**
     * Входит в новую область видимости.
     */
    public void enter() {
        current = new ScopeTableElement(current);
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
        ScopeTableElement parent = current.getParent();
        if (parent != null) {
            current = parent;
            return;
        }

        if (rootScopeMustExist) {
            throw new IllegalStateException("Cannot leave root scope");
        }
    }

    public ScopeTableElement scope() {
        return current;
    }

    @Override
    public String toString() {
        return current.toString();
    }
}
