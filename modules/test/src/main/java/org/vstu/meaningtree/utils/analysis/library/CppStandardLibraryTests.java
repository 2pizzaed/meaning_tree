package org.vstu.meaningtree.utils.analysis.library;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static java.util.Objects.requireNonNull;

class CppStandardLibraryTests {
    private static final StandardLibrary LIBRARY =
            new CppTranslator(Map.of("skipErrors", false)).getLanguageBehavior().standardLibrary();

    @Test
    void languageBehaviorCarriesTheLibrary() {
        assertTrue(LIBRARY.function("strcpy").isPresent(), "C++ describes its standard library");
    }

    /**
     * Заголовок и сигнатура — одна запись: пока они лежали в разных таблицах с одним ключом, у
     * {@code strcpy} была сигнатура без заголовка, а у {@code pow} — заголовок без сигнатуры.
     */
    @Test
    void oneEntryAnswersBothAboutHeaderAndAboutTypes() {
        LibrarySymbol strcpy = LIBRARY.symbol("strcpy").orElseThrow();
        assertEquals(LibrarySymbolKind.FUNCTION, strcpy.kind());
        assertEquals("cstring", strcpy.canonicalUnit().orElseThrow());
        LibraryFunction strcpySignature = requireNonNull(strcpy.signature());
        assertTrue(strcpySignature.isParameterOfType(0, StringType.class));
        assertTrue(strcpySignature.isParameterOfType(1, StringType.class));
        assertTrue(strcpySignature.returnsType(StringType.class));

        assertEquals("cmath", LIBRARY.unitFor("pow").orElseThrow());
        LibraryFunction pow = LIBRARY.function("pow").orElseThrow();
        assertTrue(pow.isParameterOfType(0, FloatType.class));
        assertTrue(pow.returnsType(FloatType.class));
    }

    /**
     * Заголовок нужен коду не только ради вызовов, поэтому в таблице есть и то, что не
     * вызывается: без {@code <cstdio>} не соберётся ни {@code FILE}, ни {@code EOF}, ни
     * {@code stdout}. Сигнатуры у них нет и быть не может, а единица подключения — есть.
     */
    @Test
    void namesThatAreNotFunctionsAreDescribedToo() {
        assertEquals(LibrarySymbolKind.TYPE, LIBRARY.symbol("FILE").orElseThrow().kind());
        assertEquals(LibrarySymbolKind.CONSTANT, LIBRARY.symbol("EOF").orElseThrow().kind());
        assertEquals(LibrarySymbolKind.OBJECT, LIBRARY.symbol("stdout").orElseThrow().kind());

        assertEquals("cstdio", LIBRARY.unitFor("FILE").orElseThrow());
        assertTrue(LIBRARY.function("FILE").isEmpty(), "у типа не бывает сигнатуры");
    }

    /**
     * Функция без описанной сигнатуры — не то же самое, что неизвестная: заголовок под неё
     * известен, и в составе своего заголовка она числится. Иначе состав {@code <cstdio>} нельзя
     * было бы объявить полным, не выдумав типы каждому {@code v*printf}.
     */
    @Test
    void functionWithoutADescribedSignatureStillKnowsItsHeader() {
        LibrarySymbol fopen = LIBRARY.symbol("fopen").orElseThrow();

        assertEquals(LibrarySymbolKind.FUNCTION, fopen.kind());
        assertEquals("cstdio", fopen.canonicalUnit().orElseThrow());
        assertNull(fopen.signature());
        assertTrue(LIBRARY.function("fopen").isEmpty());
    }

    /**
     * Состав отдаётся только по заголовку, объявленному полным. Для остальных ответ пуст — это
     * «неизвестно», а не «имён нет»: по неполному описанию нельзя судить, что заголовок больше
     * не нужен.
     */
    @Test
    void compositionIsAnsweredOnlyForUnitsDeclaredComplete() {
        Set<String> stdio = LIBRARY.namesOf("cstdio").orElseThrow();
        assertTrue(stdio.contains("printf"), stdio.toString());
        assertTrue(stdio.contains("FILE"), stdio.toString());
        assertTrue(stdio.contains("EOF"), stdio.toString());
        assertTrue(stdio.contains("stdin"), stdio.toString());
        assertFalse(stdio.contains("strlen"), stdio.toString());

        assertTrue(LIBRARY.namesOf("cstring").orElseThrow().contains("memcpy"));
        assertTrue(LIBRARY.completeUnits().containsAll(Set.of("cstdio", "cstring")));

        // <cmath> описан настолько, насколько понадобилось, и полным не объявлен
        assertFalse(LIBRARY.completeUnits().contains("cmath"));
        assertTrue(LIBRARY.namesOf("cmath").isEmpty());
    }

    /**
     * Имя из нескольких заголовков описано одной записью со всеми ними: {@code NULL} даёт и
     * {@code <cstdio>}, и {@code <cstring>}, и подключение любого из них снимает вопрос.
     */
    @Test
    void nameProvidedBySeveralUnitsIsOneEntry() {
        LibrarySymbol nullMacro = LIBRARY.symbol("NULL").orElseThrow();

        assertTrue(nullMacro.belongsTo("cstdio"));
        assertTrue(nullMacro.belongsTo("cstring"));
        assertEquals("cstddef", nullMacro.canonicalUnit().orElseThrow(), "подключают ради него самого");
        assertTrue(LIBRARY.namesOf("cstdio").orElseThrow().contains("NULL"));
        assertTrue(LIBRARY.namesOf("cstring").orElseThrow().contains("NULL"));
    }

    /** Повтор имени — ошибка построения: единый источник иначе перестаёт быть единым молча. */
    @Test
    void describingTheSameNameTwiceIsRejected() {
        LibrarySymbol first = new LibrarySymbol("dup", List.of("a"), LibrarySymbolKind.FUNCTION, null);
        LibrarySymbol second = new LibrarySymbol("dup", List.of("b"), LibrarySymbolKind.TYPE, null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new IndexedStandardLibrary(List.of(first, second), Set.of()));
        assertTrue(error.getMessage().contains("dup"), error.getMessage());
    }

    /** Полнота объявляется только для описанной единицы: иначе она пуста и молчаливо врёт. */
    @Test
    void completeUnitWithoutSymbolsIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new IndexedStandardLibrary(List.of(), Set.of("cstdio")));
    }

    @Test
    void parameterTypesAreDescribedBeyondStringness() {
        LibraryFunction snprintf = LIBRARY.function("snprintf").orElseThrow();

        assertTrue(snprintf.isParameterOfType(0, StringType.class), "буфер");
        IntType size = assertInstanceOf(IntType.class, snprintf.resolveParameterType(1), "размер буфера");
        assertTrue(size.isUnsigned);
        assertTrue(snprintf.isParameterOfType(2, StringType.class), "форматная строка");
    }

    /** У функции с переменным числом аргументов описаны только фиксированные параметры. */
    @Test
    void argumentBeyondFixedPartHasNoDescribedType() {
        LibraryFunction sprintf = LIBRARY.function("sprintf").orElseThrow();

        assertNull(sprintf.resolveParameterType(5));
        assertFalse(sprintf.isParameterOfType(5, StringType.class));
    }

    /** У шаблона тип зависит от аргументов, поэтому он не описан — но заголовок известен. */
    @Test
    void templateFunctionHasHeaderButNoTypes() {
        LibraryFunction min = LIBRARY.function("min").orElseThrow();

        assertEquals("algorithm", LIBRARY.unitFor("min").orElseThrow());
        assertNull(min.resolveReturnType());
        assertNull(min.resolveParameterType(0));
    }

    @Test
    void unknownFunctionIsNotAnError() {
        assertTrue(LIBRARY.symbol("totally_made_up").isEmpty());
        assertTrue(LIBRARY.function("totally_made_up").isEmpty());
        assertTrue(LIBRARY.unitFor("totally_made_up").isEmpty());
    }

    /**
     * Типы обязаны выдаваться свежими узлами: общий экземпляр, попав в дерево дважды, уронил бы
     * {@code MeaningTree.makeIndex} на дубликате идентификатора.
     */
    @Test
    void everyRequestForATypeYieldsAFreshNode() {
        LibraryFunction strlen = LIBRARY.function("strlen").orElseThrow();

        Type first = strlen.resolveParameterType(0);
        Type second = strlen.resolveParameterType(0);

        assertNotSame(first, second);
        assertFalse(first.getId() == second.getId());
        assertEquals(first, second, "разные узлы, но один и тот же тип по значению");
    }

    @Test
    void languageWithoutADescribedLibraryAnswersEmpty() {
        assertTrue(StandardLibrary.unknown().function("strcpy").isEmpty());
        assertTrue(StandardLibrary.unknown().symbol("strcpy").isEmpty());
        assertTrue(StandardLibrary.unknown().namesOf("cstring").isEmpty());
        assertTrue(StandardLibrary.unknown().completeUnits().isEmpty());
    }
}
