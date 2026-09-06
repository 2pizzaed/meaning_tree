package org.vstu.meaningtree.utils.analysis.library;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.vstu.meaningtree.utils.analysis.library.LibrarySymbolKind.CONSTANT;
import static org.vstu.meaningtree.utils.analysis.library.LibrarySymbolKind.FUNCTION;
import static org.vstu.meaningtree.utils.analysis.library.LibrarySymbolKind.OBJECT;
import static org.vstu.meaningtree.utils.analysis.library.LibrarySymbolKind.TYPE;

/**
 * Стандартная библиотека C и C++: заголовок, род имени и — у функции — сигнатура, одной записью
 * на имя.
 * <p>
 * Реализация {@link StandardLibrary} живёт в модуле языка: набор имён и написания заголовков —
 * знание про C и C++, и в общем модуле ему делать нечего. Общей остаётся сама абстракция: вопрос
 * «что известно про это библиотечное имя» одинаков для всех языков, а C++ отвечает на него
 * заголовками, Python и Java отвечали бы модулями.
 * <p>
 * Раньше это знание было разложено по трём несвязанным таблицам с одним ключом: одна знала
 * заголовок функции, другая — типы её параметров, третья (в реестре импортов) — состав
 * заголовка целиком. Ни одна не была полна, добавление имени требовало помнить про все, а
 * ответить «какие имена даёт {@code <cstdio>}» было нельзя ни по одной.
 *
 * <h2>Полнота состава</h2>
 * Заголовки {@code <cstdio>} и {@code <cstring>} объявлены полными: перечислены все имена,
 * которые они дают, включая типы, макросы и объекты. Полнота нужна тому, кто решает, что
 * заголовок больше не нужен ({@code CppViewer.dropSupersededHeaders}): пропущенное имя означало
 * бы выброшенное подключение при живом обращении, то есть код, который не собирается. Остальные
 * заголовки описаны настолько, насколько понадобилось, и полными не объявлены — про них такой
 * вопрос просто не задаётся.
 * <p>
 * Написания здесь C++-ные ({@code cstdio}, а не {@code stdio.h}): это те имена, которые печатает
 * вьюер. Си-написание получается из них через {@code CppLibraryImportRegistry.cSpellingOf}.
 * <p>
 * Класс без полей экземпляра и с неизменяемой таблицей, поэтому один экземпляр безопасно держать
 * в {@code LanguageBehavior} и разделять между всеми трансляциями языка.
 */
public final class CppStandardLibrary extends IndexedStandardLibrary {
    public CppStandardLibrary() {
        super(SYMBOLS, Set.of("cstdio", "cstring"));
    }

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

    /** Функция с описанной сигнатурой. */
    @SafeVarargs
    private static LibrarySymbol fn(String name, String unit, Supplier<Type> returnType,
                                    Supplier<Type>... parameters) {
        return new LibrarySymbol(name, List.of(unit), FUNCTION,
                new LibraryFunction(returnType, List.of(parameters)));
    }

    /**
     * Функция, сигнатура которой не описана: известны имя и заголовок, а типы — нет. Это не то
     * же самое, что неизвестная функция: заголовок под неё подключается, и в составе своего
     * заголовка она числится.
     */
    private static LibrarySymbol fn(String name, String unit) {
        return new LibrarySymbol(name, List.of(unit), FUNCTION, null);
    }

    /** Функция, тип которой не выразим: шаблон. Сигнатура известна — известно, что типов нет. */
    private static LibrarySymbol template(String name, String unit) {
        return new LibrarySymbol(name, List.of(unit), FUNCTION, new LibraryFunction(null, List.of()));
    }

    private static LibrarySymbol type(String name, String... units) {
        return new LibrarySymbol(name, List.of(units), TYPE, null);
    }

    private static LibrarySymbol constant(String name, String... units) {
        return new LibrarySymbol(name, List.of(units), CONSTANT, null);
    }

    private static LibrarySymbol object(String name, String unit) {
        return new LibrarySymbol(name, List.of(unit), OBJECT, null);
    }

    private static final List<LibrarySymbol> SYMBOLS = symbols();

    private static List<LibrarySymbol> symbols() {
        List<LibrarySymbol> symbols = new ArrayList<>();

        // <cmath>
        symbols.addAll(List.of(
                fn("pow", "cmath", real(), real(), real()),
                fn("sqrt", "cmath", real(), real()),
                fn("cbrt", "cmath", real(), real()),
                fn("exp", "cmath", real(), real()),
                fn("log", "cmath", real(), real()),
                fn("log2", "cmath", real(), real()),
                fn("log10", "cmath", real(), real()),
                fn("sin", "cmath", real(), real()),
                fn("cos", "cmath", real(), real()),
                fn("tan", "cmath", real(), real()),
                fn("asin", "cmath", real(), real()),
                fn("acos", "cmath", real(), real()),
                fn("atan", "cmath", real(), real()),
                fn("atan2", "cmath", real(), real(), real()),
                fn("ceil", "cmath", real(), real()),
                fn("floor", "cmath", real(), real()),
                fn("round", "cmath", real(), real()),
                fn("trunc", "cmath", real(), real()),
                fn("fmod", "cmath", real(), real(), real()),
                fn("hypot", "cmath", real(), real(), real()),
                fn("fabs", "cmath", real(), real())));

        // <cstdlib>
        symbols.addAll(List.of(
                fn("abs", "cstdlib", integer(), integer()),
                fn("atoi", "cstdlib", integer(), constCString()),
                fn("atof", "cstdlib", real(), constCString()),
                fn("atol", "cstdlib", integer(), constCString()),
                fn("atoll", "cstdlib", integer(), constCString()),
                fn("strtol", "cstdlib", integer(), constCString(), opaque(), integer()),
                fn("strtoll", "cstdlib", integer(), constCString(), opaque(), integer()),
                fn("strtod", "cstdlib", real(), constCString(), opaque()),
                fn("strtof", "cstdlib", real(), constCString(), opaque())));

        // <algorithm>: шаблоны, типы зависят от аргументов
        symbols.addAll(List.of(
                template("min", "algorithm"),
                template("max", "algorithm")));

        // <cwchar>
        symbols.addAll(List.of(
                fn("wcscpy", "cwchar", wideString(), wideString(), wideString()),
                fn("wcscat", "cwchar", wideString(), wideString(), wideString()),
                fn("wcscmp", "cwchar", integer(), wideString(), wideString()),
                fn("wcslen", "cwchar", size(), wideString())));

        symbols.addAll(cstdio());
        symbols.addAll(cstring());

        // Имена, которые дают сразу несколько заголовков. Канонический — <cstddef>: именно его
        // подключают ради них самих, остальные дают их попутно
        symbols.addAll(List.of(
                type("size_t", "cstddef", "cstdio", "cstring"),
                constant("NULL", "cstddef", "cstdio", "cstring")));

        return List.copyOf(symbols);
    }

    /**
     * Состав {@code <cstdio>} целиком (C17 вместе с Annex K). Сигнатуры описаны у тех функций,
     * чьи типы понадобились анализу; остальные записаны без сигнатуры — заголовок и род имени
     * известны и без неё.
     */
    private static List<LibrarySymbol> cstdio() {
        return List.of(
                fn("fputs", "cstdio", integer(), constCString(), opaque()),
                fn("fgets", "cstdio", cString(), cString(), integer(), opaque()),
                fn("sprintf", "cstdio", integer(), cString(), constCString()),
                fn("snprintf", "cstdio", integer(), cString(), size(), constCString()),
                fn("sscanf", "cstdio", integer(), constCString(), constCString()),
                fn("perror", "cstdio", NoReturn::new, constCString()),

                fn("remove", "cstdio"), fn("rename", "cstdio"),
                fn("tmpfile", "cstdio"), fn("tmpfile_s", "cstdio"),
                fn("tmpnam", "cstdio"), fn("tmpnam_s", "cstdio"),
                fn("fclose", "cstdio"), fn("fflush", "cstdio"),
                fn("fopen", "cstdio"), fn("fopen_s", "cstdio"),
                fn("freopen", "cstdio"), fn("freopen_s", "cstdio"),
                fn("setbuf", "cstdio"), fn("setvbuf", "cstdio"),
                fn("fprintf", "cstdio"), fn("fprintf_s", "cstdio"),
                fn("fscanf", "cstdio"), fn("fscanf_s", "cstdio"),
                fn("printf", "cstdio"), fn("printf_s", "cstdio"),
                fn("scanf", "cstdio"), fn("scanf_s", "cstdio"),
                fn("snprintf_s", "cstdio"), fn("sprintf_s", "cstdio"),
                fn("sscanf_s", "cstdio"),
                fn("vfprintf", "cstdio"), fn("vfprintf_s", "cstdio"),
                fn("vfscanf", "cstdio"), fn("vfscanf_s", "cstdio"),
                fn("vprintf", "cstdio"), fn("vprintf_s", "cstdio"),
                fn("vscanf", "cstdio"), fn("vscanf_s", "cstdio"),
                fn("vsnprintf", "cstdio"), fn("vsnprintf_s", "cstdio"),
                fn("vsprintf", "cstdio"), fn("vsprintf_s", "cstdio"),
                fn("vsscanf", "cstdio"), fn("vsscanf_s", "cstdio"),
                fn("fgetc", "cstdio"), fn("fputc", "cstdio"),
                fn("getc", "cstdio"), fn("getchar", "cstdio"),
                fn("gets", "cstdio"), fn("gets_s", "cstdio"),
                fn("putc", "cstdio"), fn("putchar", "cstdio"),
                fn("puts", "cstdio"), fn("ungetc", "cstdio"),
                fn("fread", "cstdio"), fn("fwrite", "cstdio"),
                fn("fgetpos", "cstdio"), fn("fsetpos", "cstdio"),
                fn("fseek", "cstdio"), fn("ftell", "cstdio"), fn("rewind", "cstdio"),
                fn("clearerr", "cstdio"), fn("feof", "cstdio"), fn("ferror", "cstdio"),

                type("FILE", "cstdio"), type("fpos_t", "cstdio"),

                constant("EOF", "cstdio"), constant("BUFSIZ", "cstdio"),
                constant("FOPEN_MAX", "cstdio"), constant("FILENAME_MAX", "cstdio"),
                constant("L_tmpnam", "cstdio"), constant("L_tmpnam_s", "cstdio"),
                constant("SEEK_CUR", "cstdio"), constant("SEEK_END", "cstdio"),
                constant("SEEK_SET", "cstdio"), constant("TMP_MAX", "cstdio"),
                constant("TMP_MAX_S", "cstdio"),

                object("stdin", "cstdio"), object("stdout", "cstdio"), object("stderr", "cstdio"));
    }

    /**
     * Состав {@code <cstring>} целиком (C17 вместе с Annex K). У {@code memcpy}, {@code memset}
     * и прочих {@code mem*} сигнатуры нет намеренно: они принимают {@code void *} и о
     * строковости своих аргументов не говорят ничего, а в составе заголовка числятся наравне с
     * остальными.
     */
    private static List<LibrarySymbol> cstring() {
        return List.of(
                fn("strcpy", "cstring", cString(), cString(), constCString()),
                fn("strncpy", "cstring", cString(), cString(), constCString(), size()),
                fn("strcat", "cstring", cString(), cString(), constCString()),
                fn("strncat", "cstring", cString(), cString(), constCString(), size()),
                fn("strcmp", "cstring", integer(), constCString(), constCString()),
                fn("strncmp", "cstring", integer(), constCString(), constCString(), size()),
                fn("strcoll", "cstring", integer(), constCString(), constCString()),
                fn("strxfrm", "cstring", size(), cString(), constCString(), size()),
                fn("strlen", "cstring", size(), constCString()),
                fn("strchr", "cstring", cString(), constCString(), integer()),
                fn("strrchr", "cstring", cString(), constCString(), integer()),
                fn("strspn", "cstring", size(), constCString(), constCString()),
                fn("strcspn", "cstring", size(), constCString(), constCString()),
                fn("strpbrk", "cstring", cString(), constCString(), constCString()),
                fn("strstr", "cstring", cString(), constCString(), constCString()),
                fn("strtok", "cstring", cString(), cString(), constCString()),
                fn("strerror", "cstring", cString(), integer()),
                fn("strdup", "cstring", cString(), constCString()),
                fn("strndup", "cstring", cString(), constCString(), size()),

                fn("memcpy", "cstring"), fn("memcpy_s", "cstring"),
                fn("memccpy", "cstring"),
                fn("memmove", "cstring"), fn("memmove_s", "cstring"),
                fn("memcmp", "cstring"), fn("memchr", "cstring"),
                fn("memset", "cstring"), fn("memset_s", "cstring"),
                fn("memset_explicit", "cstring"),
                fn("strcpy_s", "cstring"), fn("strncpy_s", "cstring"),
                fn("strcat_s", "cstring"), fn("strncat_s", "cstring"),
                fn("strtok_s", "cstring"),
                fn("strerror_s", "cstring"), fn("strerrorlen_s", "cstring"),
                fn("strnlen_s", "cstring"),

                type("rsize_t", "cstring"), type("errno_t", "cstring"));
    }
}
