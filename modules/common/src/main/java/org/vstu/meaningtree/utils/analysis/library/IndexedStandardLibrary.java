package org.vstu.meaningtree.utils.analysis.library;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Библиотека, описанная списком записей: индексы по имени и по единице подключения строятся
 * один раз здесь, а язык пишет только сам список.
 * <p>
 * Индексы, а не поиск по списку: {@link #symbol(String)} спрашивают на каждом печатаемом вызове.
 * <p>
 * Повтор имени в списке — ошибка построения, а не последняя запись побеждает: имя описывается
 * ровно один раз, со всеми своими единицами сразу, иначе единый источник перестаёт быть единым
 * незаметно для того, кто добавлял вторую запись.
 */
public class IndexedStandardLibrary implements StandardLibrary {
    private final Map<String, LibrarySymbol> byName;
    private final Map<String, Set<String>> namesByUnit;
    private final Set<String> completeUnits;

    public IndexedStandardLibrary(@NotNull Collection<LibrarySymbol> symbols,
                                  @NotNull Set<String> completeUnits) {
        Map<String, LibrarySymbol> names = new HashMap<>(symbols.size());
        Map<String, Set<String>> units = new HashMap<>();
        for (LibrarySymbol symbol : symbols) {
            LibrarySymbol previous = names.put(symbol.name(), symbol);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Standard library describes the same name twice: " + symbol.name());
            }
            for (String unit : symbol.units()) {
                units.computeIfAbsent(unit, ignored -> new HashSet<>()).add(symbol.name());
            }
        }
        for (String unit : completeUnits) {
            if (!units.containsKey(unit)) {
                throw new IllegalArgumentException(
                        "Unit is declared complete, but no symbol belongs to it: " + unit);
            }
        }
        this.byName = Map.copyOf(names);
        this.namesByUnit = units.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue())));
        this.completeUnits = Set.copyOf(completeUnits);
    }

    @Override
    public Optional<LibrarySymbol> symbol(@NotNull String name) {
        return Optional.ofNullable(byName.get(name));
    }

    @Override
    public Set<String> completeUnits() {
        return completeUnits;
    }

    @Override
    public Optional<Set<String>> namesOf(@NotNull String unit) {
        return completeUnits.contains(unit) ? Optional.ofNullable(namesByUnit.get(unit)) : Optional.empty();
    }
}
