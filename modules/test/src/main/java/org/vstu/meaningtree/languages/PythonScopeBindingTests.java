package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedConversionException;
import org.vstu.meaningtree.nodes.statements.ScopeDeclarationStatement;
import org.vstu.meaningtree.utils.analysis.ScopeTableBuilder;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Разбор и вывод {@code global} / {@code nonlocal}, а также поведение языков, у которых
 * записать эти объявления нечем.
 * <p>
 * Конверсионные случаи с ожиданиями для Java и C++ лежат в группе {@code ScopeBinding}
 * файла {@code common.test}; здесь — то, чего {@code .test} выразить не может: round trip
 * внутри одного языка, отказ вывода и восстановление привязок отдельным проходом по дереву.
 */
class PythonScopeBindingTests {
    private static final Map<String, Object> SIMPLE = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    @Test
    void nonlocalSurvivesPythonRoundTrip() {
        String source = """
                def outer():
                    total = 0
                    def inner():
                        nonlocal total
                        total = total + 1
                    inner()
                    return total
                """;

        String generated = translateToPython(source);

        assertTrue(generated.contains("nonlocal total"), generated);
        // Присваивание привязанному имени не объявляет локальную: аннотации типа быть не должно
        assertTrue(generated.contains("total = total + 1"), generated);
        assertFalse(generated.contains("total: int = total + 1"), generated);
    }

    @Test
    void globalSurvivesPythonRoundTripAndKeepsAssignmentPlain() {
        String source = """
                counter = 0
                def bump():
                    global counter
                    counter = counter + 1
                """;

        String generated = translateToPython(source);

        assertTrue(generated.contains("global counter"), generated);
        assertTrue(generated.contains("counter = counter + 1"), generated);
    }

    /**
     * Присваивание без объявления вводит локальное имя и одноимённое внешнее не трогает —
     * ради этого правило связывания для Python и перевёрнуто.
     */
    @Test
    void assignmentWithoutDeclarationShadowsOuterName() {
        String generated = translateToPython("""
                x = 1
                def f():
                    x = 2
                    return x
                """);

        assertTrue(generated.contains("x: int = 2"), generated);
    }

    @Test
    void nonlocalIsRejectedByLanguagesWithoutIt() {
        MeaningTree tree = pythonTree("""
                def outer():
                    total = 0
                    def inner():
                        nonlocal total
                        total = total + 1
                """);

        for (LanguageTranslator target : java.util.List.of(new JavaTranslator(SIMPLE), new CppTranslator(SIMPLE))) {
            UnsupportedConversionException error = assertThrows(
                    UnsupportedConversionException.class,
                    () -> target.getCode(tree),
                    target.getLanguageName());
            assertTrue(error.getMessage().contains("nonlocal"), error.getMessage());
        }
    }

    /** В C-семействе присваивание внешнему имени и так уходит наружу — {@code global} снимается. */
    @Test
    void globalIsDroppedByLanguagesWithExplicitDeclarations() {
        MeaningTree tree = pythonTree("""
                counter = 0
                def bump():
                    global counter
                    counter = counter + 1
                """);

        String java = new JavaTranslator(SIMPLE).getCode(tree);
        assertFalse(java.contains("global"), java);
        assertTrue(java.contains("counter = counter + 1;"), java);

        String cpp = new CppTranslator(SIMPLE).getCode(tree);
        assertFalse(cpp.contains("global"), cpp);
        assertTrue(cpp.contains("counter = counter + 1;"), cpp);
    }

    /**
     * Проход по готовому дереву обязан восстановить те же привязки, что собрал разбор: иначе
     * таблица, построенная из десериализованного дерева, описала бы другую программу.
     */
    @Test
    void scopeTableBuilderRestoresBindingsFromTree() {
        PythonTranslator translator = new PythonTranslator(SIMPLE);
        MeaningTree tree = translator.getMeaningTree("""
                counter = 0
                def bump():
                    global counter
                    counter = counter + 1
                """);

        ScopeTable rebuilt = ScopeTableBuilder.build(
                tree, translator.getScopePolicy(), translator.getAssignmentBinding());

        long boundScopes = rebuilt.allScopes().stream()
                .filter(scope -> !scope.allRebinds().isEmpty())
                .count();
        assertEquals(1, boundScopes, "exactly the body of bump() rebinds a name");

        assertEquals(1, tree.iterate().stream()
                .filter(info -> info.node() instanceof ScopeDeclarationStatement)
                .count());
    }

    private static String translateToPython(String source) {
        PythonTranslator translator = new PythonTranslator(SIMPLE);
        return translator.getCode(translator.getMeaningTree(source));
    }

    private static MeaningTree pythonTree(String source) {
        return new PythonTranslator(SIMPLE).getMeaningTree(source);
    }
}
