package org.vstu.meaningtree.utils;

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
}
