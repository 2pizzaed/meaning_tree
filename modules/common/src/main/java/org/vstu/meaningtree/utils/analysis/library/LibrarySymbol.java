package org.vstu.meaningtree.utils.analysis.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Одна запись стандартной библиотеки: имя, единицы подключения, которые его дают, и — у
 * функции — сигнатура.
 * <p>
 * Запись одна на имя, и это главное свойство типа. Знание про одно и то же имя, разложенное по
 * нескольким таблицам с одним ключом, неизбежно оказывается неполным в каждой: добавляющий
 * помнит про ту таблицу, ради которой пришёл. Раньше так и было — заголовок функции лежал в
 * реестре импортов, сигнатура в таблице функций, а состав заголовка (нефункциональные имена
 * вроде {@code FILE}) в третьем месте.
 *
 * @param name      имя, под которым к сущности обращаются в коде
 * @param units     единицы подключения, дающие это имя: заголовки в C++, модули в Python или
 *                  Java. Порядок значим — первая считается канонической и подключается, когда
 *                  надо выбрать одну (см. {@link #canonicalUnit()}). Пустой список означает,
 *                  что подключать нечего: имя встроено в язык
 * @param kind      род имени; сигнатура допустима только у {@link LibrarySymbolKind#FUNCTION}
 * @param signature что известно про сигнатуру функции; {@code null} — не описана. Отсутствие
 *                  описания не делает имя неизвестным: единица подключения и род известны и
 *                  без него
 */
public record LibrarySymbol(@NotNull String name,
                            @NotNull List<String> units,
                            @NotNull LibrarySymbolKind kind,
                            @Nullable LibraryFunction signature) {

    public LibrarySymbol {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        units = List.copyOf(Objects.requireNonNull(units, "units must not be null"));
        if (signature != null && kind != LibrarySymbolKind.FUNCTION) {
            throw new IllegalArgumentException("Signature belongs to a function, not to " + kind + ": " + name);
        }
    }

    /**
     * Единица, которую подключают ради этого имени, когда нужно выбрать одну: {@code NULL} даёт
     * не один заголовок, а подключить нужно какой-то определённый.
     *
     * @return пусто, если подключать нечего
     */
    public Optional<String> canonicalUnit() {
        return units.isEmpty() ? Optional.empty() : Optional.of(units.getFirst());
    }

    /** Даёт ли эта единица подключения такое имя. */
    public boolean belongsTo(@NotNull String unit) {
        return units.contains(unit);
    }
}
