package org.vstu.meaningtree.languages.helpers.templates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ClasspathTemplateRepository implements TemplateRepository {
    private final String basePath;
    private final ClassLoader classLoader;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    public ClasspathTemplateRepository(String basePath) {
        this(basePath, Thread.currentThread().getContextClassLoader());
    }

    public ClasspathTemplateRepository(String basePath, ClassLoader classLoader) {
        this.basePath = normalizeBasePath(basePath);
        this.classLoader = Objects.requireNonNullElseGet(classLoader, ClasspathTemplateRepository.class::getClassLoader);
    }

    @Override
    public String getTemplateSource(String templateKey) {
        String normalized = normalizeTemplateKey(templateKey);
        return templateCache.computeIfAbsent(normalized, this::loadFromClasspath);
    }

    private String loadFromClasspath(String templateKey) {
        String fullPath = basePath + templateKey;
        try (InputStream stream = classLoader.getResourceAsStream(fullPath)) {
            if (stream == null) {
                throw new IllegalArgumentException("Template not found in classpath: " + fullPath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read template from classpath: " + fullPath, e);
        }
    }

    private static String normalizeBasePath(String basePath) {
        if (basePath == null || basePath.isBlank()) {
            return "";
        }
        String normalized = basePath.replace("\\", "/");
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }
        return normalized;
    }

    private static String normalizeTemplateKey(String templateKey) {
        if (templateKey == null || templateKey.isBlank()) {
            throw new IllegalArgumentException("Template key cannot be null or blank");
        }
        String normalized = templateKey.replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
