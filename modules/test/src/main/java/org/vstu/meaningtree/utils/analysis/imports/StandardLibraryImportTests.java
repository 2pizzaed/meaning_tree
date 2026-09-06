package org.vstu.meaningtree.utils.analysis.imports;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.analysis.library.StandardLibrary;
import org.vstu.meaningtree.nodes.types.containers.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты подключения стандартной библиотеки при генерации кода.
 * <p>
 * Заголовки нужны только C++: Java печатает коллекции полными именами
 * ({@code java.util.ArrayList<...>}), а в Python они встроенные — там подключать нечего, и
 * проверяется ровно обратное: что импорт не появился.
 */
class StandardLibraryImportTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "procedural",
            "skipErrors", false
    );

    @Test
    void cppGetsHeaderForEachCollectionItActuallyPrints() {
        assertCppIncludes(new PythonTranslator(CONFIG), "a: list[int] = [1, 2, 3]", "#include <vector>", "std::vector<");
        assertCppIncludes(new PythonTranslator(CONFIG), "a = {1, 2, 3}", "#include <set>", "std::set<");
        assertCppIncludes(new PythonTranslator(CONFIG), "a: dict[str, int] = {}", "#include <map>", "std::map<");
        assertCppIncludes(new JavaTranslator(CONFIG), "java.util.ArrayList<Integer> a;", "#include <vector>", "std::vector<");
        assertCppIncludes(new JavaTranslator(CONFIG), "java.util.HashSet<Integer> a;", "#include <set>", "std::set<");
    }

    @Test
    void cppUnorderedDictionaryGetsItsOwnHeader() {
        // java.util.HashMap неупорядочен, java.util.TreeMap упорядочен: заголовок обязан
        // следовать за тем типом, который вьюер напечатал, а не за «словарём вообще»
        String unordered = translate(new JavaTranslator(CONFIG), new CppTranslator(CONFIG),
                "java.util.HashMap<String, Integer> a;");
        assertTrue(unordered.contains("std::unordered_map<"), unordered);
        assertTrue(unordered.contains("#include <unordered_map>"), unordered);

        String ordered = translate(new JavaTranslator(CONFIG), new CppTranslator(CONFIG),
                "java.util.TreeMap<String, Integer> a;");
        assertTrue(ordered.contains("std::map<"), ordered);
        assertTrue(ordered.contains("#include <map>"), ordered);
        assertFalse(ordered.contains("unordered_map"), ordered);
    }

    @Test
    void cppGetsAlgorithmAndCstdlibHeadersForFreeFunctions() {
        String withMin = translate(new PythonTranslator(CONFIG), new CppTranslator(CONFIG), "a = min(x, y)");
        assertTrue(withMin.contains("#include <algorithm>"), withMin);

        String withAbs = translate(new PythonTranslator(CONFIG), new CppTranslator(CONFIG), "a = abs(x)");
        assertTrue(withAbs.contains("#include <cstdlib>"), withAbs);
    }

    @Test
    void registryCoversEveryContainerTypeTheCppViewerCanPrint() {
        // std::array вьюер печатает только для неизменяемого списка, а до него не доходит ни
        // один существующий разбор — поэтому таблица проверяется напрямую
        assertEquals("array", header(new UnmodifiableListType(new IntType(32))));
        assertEquals("array", header(new ArrayType(new IntType(32), 1)));
        assertEquals("vector", header(new ListType(new IntType(32))));
        assertEquals("set", header(new SetType(new IntType(32))));
        assertEquals("map", header(new OrderedDictionaryType(new IntType(32), new IntType(32))));
        assertEquals("unordered_map", header(new UnorderedDictionaryType(new IntType(32), new IntType(32))));
        assertTrue(CppLibraryImportRegistry.headerForType(new IntType(32)).isEmpty());
    }

    private String header(org.vstu.meaningtree.nodes.Type type) {
        return CppLibraryImportRegistry.headerForType(type).orElseThrow();
    }

    @Test
    void cppGetsMathHeaderOnlyWhenTheFunctionIsUsed() {
        String withPow = translate(new PythonTranslator(CONFIG), new CppTranslator(CONFIG), "a: int = x ** y");
        assertTrue(withPow.contains("#include <cmath>"), withPow);

        String withoutPow = translate(new PythonTranslator(CONFIG), new CppTranslator(CONFIG), "a: int = x + y");
        assertFalse(withoutPow.contains("#include"), withoutPow);
    }

    @Test
    void cppDoesNotRepeatHeaderAlreadyPresentInProgram() {
        String code = translate(new CppTranslator(fullUnit()), new CppTranslator(fullUnit()),
                "#include <cmath>\nint main() { double a = pow(2, 3); return 0; }");
        assertEquals(1, countOccurrences(code, "#include <cmath>"), code);
    }

    /**
     * Неиспользованный заголовок исчезает без отказа: убирать импорт, на который в выводе нет
     * ни одной ссылки, безопасно — переводить там нечего.
     */
    @Test
    void cppSystemHeaderDisappearsWhenTranslatedToJavaAndPython() {
        // <vector> в Java и Python не переводится ни во что: коллекции там встроены или
        // печатаются полным именем, и import vector был бы мусором, а не переводом
        String java = translate(new CppTranslator(fullUnit()), new JavaTranslator(fullUnit()),
                "#include <vector>\nint main() { return 0; }");
        assertFalse(java.contains("vector"), java);

        String python = translate(new CppTranslator(fullUnit()), new PythonTranslator(fullUnit()),
                "#include <vector>\nint main() { return 0; }");
        assertFalse(python.contains("vector"), python);
    }

    /**
     * Имя заголовка — имя файла, а не сущности, и совпадение этого слова с чем-нибудь в выводе
     * ссылкой на подключение не является: {@code <map>} ничего не говорит о переменной
     * {@code map}, как {@code <format>} — о встроенном {@code String.format}. Раньше такое
     * совпадение читалось как живая ссылка, и перевод отказывал вместо того, чтобы выбросить
     * подключение, которого в целевом языке не бывает вовсе.
     */
    @Test
    void cppSystemHeaderDoesNotBlockOnANameThatOnlyLooksLikeIt() {
        String java = translate(new CppTranslator(fullUnit()), new JavaTranslator(fullUnit()),
                "#include <map>\nint main() { int map = 1; return map; }");
        assertTrue(java.contains("int map = 1;"), java);
        assertFalse(java.contains("import "), java);
    }

    /**
     * Импорт без соответствия в целевом языке можно убрать только тогда, когда в выводе не
     * осталось ссылок на него. Здесь ссылка остаётся: {@code random.random()} в Java ни к чему
     * не относится, и программа, из которой просто выкинули строку импорта, выглядит целой, но
     * не работает.
     */
    @Test
    void refusesToDropAnImportThatGeneratedCodeStillUses() {
        UnsupportedViewingException error = assertThrows(UnsupportedViewingException.class,
                () -> translate(new PythonTranslator(CONFIG), new JavaTranslator(CONFIG),
                        "import random\nx = random.random()\n"));

        assertTrue(error.getMessage().contains("random"), error.getMessage());
        assertTrue(error.getMessage().contains("silentlySkipUnknownImports"), error.getMessage());
    }

    /** Отказ снимается явным включением флага — и только им. */
    @Test
    void silentSkipFlagRestoresDropping() {
        Map<String, Object> silent = new java.util.HashMap<>(CONFIG);
        silent.put("silentlySkipUnknownImports", true);

        String java = translate(new PythonTranslator(CONFIG), new JavaTranslator(silent),
                "import random\nx = random.random()\n");

        assertFalse(java.contains("import random"), java);
    }

    /**
     * Си-заголовок исходной программы, вытесненный C++-заголовком: {@code char *} напечатан
     * {@code std::string} из {@code <string>}, и из {@code <string.h>} в выводе не осталось
     * ничего. Раньше в шапку попадали оба — вытесняющий заголовок откладывался по ходу
     * отрисовки, а вытесненный так и стоял в программе.
     */
    @Test
    void cppDropsCHeaderSupersededByTheCppOne() {
        String code = translate(new CppTranslator(fullUnit()), new CppTranslator(fullUnit()),
                "#include <string.h>\nint main() { std::string s = \"a\"; return 0; }");
        assertTrue(code.contains("#include <string>"), code);
        assertFalse(code.contains("string.h"), code);
    }

    /** То же для C++-написания того же файла: {@code <cstring>} — это {@code <string.h>}. */
    @Test
    void cppDropsSupersededHeaderInItsCppSpellingToo() {
        String code = translate(new CppTranslator(fullUnit()), new CppTranslator(fullUnit()),
                "#include <cstring>\nint main() { std::string s = \"a\"; return 0; }");
        assertTrue(code.contains("#include <string>"), code);
        assertFalse(code.contains("cstring"), code);
    }

    /**
     * Вытесняющий заголовок сам по себе выбросить вытесненный не разрешает: один файл даёт
     * много средств, и пока в выводе осталось хоть одно имя из него, подключение нужно.
     */
    @Test
    void cppKeepsSupersededHeaderWhileTheOutputStillUsesIt() {
        String code = translate(new CppTranslator(fullUnit()), new CppTranslator(fullUnit()),
                "#include <string.h>\nint main() { std::string s = \"a\"; return (int) strlen(\"b\"); }");
        assertTrue(code.contains("#include <string>"), code);
        assertTrue(code.contains("#include <string.h>"), code);
    }

    /** Без вытесняющего заголовка неиспользованный Си-заголовок остаётся: выбрасывать его не за что. */
    @Test
    void cppKeepsUnusedCHeaderWithoutASupersedingOne() {
        String code = translate(new CppTranslator(fullUnit()), new CppTranslator(fullUnit()),
                "#include <string.h>\nint main() { return 0; }");
        assertTrue(code.contains("#include <string.h>"), code);
    }

    /**
     * Вытеснение решается по именам вытесненного заголовка, поэтому у каждого объявленного
     * отношения состав обязан быть описан полностью — иначе правило либо не сработает ни разу,
     * либо выбросит подключение при живом обращении.
     */
    @Test
    void everyDeclaredSupersessionKnowsTheNamesOfTheSupersededHeader() {
        StandardLibrary library = new CppTranslator(fullUnit()).getLanguageBehavior().standardLibrary();
        for (String superseding : new String[] {"iostream", "string"}) {
            String superseded = CppLibraryImportRegistry.headerSupersededBy(superseding).orElseThrow();
            String unit = CppLibraryImportRegistry.cppSpellingOf(superseded).orElseThrow();
            assertTrue(library.namesOf(unit).isPresent(),
                    () -> "состав " + unit + " не описан полностью");
        }
        assertTrue(CppLibraryImportRegistry.headerSupersededBy("vector").isEmpty());
    }

    /**
     * Имя, объявленное в самой программе, библиотечным не считается: своя {@code abs} — обычная
     * функция автора, и заголовок под неё не подключается. Решение принимается по имени, поэтому
     * без вопроса к таблице областей видимости любое совпадение с библиотечным именем приносило
     * бы в вывод посторонний заголовок.
     */
    @Test
    void cppAsksTheScopeTableBeforeIncludingAHeaderForACall() {
        String own = translate(new CppTranslator(fullUnit()), new CppTranslator(fullUnit()),
                "int abs(int x) { return x; }\nint main() { return abs(-1); }");
        assertFalse(own.contains("#include"), own);

        String library = translate(new CppTranslator(fullUnit()), new CppTranslator(fullUnit()),
                "int main() { return abs(-1); }");
        assertTrue(library.contains("#include <cstdlib>"), library);
    }

    @Test
    void javaAndPythonCollectionsNeedNoImport() {
        String java = translate(new PythonTranslator(CONFIG), new JavaTranslator(CONFIG), "a: list[int] = [1, 2, 3]");
        assertFalse(java.contains("import "), java);

        String python = translate(new JavaTranslator(CONFIG), new PythonTranslator(CONFIG),
                "java.util.ArrayList<Integer> a;");
        assertFalse(python.contains("import "), python);
    }

    @Test
    void javaNamesLibraryClassesQualifiedByDefault() {
        String code = translate(new PythonTranslator(CONFIG), new JavaTranslator(CONFIG), "a: list[int] = [1, 2, 3]");
        assertTrue(code.contains("java.util.ArrayList<"), code);
        assertFalse(code.contains("import "), code);
    }

    @Test
    void javaSwitchesToShortNamesWithImportsWhenQualifiedReferencesAreOff() {
        // Простое имя без импорта не компилируется, поэтому проверяются обе половины решения
        String code = translate(new PythonTranslator(CONFIG), new JavaTranslator(shortNames()),
                "a: list[int] = [1, 2, 3]");
        assertTrue(code.contains("import java.util.ArrayList;"), code);
        assertTrue(code.contains("import java.util.List;"), code);
        assertTrue(code.contains("ArrayList<Integer> a"), code);
        assertFalse(code.contains("java.util.ArrayList<"), code);
    }

    @Test
    void javaImportHeaderStaysAboveTheClassInWholeProgram() {
        String code = translate(new PythonTranslator(fullUnit()), new JavaTranslator(fullUnitShortNames()),
                "a: list[int] = [1, 2, 3]");
        assertTrue(code.indexOf("import java.util.ArrayList;") < code.indexOf("class Main"), code);
    }

    @Test
    void javaEmitsNoImportForAConstructThatDoesNotUseTheLibrary() {
        String code = translate(new PythonTranslator(CONFIG), new JavaTranslator(shortNames()), "a: int = 1 + 2");
        assertFalse(code.contains("import "), code);
    }

    private Map<String, Object> shortNames() {
        return Map.of("translationUnitMode", "procedural", "skipErrors", false,
                "preferQualifiedReferences", false);
    }

    private Map<String, Object> fullUnitShortNames() {
        return Map.of("translationUnitMode", "full", "skipErrors", false,
                "preferQualifiedReferences", false);
    }

    private void assertCppIncludes(LanguageTranslator source, String sourceCode,
                                   String expectedInclude, String expectedType) {
        String code = translate(source, new CppTranslator(CONFIG), sourceCode);
        assertTrue(code.contains(expectedType), code);
        assertTrue(code.contains(expectedInclude), code);
    }

    private Map<String, Object> fullUnit() {
        return Map.of("translationUnitMode", "full", "skipErrors", false);
    }

    private String translate(LanguageTranslator from, LanguageTranslator to, String code) {
        MeaningTree tree = from.getMeaningTree(code);
        return to.getCode(tree);
    }

    private int countOccurrences(String text, String fragment) {
        int count = 0;
        int index = text.indexOf(fragment);
        while (index >= 0) {
            count++;
            index = text.indexOf(fragment, index + fragment.length());
        }
        return count;
    }
}
