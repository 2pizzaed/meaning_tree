package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Флаг {@code useDefaultNamespace}: программа печатается под {@code using namespace std;},
 * без префикса {@code std::}. Разбор при этом принимает оба написания независимо от флага.
 */
class CppDefaultNamespaceTests {
    private static final Map<String, Object> DEFAULT_NAMESPACE = Map.of(
            "translationUnitMode", "full",
            "useDefaultNamespace", true,
            "skipErrors", false
    );

    private static final Map<String, Object> QUALIFIED = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    private static final String STD_HEAVY_SOURCE = """
            #include <iostream>
            #include <vector>
            #include <map>
            #include <string>
            int main() {
                std::vector<int> values;
                std::map<int, int> pairs;
                std::string text = "ok";
                std::cout << text << std::endl;
                std::cin >> text;
                return 0;
            }
            """;

    @Test
    void printsUsingDirectiveAndDropsQualification() {
        String generated = translate(STD_HEAVY_SOURCE, DEFAULT_NAMESPACE);

        assertFalse(generated.contains("std::"), generated);
        assertTrue(generated.contains("vector<int> values;"), generated);
        assertTrue(generated.contains("map<int, int> pairs;"), generated);
        assertTrue(generated.contains("string text = \"ok\";"), generated);
        assertTrue(generated.contains("cout << text << endl;"), generated);
        assertTrue(generated.contains("cin >> text;"), generated);
    }

    /** Директива стоит ровно один раз, ниже последнего включения и выше тела программы. */
    @Test
    void placesDirectiveAfterAllIncludes() {
        List<String> lines = translate(STD_HEAVY_SOURCE, DEFAULT_NAMESPACE).lines().toList();

        int directive = lines.indexOf("using namespace std;");
        assertTrue(directive >= 0, lines::toString);
        assertEquals(1, lines.stream().filter(line -> line.equals("using namespace std;")).count(),
                lines::toString);

        int lastInclude = -1;
        int firstBody = -1;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("#include")) {
                lastInclude = i;
            } else if (line.startsWith("int main") && firstBody < 0) {
                firstBody = i;
            }
        }
        assertTrue(lastInclude >= 0 && lastInclude < directive, lines::toString);
        assertTrue(firstBody > directive, lines::toString);
    }

    /** Значение по умолчанию — прежний вывод: полная квалификация и никакой директивы. */
    @Test
    void keepsQualifiedNamesByDefault() {
        String generated = translate(STD_HEAVY_SOURCE, QUALIFIED);

        assertFalse(generated.contains("using namespace std;"), generated);
        assertTrue(generated.contains("std::vector<int> values;"), generated);
        assertTrue(generated.contains("std::cout << text << std::endl;"), generated);
    }

    /** Авторский {@code std::sqrt} печатается голым именем наравне с именами от генератора. */
    @Test
    void dropsQualificationOfIdentifiersFromTheSource() {
        String source = """
                #include <cmath>
                int main() {
                    double x = std::sqrt(2.0);
                    return 0;
                }
                """;
        String generated = translate(source, DEFAULT_NAMESPACE);

        assertTrue(generated.contains("sqrt(2.0)"), generated);
        assertFalse(generated.contains("std::"), generated);
        assertEquals(1, generated.lines().filter(line -> line.contains("#include <cmath>")).count(),
                generated);
    }

    /** Си пространств имён не знает: флаг на C-режим не влияет вовсе. */
    @Test
    void doesNotAffectCMode() {
        Map<String, Object> config = Map.of(
                "translationUnitMode", "full",
                "preferC", true,
                "useDefaultNamespace", true,
                "skipErrors", false
        );
        String generated = translate("""
                int main() {
                    std::string text = "ok";
                    return 0;
                }
                """, config);

        assertFalse(generated.contains("using namespace std;"), generated);
        assertTrue(generated.contains("char * text = \"ok\";"), generated);
    }

    /**
     * В simple-режиме шапки нет, тело — это содержимое main. Директиве там не место, а значит
     * и квалификацию снимать нельзя: получился бы текст, который не собрать.
     */
    @Test
    void leavesHeaderlessModesUntouched() {
        Map<String, Object> simple = Map.of(
                "translationUnitMode", "simple",
                "useDefaultNamespace", true,
                "skipErrors", false
        );
        String simpleOutput = translate("int main() { std::cout << 1 << std::endl; }", simple);
        assertFalse(simpleOutput.contains("using namespace std;"), simpleOutput);
        assertTrue(simpleOutput.contains("std::cout"), simpleOutput);

        Map<String, Object> expression = Map.of(
                "translationUnitMode", "expression",
                "useDefaultNamespace", true,
                "skipErrors", false
        );
        String expressionOutput = translate("std::cout << 1", expression);
        assertFalse(expressionOutput.contains("using namespace std;"), expressionOutput);
        assertTrue(expressionOutput.contains("std::cout"), expressionOutput);
    }

    /** В procedural-режиме шапка есть — значит, есть и директива. */
    @Test
    void printsDirectiveInProceduralMode() {
        Map<String, Object> config = Map.of(
                "translationUnitMode", "procedural",
                "useDefaultNamespace", true,
                "skipErrors", false
        );
        String generated = translate("""
                #include <iostream>
                int main() {
                    std::cout << 1 << std::endl;
                    return 0;
                }
                """, config);

        assertTrue(generated.contains("using namespace std;"), generated);
        assertFalse(generated.contains("std::"), generated);
    }

    /** Разбор голых имён под директивой — независимо от того, как программу собираются печатать. */
    @Test
    void parsesUnqualifiedStdNamesUnderTheDirective() {
        String source = """
                #include <iostream>
                #include <vector>
                #include <map>
                using namespace std;
                int main() {
                    vector<int> values;
                    map<int, int> pairs;
                    string text = "ok";
                    cout << text << endl;
                    return 0;
                }
                """;

        assertFalse(translate(source, DEFAULT_NAMESPACE).contains("std::"),
                translate(source, DEFAULT_NAMESPACE));

        String qualified = translate(source, QUALIFIED);
        assertFalse(qualified.contains("using namespace std;"), qualified);
        assertTrue(qualified.contains("std::vector<int> values;"), qualified);
        assertTrue(qualified.contains("std::map<int, int> pairs;"), qualified);
        assertTrue(qualified.contains("std::cout << text << std::endl;"), qualified);
    }

    /** Директива внутри функции значит то же самое и до дерева тоже не доходит. */
    @Test
    void acceptsTheDirectiveInsideAFunction() {
        String generated = translate("""
                #include <vector>
                int main() {
                    using namespace std;
                    vector<int> values;
                    return 0;
                }
                """, QUALIFIED);

        assertFalse(generated.contains("using namespace"), generated);
        assertTrue(generated.contains("std::vector<int> values;"), generated);
    }

    /**
     * Без директивы {@code vector} — пользовательский шаблон, случайно названный так же:
     * подменять его std-коллекцией нельзя.
     */
    @Test
    void doesNotTreatUnqualifiedTemplatesAsStdWithoutTheDirective() {
        String generated = translate("""
                int main() {
                    vector<int> values;
                    return 0;
                }
                """, QUALIFIED);

        assertTrue(generated.contains("vector<int> values;"), generated);
        assertFalse(generated.contains("std::vector"), generated);
        // Тип не признан коллекцией, поэтому и заголовок под него не дописан
        assertFalse(generated.contains("#include <vector>"), generated);
    }

    /** Поддерживается явно только std; остальные using-объявления разбор останавливают. */
    @Test
    void rejectsOtherUsingDeclarations() {
        for (String directive : new String[] {"using namespace foo;", "using std::vector;"}) {
            var error = assertThrows(UnsupportedParsingException.class,
                    () -> translate(directive + "\nint main() { return 0; }", QUALIFIED),
                    directive);
            assertTrue(error.getMessage().contains("using namespace std"), error.getMessage());
        }
    }

    private static String translate(String source, Map<String, Object> config) {
        CppTranslator translator = new CppTranslator(config);
        return translator.getCode(translator.getMeaningTree(source));
    }
}
