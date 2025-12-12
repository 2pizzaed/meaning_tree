package org.vstu.meaningtree.languages.configs;

import org.vstu.meaningtree.exceptions.ConflictingConfigParameterException;

import java.util.*;
import java.util.function.Predicate;

/**
 * Неизменяемый контейнер конфигурации для языковых трансляторов.
 * <p>
 * Использует класс параметра как ключ для типобезопасного доступа.
 * Все операции изменения возвращают новые экземпляры.
 */
public class Config {
    private final Map<Class<?>, ConfigParameter<?>> parameters = new HashMap<>();

    /**
     * Создает конфигурацию из массива параметров.
     * Последние параметры переопределяют предыдущие того же типа.
     */
    public Config(ConfigParameter<?>... configParameters) {
        put(configParameters);
    }

    /**
     * Создает конфигурацию из коллекции параметров.
     * Последние параметры переопределяют предыдущие того же типа.
     */
    public Config(Iterable<ConfigParameter<?>> configParameters) {
        put(configParameters);
        checkConflicts();
    }

    private void checkConflicts() {
        for (ConfigParameter<?> param : parameters.values()) {
            for (var entry : param.conflictsWith.entrySet()) {
                var foundParam = Optional.ofNullable(parameters.getOrDefault(entry.getKey(), null));
                if (entry.getValue() == null && foundParam.isPresent()) {
                    throw new ConflictingConfigParameterException("Parameter `%s` with it value `%s` cannot be defined with parameter `%s`"
                            .formatted(param.getClass().getSimpleName(), param.getValue(), foundParam.get().getClass().getSimpleName()));
                } else if (foundParam.isPresent() && foundParam.get().getValue().equals(entry.getValue())) {
                    throw new ConflictingConfigParameterException("Parameter `%s` with it value `%s` cannot be defined with parameter `%s` with value `%s`"
                            .formatted(param.getClass().getSimpleName(), param.getValue(), foundParam.get().getClass().getSimpleName(), foundParam.get().getValue())
                    );
                }
            }
        }
    }

    private void put(Iterable<ConfigParameter<?>> configParameters) {
        for (var param : configParameters) {
            put(param);
        }
    }

    private void put(ConfigParameter<?>... configParameters) {
        for (var param : configParameters) {
            put(param);
        }
    }

    private void put(ConfigParameter<?> parameter) {
        // check consistency
        if (parameters.containsKey(parameter.getClass())) {
            var existingParam = parameters.get(parameter.getClass());
            String formatted = "Unexpected narrowing of scope for parameter `%s`".formatted(parameter.getClass().getSimpleName());

            if (!(parameter instanceof ConfigScopedParameter && existingParam instanceof ConfigScopedParameter) && !parameter.getClass().equals(existingParam.getClass())) {
                throw new ConflictingConfigParameterException(formatted);
            }
            if (parameter instanceof ConfigScopedParameter scopedParameter
                    && existingParam instanceof ConfigScopedParameter existingScopedParameter
                    && scopedParameter._scope != existingScopedParameter._scope
            ) {
                throw new ConflictingConfigParameterException(formatted);
            }
        }

        parameters.put(parameter.getClass(), parameter);
    }

    /**
     * Проверяет наличие параметра заданного типа.
     */
    public <P, T extends ConfigParameter<P>> boolean has(Class<T> paramClass) {
        return parameters.containsKey(paramClass);
    }

    /**
     * Объединяет с другой конфигурацией.
     * Параметры другой конфигурации переопределяют параметры текущей.
     * 
     * @param other конфигурация для объединения (имеет приоритет)
     * @return новая объединенная конфигурация
     */
    public Config merge(Config other) {
        Set<ConfigParameter<?>> newParameters = new HashSet<>(other.parameters.values());
        newParameters.addAll(this.parameters.values());
        return new Config(newParameters);
    }

    /**
     * Объединяет с несколькими конфигурациями по порядку.
     * Более поздние конфигурации переопределяют более ранние.
     * 
     * @param others конфигурации для объединения (правые имеют приоритет)
     * @return новая объединенная конфигурация
     */
    public Config merge(Config... others) {
        List<Config> otherConfigs = new LinkedList<>(Arrays.asList(others));
        otherConfigs.addFirst(this);

        Set<ConfigParameter<?>> newParameters = new LinkedHashSet<>();

        for (var config : otherConfigs) {
            newParameters.addAll(config.parameters.values());
        }

        return new Config(newParameters);
    }

    /**
     * Создает подмножество конфига с параметрами, для которых предикат вернул <code>true</code>.
     */
    public Config subset(Predicate<ConfigParameter<?>> predicate) {
        return new Config(
                parameters.values().stream().filter(predicate).toList()
        );
    }

    /**
     * Получает значение параметра по типу.
     * 
     * @param paramClass класс параметра
     * @return значение параметра или пустой Optional если не найден
     */
    public <P, T extends ConfigParameter<P>> Optional<P> get(Class<T> paramClass) {
        return Optional.ofNullable(parameters.get(paramClass))
                .filter(paramClass::isInstance)
                .map(paramClass::cast)
                .map(ConfigParameter::getValue);
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (var param : parameters.entrySet()) {
            builder.append(param.getKey().getSimpleName());
            builder.append(" - ");
            builder.append(param.getValue()._value.toString());
            builder.append("\n");
        }

        return builder.toString();
    }
}
