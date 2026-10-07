package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Флаг {@code skipUnknownPreprocDirectives}: директивы препроцессора без разбора пропускаются,
 * от условной остаётся код первой ветки. {@code #include} флаг не затрагивает.
 */
class CppPreprocDirectivesTests {
    private static final Map<String, Object> SKIP = Map.of(
            "translationUnitMode", "full",
            "skipUnknownPreprocDirectives", true,
            "skipErrors", false
    );

    private static final Map<String, Object> STRICT = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    /** По умолчанию — прежнее поведение: разбор останавливается на первой же директиве. */
    @Test
    void rejectsUnknownDirectivesByDefault() {
        for (String directive : new String[] {"#define N 10", "#pragma once", "#undef N", "#ifdef N\n#endif"}) {
            assertThrows(UnsupportedParsingException.class,
                    () -> translate(directive + "\nint main() { return 0; }", STRICT), directive);
        }
    }

    @Test
    void dropsDirectivesWithoutBranches() {
        String generated = translate("""
                #define N 10
                #define SQ(x) ((x) * (x))
                #pragma once
                #undef N
                int main() {
                    #pragma unroll
                    int x = 1;
                    #error never
                    return 0;
                }
                """, SKIP);

        assertFalse(generated.contains("#"), generated);
        assertTrue(generated.contains("int x = 1;"), generated);
        assertTrue(generated.contains("return 0;"), generated);
    }

    /** {@code #include} по-прежнему разбирается — в том числе внутри раскрытой ветки. */
    @Test
    void keepsIncludes() {
        String generated = translate("""
                #ifndef MAIN_H
                #define MAIN_H
                #include <iostream>
                int main() {
                    std::cout << 1;
                    return 0;
                }
                #endif
                """, SKIP);

        assertTrue(generated.contains("#include <iostream>"), generated);
        assertTrue(generated.contains("std::cout << 1;"), generated);
        assertFalse(generated.contains("MAIN_H"), generated);
    }

    @Test
    void keepsFirstBranchOfConditionals() {
        String generated = translate("""
                int main() {
                    #if defined(A)
                    int x = 1;
                    #elif B
                    int x = 2;
                    #else
                    int x = 3;
                    #endif
                    #ifdef C
                    #ifndef D
                    int y = 4;
                    #endif
                    #else
                    int y = 5;
                    #endif
                    return x + y;
                }
                """, SKIP);

        assertTrue(generated.contains("int x = 1;"), generated);
        assertTrue(generated.contains("int y = 4;"), generated);
        assertFalse(generated.contains("int x = 2;"), generated);
        assertFalse(generated.contains("int x = 3;"), generated);
        assertFalse(generated.contains("int y = 5;"), generated);
    }

    @Test
    void expandsDirectivesInClassAndEnumBodies() {
        String generated = translate("""
                struct Point {
                #ifdef THREE_D
                    int z;
                #endif
                    int x;
                };
                enum Color { RED,
                #if EXTRA
                    GREEN,
                #else
                    BLUE,
                #endif
                };
                int main() {
                    return 0;
                }
                """, SKIP);

        assertTrue(generated.contains("int z;"), generated);
        assertTrue(generated.contains("int x;"), generated);
        assertTrue(generated.contains("GREEN"), generated);
        assertFalse(generated.contains("BLUE"), generated);
    }

    @Test
    void expandsDirectivesInSwitch() {
        String generated = translate("""
                int main() {
                    int x = 1;
                    switch (x) {
                    #ifdef ONE
                        case 1:
                            x = 10;
                            break;
                    #endif
                        case 2:
                    #pragma note
                            x = 20;
                            break;
                        default:
                    #if A
                            // default value
                            x = 30;
                    #endif
                            break;
                    }
                    return x;
                }
                """, SKIP);

        assertTrue(generated.contains("case 1:"), generated);
        assertTrue(generated.contains("x = 10;"), generated);
        assertTrue(generated.contains("x = 20;"), generated);
        assertTrue(generated.contains("x = 30;"), generated);
        assertFalse(generated.contains("#"), generated);
        // Директива в ветке обрывает case_statement в дереве tree-sitter; операторы и
        // комментарии после неё всё равно остаются в своей ветке
        List<String> lines = generated.lines().map(String::strip).toList();
        assertEquals(lines.indexOf("x = 30;") - 1, lines.indexOf("// default value"), generated);
    }

    /**
     * Комментарий из раскрытой ветки переносится, как если бы директивы не было. Комментарий
     * отброшенной ветки или строки самой директивы — нет.
     */
    @Test
    void keepsCommentsOfTheExpandedBranchOnly() {
        String generated = translate("""
                int main() {
                    #if A
                    // kept
                    int x = 1 /* inline */ + 2;
                    #else
                    // dropped
                    int x = 3 /* gone */;
                    #endif
                    #define N 1 // directive
                    return x;
                }
                """, SKIP);

        assertTrue(generated.contains("kept"), generated);
        assertTrue(generated.contains("inline"), generated);
        assertFalse(generated.contains("dropped"), generated);
        assertFalse(generated.contains("gone"), generated);
        assertFalse(generated.contains("directive"), generated);
    }

    @Test
    void appliesToCMode() {
        Map<String, Object> config = Map.of(
                "translationUnitMode", "full",
                "preferC", true,
                "skipUnknownPreprocDirectives", true,
                "skipErrors", false
        );
        String generated = translate("""
                #include <stdio.h>
                #define N 3
                int main() {
                #ifdef DEBUG
                    printf("%d", 1);
                #endif
                    return 0;
                }
                """, config);

        assertTrue(generated.contains("#include <stdio.h>"), generated);
        assertTrue(generated.contains("printf("), generated);
        assertFalse(generated.contains("DEBUG"), generated);
    }

    private static String translate(String source, Map<String, Object> config) {
        CppTranslator translator = new CppTranslator(config);
        return translator.getCode(translator.getMeaningTree(source));
    }
}
