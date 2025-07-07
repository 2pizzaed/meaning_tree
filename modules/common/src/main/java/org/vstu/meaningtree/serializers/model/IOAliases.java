package org.vstu.meaningtree.serializers.model;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IOAliases<T> {
    private final List<IOAlias<T>> aliases;

    public IOAliases(List<IOAlias<T>> aliases) {
        this.aliases = List.copyOf(aliases);
    }

    public <R> Optional<R> apply(String formatName, Function<T, R> mapper) {
        return aliases.stream()
                .map(alias -> alias.apply(formatName, mapper))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public String getSupportedFormatsMessage() {
        String supportedFormats = aliases.stream()
                .map(IOAlias::getNameForDisplay)
                .collect(Collectors.joining(", "));
        return "Supported: " + supportedFormats;
    }
} 