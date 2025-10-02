package org.vstu.meaningtree.utils;

import java.util.regex.Pattern;

public class TransliterationUtils {
    public static String camelToSnake(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        // Заменяем каждую заглавную букву на "_" + строчную
        String result = input
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
                .toLowerCase();
        return result;
    }

    public static String snakeToCamel(String snake) {
        if (snake == null || snake.isEmpty()) {
            return snake;
        }

        StringBuilder result = new StringBuilder();
        boolean toUpper = false;

        for (char c : snake.toCharArray()) {
            if (c == '_') {
                toUpper = true;
            } else {
                if (toUpper) {
                    result.append(Character.toUpperCase(c));
                    toUpper = false;
                } else {
                    result.append(c);
                }
            }
        }

        return result.toString();
    }

    public static String toSlug(String input, char separator) {
        if (input == null) {
            return null;
        }

        // Приводим к нижнему регистру и убираем лишние пробелы
        String normalized = input.trim().toLowerCase();

        StringBuilder slug = new StringBuilder();

        for (char c : normalized.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                slug.append(c);
            } else if (Character.isWhitespace(c)) {
                // Заменяем пробелы на выбранный символ
                slug.append(separator);
            }
            // Все остальные символы (.,!?: и т.п.) игнорируем
        }

        // Убираем возможные повторяющиеся разделители
        return slug.toString().replaceAll(Pattern.quote(String.valueOf(separator)) + "+", String.valueOf(separator));
    }
}
