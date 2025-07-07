package org.vstu.meaningtree.utils.type_inference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.types.UnknownType;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code TypedEntities} представляет набор сущностей текущей области видимости,
 * включая переменные и методы с хранящимся возвращаемым типом.
 * Поддерживает вложенные области через ссылку на родительский {@code TypedEntities}.
 */
public class TypedEntities {
    /**
     * Родительская коллекция сущностей, соответствующая вышестоящей области видимости.
     */
    @Nullable
    private final TypedEntities parent;

    /**
     * Хранилище типов переменных в текущей области.
     */
    @NotNull
    private final Map<SimpleIdentifier, Type> variables;

    /**
     * Хранилище возвращаемых типов методов в текущей области.
     */
    @NotNull
    private final Map<SimpleIdentifier, Type> methods;

    /**
     * Создаёт новый набор сущностей с родителем.
     *
     * @param parent родительский набор сущностей или {@code null} для корневого
     */
    public TypedEntities(@Nullable TypedEntities parent) {
        this.parent = parent;
        this.variables = new HashMap<>();
        this.methods = new HashMap<>();
    }

    /**
     * Добавляет переменную с указанным типом в текущую область.
     *
     * @param name идентификатор переменной, не может быть {@code null}
     * @param type тип переменной, не может быть {@code null}
     */
    public void addVariable(@NotNull SimpleIdentifier name, @NotNull Type type) {
        variables.put(name, type);
    }

    /**
     * Добавляет метод с указанным возвращаемым типом.
     *
     * @param name       идентификатор метода, не может быть {@code null}
     * @param returnType возвращаемый тип метода, не может быть {@code null}
     */
    public void addMethod(@NotNull SimpleIdentifier name, @NotNull Type returnType) {
        methods.put(name, returnType);
    }

    /**
     * Ищет тип переменной в текущей и родительских областях.
     *
     * @param name идентификатор переменной, не может быть {@code null}
     * @return найденный тип или {@code null}, если переменная не объявлена
     */
    @Nullable
    public Type getVariableType(@NotNull SimpleIdentifier name) {
        Type type = variables.get(name);
        if (type != null) {
            return type;
        }
        if (parent != null) {
            return parent.getVariableType(name);
        }
        return null;
    }

    /**
     * Изменяет или создаёт переменную в текущей области.
     *
     * @param name                идентификатор переменной, не может быть {@code null}
     * @param type                новый тип переменной, не может быть {@code null}
     * @param createIfNotExists   если {@code true}, создаёт переменную при отсутствии;
     *                            иначе выбрасывает исключение
     * @throws IllegalArgumentException если переменная отсутствует и
     *                                  {@code createIfNotExists} равно {@code false}
     */
    public void changeVariableType(@NotNull SimpleIdentifier name,
                                   @NotNull Type type,
                                   boolean createIfNotExists) {
        if (getVariableType(name) == null && !createIfNotExists) {
            throw new IllegalArgumentException("No such variable: " + name);
        }
        variables.put(name, type);
    }

    /**
     * Изменяет или создаёт переменную в текущей области.
     *
     * @param name идентификатор переменной, не может быть {@code null}
     * @param type новый тип переменной, не может быть {@code null}
     */
    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type) {
        changeVariableType(name, type, true);
    }

    /**
     * Ищет возвращаемый тип метода в текущей и родительских областях.
     *
     * @param name идентификатор метода, не может быть {@code null}
     * @return найденный тип или {@link UnknownType}, если метод не объявлен
     */
    @NotNull
    public Type getMethodReturnType(@NotNull SimpleIdentifier name) {
        Type type = methods.get(name);
        if (type != null) {
            return type;
        }
        if (parent != null) {
            return parent.getMethodReturnType(name);
        }
        return new UnknownType();
    }

    /**
     * Возвращает карту переменных текущей области.
     *
     * @return карта из идентификаторов переменных в типы
     */
    @NotNull
    public Map<SimpleIdentifier, Type> getVariables() {
        return variables;
    }

    /**
     * Возвращает карту методов текущей области.
     *
     * @return карта из идентификаторов методов в возвращаемые типы
     */
    @NotNull
    public Map<SimpleIdentifier, Type> getMethods() {
        return methods;
    }

    /**
     * Возвращает родительский {@code TypedEntities}.
     *
     * @return родитель или {@code null} для корня
     */
    @Nullable
    public TypedEntities getParent() {
        return parent;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        variables.forEach((id, t) -> sb.append(id).append(" = ").append(t).append(", "));
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}
