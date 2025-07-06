package org.vstu.meaningtree.serializers.model;

import java.util.Optional;
import java.util.function.Function;

/**
 * Представляет именованный «псевдоним» для некоторого значения типа {@code T}.
 * Позволяет хранить различные обработчики (например, функции-сериализаторы) под понятными строковыми именами
 * и выбирать нужный обработчик по запрошенному имени.
 *
 * <p>Основное назначение класса — обеспечить гибкое определение и применение разных стратегий ввода/вывода,
 * например, сериализаторов узлов в различных форматах (JSON, RDF и т.п.),
 * без привязывания к конкретным реализациям.
 *
 * <p>Пример использования:
 * <pre>{@code
 * private static final IOAliases<Function<Node, String>> serializers = new IOAliases<>(List.of(
 *     new IOAlias<>("json", node -> { ... }),
 *     new IOAlias<>("rdf",  node -> { ... })
 * ));
 *
 * // при запросе формата "json" будет применён соответствующий сериализатор:
 * serializers.stream()
 *           .map(alias -> alias.apply("json", fn -> fn.apply(node)))
 *           .filter(Optional::isPresent)
 *           .map(Optional::get)
 *           .findFirst();
 * }</pre>
 *
 * @param <T> тип хранимого значения (например, функция-сериализатор или другой обработчик)
 */
public class IOAlias<T> {
    private final String name;
    private final T value;

    /**
     * Создаёт новый псевдоним для заданного значения.
     *
     * @param name  имя псевдонима (например, формат вывода: "json", "rdf" и т.п.)
     * @param value само значение, с которым будет связан псевдоним
     */
    public IOAlias(String name, T value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Если имя псевдонима совпадает с {@code requestedName} (без учёта регистра),
     * применяет к внутреннему значению функцию {@code function} и возвращает результат
     * в виде {@link Optional}. Иначе возвращает пустой {@code Optional}.
     *
     * @param requestedName имя, по которому проверяется совпадение с этим псевдонимом
     * @param function      функция для обработки значения типа {@code T}
     * @param <R>           тип результата применения функции
     * @return {@code Optional<R>} с результатом функции, если имена совпали;
     *         иначе — пустой {@code Optional}
     */
    public <R> Optional<R> apply(String requestedName, Function<T, R> function) {
        if (this.name.equalsIgnoreCase(requestedName)) {
            return Optional.of(function.apply(value));
        }
        return Optional.empty();
    }

    /**
     * Возвращает имя псевдонима для отображения пользователю или в логах.
     *
     * @return строковое имя псевдонима
     */
    public String getNameForDisplay() {
        return name;
    }
}
