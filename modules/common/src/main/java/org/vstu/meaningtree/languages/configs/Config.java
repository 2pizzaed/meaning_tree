package org.vstu.meaningtree.languages.configs;

import org.jspecify.annotations.NonNull;
import org.vstu.meaningtree.exceptions.UnsupportedConfigParameterException;

import java.util.*;
import java.util.function.Predicate;

/**
 * Неизменяемый контейнер конфигурации для языковых трансляторов.
 * <p>
 * Использует класс параметра как ключ для типобезопасного доступа.
 * Все операции изменения возвращают новые экземпляры.
 */
public class Config implements Iterable<ConfigParameter> {
    // param id -> param object
    private final Map<String, ConfigParameter> parameters = new HashMap<>();

    /**
     * Создает конфигурацию из массива параметров.
     * Последние параметры переопределяют предыдущие того же типа.
     */
    public Config(ConfigParameter... configParameters) {
        put(configParameters);
    }

    /**
     * Создает конфигурацию из коллекции параметров.
     * Последние параметры переопределяют предыдущие того же типа.
     */
    public Config(Iterable<ConfigParameter> configParameters) {
        put(configParameters);
    }

    private void put(Iterable<ConfigParameter> configParameters) {
        for (var param : configParameters) {
            put(param);
        }
    }

    private void put(ConfigParameter... configParameters) {
        for (var param : configParameters) {
            put(param);
        }
    }

    private void put(ConfigParameter parameter) {
        parameters.put(parameter.getId(), parameter);
    }

    /**
     * Проверяет наличие параметра заданного типа.
     */
    public boolean has(String id) {
        return parameters.containsKey(id);
    }

    /**
     * Объединяет с другой конфигурацией.
     * Параметры другой конфигурации переопределяют параметры текущей.
     * 
     * @param other конфигурация для объединения (имеет приоритет)
     * @return новая объединенная конфигурация
     */
    public Config merge(Config other) {
        Set<ConfigParameter> newParameters = new HashSet<>(other.parameters.values());
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

        Set<ConfigParameter> newParameters = new LinkedHashSet<>();

        for (var config : otherConfigs) {
            newParameters.addAll(config.parameters.values());
        }

        return new Config(newParameters);
    }

    /**
     * Создает подмножество конфига с параметрами, для которых предикат вернул <code>true</code>.
     */
    public Config subset(Predicate<ConfigParameter> predicate) {
        return new Config(
                parameters.values().stream().filter(predicate).toList()
        );
    }

    /**
     * Получает значение параметра по типу.
     * 
     * @param id идентификатор параметра
     * @return значение параметра или исключение об отсутствии
     */
    public ConfigParameter get(String id) {
        var result = parameters.getOrDefault(id, null);
        if (result == null) {
            throw new UnsupportedConfigParameterException("%s config wasn't found".formatted(id));
        }
        return result;
    }

    /**
     * Получает значение параметра по типу.
     *
     * @param id идентификатор параметра
     * @return значение параметра или пустой Optional если не найден
     */
    public Optional<ConfigParameter> getOptional(String id) {
        return Optional.ofNullable(parameters.getOrDefault(id, null));
    }

    @Override
    public Config clone() {
        return new Config(parameters.values());
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();

        for (var param : parameters.entrySet()) {
            builder.append(param.getKey());
            builder.append(" - ");
            builder.append(param.getValue()._value.toString());
            builder.append("\n");
        }

        return builder.toString();
    }

    @Override
    public @NonNull Iterator<ConfigParameter> iterator() {
        return parameters.values().iterator();
    }
}
