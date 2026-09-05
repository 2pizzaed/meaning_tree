package org.vstu.meaningtree.utils.analysis.library;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Стандартная библиотека C и C++: заголовок, возвращаемый тип и типы параметров — одной записью
 * на функцию.
 * <p>
 * Реализация {@link StandardLibrary} живёт в модуле языка: набор функций и написания заголовков —
 * знание про C и C++, и в общем модуле ему делать нечего. Общей остаётся сама абстракция: вопрос
 * «что известно про эту библиотечную функцию» одинаков для всех языков, а C++ отвечает на него
 * заголовками, Python и Java отвечали бы модулями.
 * <p>
 * Раньше это знание было разложено по двум несвязанным таблицам с одинаковым ключом: одна знала
 * заголовок ({@code pow} → {@code <cmath>}), другая — какие параметры являются строкой. Ни одна
 * из них не была полна, добавление функции требовало помнить про обе, а ответить на вопрос
 * «какого типа второй параметр {@code snprintf}» было нельзя ни по одной.
 * <p>
 * Класс без полей экземпляра и с неизменяемой таблицей, поэтому один экземпляр безопасно держать
 * в {@code LanguageBehavior} и разделять между всеми трансляциями языка.
 */
public final class CppStandardLibrary implements StandardLibrary {

    private static Supplier<Type> cString() {
        return () -> new StringType(8, true, false, null);
    }

    private static Supplier<Type> constCString() {
        return () -> {
            StringType type = new StringType(8, true, false, null);
            type.setConst(true);
            return type;
        };
    }

    private static Supplier<Type> wideString() {
        return () -> new StringType(16, true, false, null);
    }

    private static Supplier<Type> integer() {
        return IntType::new;
    }

    /** {@code size_t} и прочие беззнаковые счётчики: для анализа важна беззнаковость, не ширина. */
    private static Supplier<Type> size() {
        return () -> new IntType(true);
    }

    private static Supplier<Type> real() {
        return () -> new FloatType(64);
    }

    /** Тип, которого в семантической модели нет ({@code FILE *}, {@code char **}). */
    private static Supplier<Type> opaque() {
        return () -> new PointerType(new UnknownType());
    }

    @SafeVarargs
    private static LibraryFunction fn(String header, Supplier<Type> returnType,
                                      Supplier<Type>... parameters) {
        return new LibraryFunction(header, returnType, List.of(parameters));
    }

    /** Функция, тип которой не выразим: шаблон. */
    private static LibraryFunction template(String header) {
        return new LibraryFunction(header, null, List.of());
    }

    private static final Map<String, LibraryFunction> FUNCTIONS = Map.ofEntries(
            // <cmath>
            Map.entry("pow", fn("cmath", real(), real(), real())),
            Map.entry("sqrt", fn("cmath", real(), real())),
            Map.entry("cbrt", fn("cmath", real(), real())),
            Map.entry("exp", fn("cmath", real(), real())),
            Map.entry("log", fn("cmath", real(), real())),
            Map.entry("log2", fn("cmath", real(), real())),
            Map.entry("log10", fn("cmath", real(), real())),
            Map.entry("sin", fn("cmath", real(), real())),
            Map.entry("cos", fn("cmath", real(), real())),
            Map.entry("tan", fn("cmath", real(), real())),
            Map.entry("asin", fn("cmath", real(), real())),
            Map.entry("acos", fn("cmath", real(), real())),
            Map.entry("atan", fn("cmath", real(), real())),
            Map.entry("atan2", fn("cmath", real(), real(), real())),
            Map.entry("ceil", fn("cmath", real(), real())),
            Map.entry("floor", fn("cmath", real(), real())),
            Map.entry("round", fn("cmath", real(), real())),
            Map.entry("trunc", fn("cmath", real(), real())),
            Map.entry("fmod", fn("cmath", real(), real(), real())),
            Map.entry("hypot", fn("cmath", real(), real(), real())),
            Map.entry("fabs", fn("cmath", real(), real())),
            // <cstdlib>
            Map.entry("abs", fn("cstdlib", integer(), integer())),
            Map.entry("atoi", fn("cstdlib", integer(), constCString())),
            Map.entry("atof", fn("cstdlib", real(), constCString())),
            Map.entry("atol", fn("cstdlib", integer(), constCString())),
            Map.entry("atoll", fn("cstdlib", integer(), constCString())),
            Map.entry("strtol", fn("cstdlib", integer(), constCString(), opaque(), integer())),
            Map.entry("strtoll", fn("cstdlib", integer(), constCString(), opaque(), integer())),
            Map.entry("strtod", fn("cstdlib", real(), constCString(), opaque())),
            Map.entry("strtof", fn("cstdlib", real(), constCString(), opaque())),
            // <algorithm>: шаблоны, типы зависят от аргументов
            Map.entry("min", template("algorithm")),
            Map.entry("max", template("algorithm")),
            // <cstring>. Функции memcpy/memset/memcmp сюда не входят намеренно: они принимают
            // void * и о строковости не говорят ничего
            Map.entry("strcpy", fn("cstring", cString(), cString(), constCString())),
            Map.entry("strncpy", fn("cstring", cString(), cString(), constCString(), size())),
            Map.entry("strcat", fn("cstring", cString(), cString(), constCString())),
            Map.entry("strncat", fn("cstring", cString(), cString(), constCString(), size())),
            Map.entry("strcmp", fn("cstring", integer(), constCString(), constCString())),
            Map.entry("strncmp", fn("cstring", integer(), constCString(), constCString(), size())),
            Map.entry("strcoll", fn("cstring", integer(), constCString(), constCString())),
            Map.entry("strxfrm", fn("cstring", size(), cString(), constCString(), size())),
            Map.entry("strlen", fn("cstring", size(), constCString())),
            Map.entry("strchr", fn("cstring", cString(), constCString(), integer())),
            Map.entry("strrchr", fn("cstring", cString(), constCString(), integer())),
            Map.entry("strspn", fn("cstring", size(), constCString(), constCString())),
            Map.entry("strcspn", fn("cstring", size(), constCString(), constCString())),
            Map.entry("strpbrk", fn("cstring", cString(), constCString(), constCString())),
            Map.entry("strstr", fn("cstring", cString(), constCString(), constCString())),
            Map.entry("strtok", fn("cstring", cString(), cString(), constCString())),
            Map.entry("strerror", fn("cstring", cString(), integer())),
            Map.entry("strdup", fn("cstring", cString(), constCString())),
            Map.entry("strndup", fn("cstring", cString(), constCString(), size())),
            // <cwchar>
            Map.entry("wcscpy", fn("cwchar", wideString(), wideString(), wideString())),
            Map.entry("wcscat", fn("cwchar", wideString(), wideString(), wideString())),
            Map.entry("wcscmp", fn("cwchar", integer(), wideString(), wideString())),
            Map.entry("wcslen", fn("cwchar", size(), wideString())),
            // <cstdio>
            Map.entry("fputs", fn("cstdio", integer(), constCString(), opaque())),
            Map.entry("fgets", fn("cstdio", cString(), cString(), integer(), opaque())),
            Map.entry("sprintf", fn("cstdio", integer(), cString(), constCString())),
            Map.entry("snprintf", fn("cstdio", integer(), cString(), size(), constCString())),
            Map.entry("sscanf", fn("cstdio", integer(), constCString(), constCString())),
            Map.entry("perror", fn("cstdio", NoReturn::new, constCString()))
    );

    @Override
    public Optional<LibraryFunction> function(@NotNull String functionName) {
        return Optional.ofNullable(FUNCTIONS.get(functionName));
    }
}
