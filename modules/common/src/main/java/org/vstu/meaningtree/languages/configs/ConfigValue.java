package org.vstu.meaningtree.languages.configs;

import com.google.gson.*;

import java.util.*;

public class ConfigValue {
    // Value container
    private final Object value;

    // Signature of value
    private final boolean nullable;
    private final Class<?> type;
    private final List<Object> valueNarrowing; // нужно ограничить по допустимым значениям

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ConfigValue that = (ConfigValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    public ConfigValue(Object value, boolean nullable) {
        if (!fitsSupportedTypes(value.getClass())) {
            throw new IllegalArgumentException("Unsupported type %s. Map, Lists, primitives are allowed only".formatted(value.getClass()));
        }
        this.type = value.getClass();
        this.nullable = nullable;
        this.valueNarrowing = null;
        this.value = commonParse(value);
    }

    public ConfigValue(Object value) {
        this(value, false);
    }


    private ConfigValue(Class<?> type, List<Object> valueNarrowing, Object value, boolean nullable) {
        if (!nullable && value == null) {
            throw new IllegalArgumentException("Value cannot be null");
        }
        this.nullable = nullable;
        this.type = type;
        if (!fits(value)) {
            throw new IllegalArgumentException("Conflicting value type %s and declared type %s: cannot cast".formatted(value.getClass(), type));
        }
        this.valueNarrowing = valueNarrowing;
        if (valueNarrowing != null) {
            for (Object v : valueNarrowing) {
                if (!fits(v)) {
                    throw new IllegalArgumentException("Illegal type %s in possible values".formatted(v.getClass()));
                }
            }
        }
        this.value = commonParse(value);
    }

    public static ConfigValue nullable(Class<?> type) {
        return new ConfigValue(type, List.of(), null, true);
    }

    public static ConfigValue ofPossible(Class<?> type, Object defaultValue, List<Object> values) {
        return new ConfigValue(type, values, defaultValue, defaultValue == null);
    }

    public ConfigValue change(Object value) {
        if (!fits(value)) {
            throw new IllegalStateException("Required type %s, not %s".formatted(type, value.getClass()));
        }
        return new ConfigValue(type, valueNarrowing, value, nullable);
    }

    public Object commonParse(Object value) {
        if (Boolean.class.equals(type) && value instanceof String stringVal) {
            return Boolean.parseBoolean(stringVal.toLowerCase());
        }
        return value;
    }

    /**
     * Получить сырое значение (Object).
     * При необходимости можно добавить методы asString(), asMap() и т.д.
     */
    public Object getValue() {
        return value;
    }

    public boolean asBoolean() {
        return (boolean) value;
    }

    public int asInt() {
        return (int) value;
    }

    public long asLong() {
        return (long) value;
    }

    public double asDouble() {
        return (long) value;
    }

    public String asString() {
        return (String) value;
    }

    public List<?> asList() {
        return (List<?>) value;
    }

    public Map<?, ?> asMap() {
        return (Map<?, ?>) value;
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isNullable() {
        return nullable;
    }

    public boolean isNull() {
        return value == null;
    }

    private boolean fits(Object value) {
        if (value == null && isNullable()) {
            return true;
        }
        if (!type.isInstance(value)) {
            return false;
        }
        if (value instanceof List<?> | value instanceof Map<?, ?>) {
            return fitsSupportedTypes(value);
        }
        return true;
    }

    public static boolean fitsSupportedTypes(Class<?> clazz) {
        if (clazz == null) return false;

        // 1. Простые типы: Строки, Числа, Булевы значения
        if (String.class.isAssignableFrom(clazz) ||
                Number.class.isAssignableFrom(clazz) ||
                Boolean.class.isAssignableFrom(clazz)) {
            return true;
        }

        // 2. Примитивы (int, double, boolean и т.д.)
        if (clazz.isPrimitive()) {
            return true;
        }

        // 3. Коллекции (JSON Array)
        if (List.class.isAssignableFrom(clazz)) {
            return true;
        }

        // 4. Карты (JSON Object)
        if (Map.class.isAssignableFrom(clazz)) {
            return true;
        }

        return false;
    }

    public static boolean fitsSupportedTypes(Object value) {
        // 1. Разрешаем null (в JSON null допустим)
        if (value == null) {
            return true;
        }

        // 2. Примитивы
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return true;
        }

        // 3. Списки (JSON Array)
        if (value instanceof List<?>) {
            for (Object element : (List<?>) value) {
                if (!fitsSupportedTypes(element)) { // Рекурсивная проверка
                    return false;
                }
            }
            return true;
        }

        // 4. Map (JSON Object) -> Ключи должны быть String
        if (value instanceof Map<?, ?>) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!(entry.getKey() instanceof String)) {
                    return false; // Ключ в JSON обязан быть строкой
                }
                if (!fitsSupportedTypes(entry.getValue())) { // Рекурсивная проверка значения
                    return false;
                }
            }
            return true;
        }

        return false;
    }

    public static ConfigValue fromJson(JsonElement jsonElement) {
        return new ConfigValue(unwrapJson(jsonElement));
    }

    /**
     * Вспомогательный метод для превращения Gson-типов в Java-типы (Map, List, Primitives)
     */
    protected static Object unwrapJson(JsonElement element) {
        if (element.isJsonNull()) {
            return null;
        } else if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            } else if (primitive.isNumber()) {
                return primitive.getAsNumber();
            } else {
                return primitive.getAsString();
            }
        } else if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement el : element.getAsJsonArray()) {
                list.add(unwrapJson(el)); // Рекурсия
            }
            return list;
        } else if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                map.put(entry.getKey(), unwrapJson(entry.getValue())); // Рекурсия
            }
            return map;
        }
        throw new IllegalArgumentException("Unknown JsonElement type: " + element);
    }

    public JsonElement asJsonElement() {
        return wrapJson(this.value);
    }

    /**
     * Вспомогательный метод для превращения Java-типов обратно в Gson
     */
    private static JsonElement wrapJson(Object obj) {
        if (obj == null) {
            return JsonNull.INSTANCE;
        }

        if (obj instanceof String) {
            return new JsonPrimitive((String) obj);
        } else if (obj instanceof Number) {
            return new JsonPrimitive((Number) obj);
        } else if (obj instanceof Boolean) {
            return new JsonPrimitive((Boolean) obj);
        } else if (obj instanceof List) {
            JsonArray array = new JsonArray();
            for (Object item : (List<?>) obj) {
                array.add(wrapJson(item)); // Рекурсия
            }
            return array;
        } else if (obj instanceof Map) {
            JsonObject jsonObject = new JsonObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                // Мы уже гарантировали в fits(), что ключи - это String
                jsonObject.add((String) entry.getKey(), wrapJson(entry.getValue())); // Рекурсия
            }
            return jsonObject;
        }

        throw new IllegalStateException("Unexpected type in ConfigValue: " + obj.getClass());
    }

    @Override
    public String toString() {
        return "ConfigValue{" + value + "}";
    }
}