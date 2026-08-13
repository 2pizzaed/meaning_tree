package org.vstu.meaningtree;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.regex.Pattern;

final class TestCaseOptions {
    private static final Pattern CONFIGURATION_ENTRY =
            Pattern.compile("(^|,)\\h*([A-Za-z_][A-Za-z0-9_]*)\\h*=");

    private TestCaseOptions() {
    }

    static JsonObject parseConfiguration(String rawOptions, String header) {
        if (rawOptions == null || rawOptions.isBlank()) {
            return new JsonObject();
        }

        String json = "{%s}".formatted(
                CONFIGURATION_ENTRY.matcher(rawOptions).replaceAll("$1\\\"$2\\\":")
        );
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "Configuration options must be valid JSON values in " + header, e
            );
        }
    }

    static JsonObject mergeConfiguration(JsonObject base, JsonObject overrides) {
        JsonObject result = base.deepCopy();
        overrides.entrySet().forEach(entry -> result.add(entry.getKey(), entry.getValue().deepCopy()));
        return result;
    }
}
