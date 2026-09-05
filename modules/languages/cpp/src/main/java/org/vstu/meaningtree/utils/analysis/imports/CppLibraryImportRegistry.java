package org.vstu.meaningtree.utils.analysis.imports;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.StringType;
import org.vstu.meaningtree.nodes.types.containers.*;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Заголовки стандартной библиотеки C++, без которых сгенерированный код не соберётся.
 * <p>
 * Реестр живёт в модуле языка: заголовки — знание про C++, и в общем модуле им делать нечего.
 * Заголовки вообще нужны только здесь — Java печатает коллекции полными именами, а в Python
 * они встроенные, так что соответствие принципиально одностороннее.
 * <p>
 * Реестр строк, а не узлов: заводить семантический узел под каждый тип — цена, несопоставимая с
 * задачей, потому что все эти конструкции уже типизированы в той самой точке, где вьюер
 * собирается их напечатать.
 * <p>
 * Здесь только соответствия «тип → заголовок» и «библиотечный модуль чужого языка → заголовок».
 * Всё, что известно про функции стандартной библиотеки, включая их заголовки, живёт одной
 * записью на функцию в {@link org.vstu.meaningtree.utils.analysis.library.CppStandardLibrary}:
 * пока заголовок функции лежал здесь, а её сигнатура — в другой таблице, знание про одну и ту же
 * функцию было разложено по двум местам с одним ключом.
 */
public final class CppLibraryImportRegistry {
    private CppLibraryImportRegistry() {}

    /**
     * Заголовки под библиотечные модули других языков — только там, где соответствие
     * действительно однозначно. Таблица нарочно короткая: выдуманное соответствие даёт код,
     * который не собирается, а пропуск — код, которому не хватает одной строки.
     */
    private static final Map<String, String> LIBRARY_MODULE_HEADERS = Map.ofEntries(
            // Python
            Map.entry("math", "cmath"),
            Map.entry("cmath", "cmath"),
            Map.entry("random", "random"),
            Map.entry("string", "string"),
            Map.entry("array", "array"),
            Map.entry("queue", "queue"),
            // Java
            Map.entry("java.lang.Math", "cmath"),
            Map.entry("java.util.List", "vector"),
            Map.entry("java.util.ArrayList", "vector"),
            Map.entry("java.util.Vector", "vector"),
            Map.entry("java.util.Set", "set"),
            Map.entry("java.util.HashSet", "set"),
            Map.entry("java.util.TreeSet", "set"),
            Map.entry("java.util.Map", "unordered_map"),
            Map.entry("java.util.HashMap", "unordered_map"),
            Map.entry("java.util.TreeMap", "map"),
            Map.entry("java.util.Arrays", "algorithm"),
            Map.entry("java.util.Collections", "algorithm")
    );

    /**
     * Заголовки стандартной библиотеки Си (C17). Список закрытый и полный — в этом и смысл:
     * решать, допустимо ли подключение в C-режиме, перечислением C++-заголовков нельзя, их
     * множество открытое и растёт с каждой ревизией стандарта. Всё, чего здесь нет, Си-заголовком
     * не является.
     */
    private static final Set<String> C_STANDARD_HEADERS = Set.of(
            "assert.h", "complex.h", "ctype.h", "errno.h", "fenv.h", "float.h", "inttypes.h",
            "iso646.h", "limits.h", "locale.h", "math.h", "setjmp.h", "signal.h", "stdalign.h",
            "stdarg.h", "stdatomic.h", "stdbool.h", "stddef.h", "stdint.h", "stdio.h", "stdlib.h",
            "stdnoreturn.h", "string.h", "tgmath.h", "threads.h", "time.h", "uchar.h", "wchar.h",
            "wctype.h"
    );

    /**
     * C++-написания Си-заголовков: {@code <cstdio>} — это тот же {@code <stdio.h>}, недоступный
     * под этим именем в Си. Такое подключение не выбрасывается, а переписывается: содержимое
     * заголовка коду по-прежнему нужно.
     */
    private static final Map<String, String> C_HEADER_SPELLINGS = C_STANDARD_HEADERS.stream()
            .filter(header -> !Set.of("stdalign.h", "stdatomic.h", "stdbool.h", "stdnoreturn.h",
                    "threads.h", "iso646.h").contains(header))
            .collect(Collectors.toUnmodifiableMap(
                    header -> "c" + header.substring(0, header.length() - ".h".length()),
                    header -> header));

    /**
     * Заголовок, без которого не соберётся тип, напечатанный вьюером.
     * <p>
     * Разбор идёт от частного к общему ровно в том же порядке, в котором {@code CppViewer}
     * выбирает сам тип: иначе таблица разошлась бы с выводом и подключала не тот заголовок.
     *
     * @return имя заголовка без угловых скобок
     */
    public static Optional<String> headerForType(Type type) {
        return Optional.ofNullable(switch (type) {
            case ArrayType ignored -> "array";
            case UnmodifiableListType ignored -> "array";
            case SetType ignored -> "set";
            case OrderedDictionaryType ignored -> "map";
            case UnorderedDictionaryType ignored -> "unordered_map";
            case PlainCollectionType ignored -> "vector";
            case StringType ignored -> "string";
            default -> null;
        });
    }


    /**
     * Заголовок под библиотечный модуль другого языка.
     *
     * @return пусто, если однозначного соответствия нет — тогда подключение лучше опустить,
     *         чем сослаться на несуществующий файл
     */
    public static Optional<String> headerForLibraryModule(String dottedName) {
        return Optional.ofNullable(LIBRARY_MODULE_HEADERS.get(dottedName));
    }

    /**
     * Допустим ли заголовок в чистом Си.
     * <p>
     * Проверка положительная — по списку Си-заголовков, а не по перечню запрещённых
     * C++-заголовков. Прежний чёрный список собирался из тех заголовков, которые этот реестр сам
     * умеет выдавать, поэтому в C-вывод беспрепятственно проходили {@code <iostream>},
     * {@code <memory>}, {@code <sstream>}, {@code <thread>} и любой другой заголовок, о котором
     * реестр не знает: получался Си-файл, который не собирается.
     */
    public static boolean isCStandardHeader(String header) {
        return C_STANDARD_HEADERS.contains(header);
    }

    /**
     * Си-написание заголовка, если это C++-написание Си-заголовка ({@code cstdio} →
     * {@code stdio.h}).
     *
     * @return пусто, если заголовок не имеет Си-эквивалента
     */
    public static Optional<String> cSpellingOf(String header) {
        return Optional.ofNullable(C_HEADER_SPELLINGS.get(header));
    }
}
