package org.vstu.meaningtree.utils.analysis.library;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CppStandardLibraryTests {
    private static final StandardLibrary LIBRARY =
            new CppTranslator(Map.of("skipErrors", false)).getLanguageBehavior().standardLibrary();

    @Test
    void languageBehaviorCarriesTheLibrary() {
        assertTrue(LIBRARY.function("strcpy").isPresent(), "C++ describes its standard library");
    }

    /**
     * Заголовок и сигнатура — одна запись: пока они лежали в двух таблицах с одним ключом, у
     * {@code strcpy} была сигнатура без заголовка, а у {@code pow} — заголовок без сигнатуры.
     */
    @Test
    void oneEntryAnswersBothAboutHeaderAndAboutTypes() {
        LibraryFunction strcpy = LIBRARY.function("strcpy").orElseThrow();
        assertEquals("cstring", strcpy.header());
        assertTrue(strcpy.isParameterOfType(0, StringType.class));
        assertTrue(strcpy.isParameterOfType(1, StringType.class));
        assertTrue(strcpy.returnsType(StringType.class));

        LibraryFunction pow = LIBRARY.function("pow").orElseThrow();
        assertEquals("cmath", pow.header());
        assertTrue(pow.isParameterOfType(0, FloatType.class));
        assertTrue(pow.returnsType(FloatType.class));
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

        assertEquals("algorithm", min.header());
        assertNull(min.resolveReturnType());
        assertNull(min.resolveParameterType(0));
    }

    @Test
    void unknownFunctionIsNotAnError() {
        assertTrue(LIBRARY.function("totally_made_up").isEmpty());
        assertTrue(LIBRARY.headerForFunction("totally_made_up").isEmpty());
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
    }
}
