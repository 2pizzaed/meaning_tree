package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;
import org.vstu.meaningtree.nodes.types.containers.ArrayType;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Эвристика распознавания Си-строк: проверяется сам вывод типа, а не только круговой перевод.
 * Круг остаётся зелёным и тогда, когда эвристика не сработала вовсе, поэтому проверять надо
 * узлы дерева.
 */
class CppCStringInferenceTests {
    private static final Map<String, Object> FULL = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    @Test
    void pointerInitializedByStringLiteralBecomesString() {
        MeaningTree tree = parse("""
                int main() {
                    char *s = "abc";
                    return 0;
                }
                """);

        StringType inferred = stringTypeOf(tree, "s");
        assertTrue(inferred.isCStyleString(), "the string keeps its C spelling");
        assertFalse(inferred.hasMaxLength(), "a pointer carries no buffer capacity");
        assertFalse(inferred.isImmutable(), "C strings are mutable");
        assertEquals(8, inferred.getCharSize());
    }

    @Test
    void constCharPointerKeepsConstness() {
        MeaningTree tree = parse("""
                int main() {
                    const char *a = "abc";
                    return 0;
                }
                """);

        assertTrue(stringTypeOf(tree, "a").isConst());
    }

    @Test
    void bufferPassedToStrcpyBecomesStringAndKeepsItsCapacity() {
        MeaningTree tree = parse("""
                #include <string.h>
                int main() {
                    char buf[64];
                    strcpy(buf, "abc");
                    return 0;
                }
                """);

        StringType inferred = stringTypeOf(tree, "buf");
        assertTrue(inferred.hasMaxLength(), "capacity of char buf[64] must survive");
        assertEquals("64", render(inferred.getMaxLength()));
    }

    @Test
    void parameterUsedByStrlenBecomesString() {
        MeaningTree tree = parse("""
                #include <string.h>
                int length(char *p) {
                    return strlen(p);
                }
                """);

        DeclarationArgument parameter = tree.iterate().stream()
                .filter(info -> info.node() instanceof DeclarationArgument)
                .map(info -> (DeclarationArgument) info.node())
                .findFirst()
                .orElseThrow();
        assertInstanceOf(StringType.class, parameter.getType());
    }

    @Test
    void characterArrayLiteralWithTerminatorBecomesStringLiteral() {
        MeaningTree tree = parse("""
                int main() {
                    char t[] = {'h', 'i', '\\0'};
                    return 0;
                }
                """);

        VariableDeclaration declaration = declarationOf(tree, "t").orElseThrow();
        assertInstanceOf(StringType.class, declaration.getType());
        StringLiteral literal = assertInstanceOf(StringLiteral.class,
                declaration.getFirstDeclarator().getRValue());
        assertEquals("hi", literal.getUnescapedValue(), "the terminator belongs to the representation");
    }

    @Test
    void characterArrayLiteralWithoutTerminatorStaysAnArray() {
        MeaningTree tree = parse("""
                int main() {
                    char t[] = {'h', 'i'};
                    return 0;
                }
                """);

        assertInstanceOf(ArrayType.class, declarationOf(tree, "t").orElseThrow().getType());
    }

    @Test
    void pointerWithoutAnyEvidenceStaysAPointer() {
        MeaningTree tree = parse("""
                #include <stdlib.h>
                int main() {
                    char *raw;
                    raw = (char *) malloc(10);
                    return 0;
                }
                """);

        assertInstanceOf(PointerType.class, declarationOf(tree, "raw").orElseThrow().getType());
    }

    /** {@code memcpy} принимает {@code void *} и о строковости не говорит ничего. */
    @Test
    void memoryFunctionsAreNotEvidence() {
        MeaningTree tree = parse("""
                #include <string.h>
                int main() {
                    char buf[64];
                    memcpy(buf, 0, 10);
                    return 0;
                }
                """);

        assertInstanceOf(ArrayType.class, declarationOf(tree, "buf").orElseThrow().getType());
    }

    /** Константность самого указателя в {@link StringType} невыразима, поэтому не трогается. */
    @Test
    void constPointerToCharStaysAPointer() {
        MeaningTree tree = parse("""
                int main() {
                    char * const d = "test";
                    return 0;
                }
                """);

        assertInstanceOf(PointerType.class, declarationOf(tree, "d").orElseThrow().getType());
    }

    @Test
    void stringnessSpreadsAlongAssignments() {
        MeaningTree tree = parse("""
                int main() {
                    char *s = "abc";
                    char *alias = s;
                    return 0;
                }
                """);

        assertInstanceOf(StringType.class, declarationOf(tree, "alias").orElseThrow().getType());
    }

    @Test
    void inferenceIsOffWhenTheFlagIsCleared() {
        CppTranslator translator = new CppTranslator(Map.of(
                "translationUnitMode", "full",
                "skipErrors", false,
                "preferCharArrayAsString", false));
        MeaningTree tree = translator.getMeaningTree("""
                int main() {
                    char *s = "abc";
                    return 0;
                }
                """);

        assertInstanceOf(PointerType.class, declarationOf(tree, "s").orElseThrow().getType());
    }

    @Test
    void inferredStringIsRenderedBackInItsOriginalSpelling() {
        String source = """
                #include <string.h>
                int main() {
                    char *s = "abc";
                    char buf[64];
                    strcpy(buf, s);
                    return 0;
                }
                """;
        CppTranslator translator = new CppTranslator(FULL);
        String generated = translator.getCode(translator.getMeaningTree(source));

        assertTrue(generated.contains("char * s = \"abc\";"), generated);
        assertTrue(generated.contains("char buf[64];"), generated);
        assertFalse(generated.contains("std::string"), generated);
    }

    /**
     * Заголовок под библиотечную функцию теперь известен из того же описания, что и её
     * сигнатура: раньше {@code strcpy} печатался без {@code <cstring>}, потому что заголовки
     * функций лежали в отдельной таблице, куда строковые функции никто не внёс.
     */
    @Test
    void callToLibraryFunctionBringsItsHeader() {
        CppTranslator translator = new CppTranslator(FULL);
        String generated = translator.getCode(translator.getMeaningTree("""
                int main() {
                    char buf[64];
                    strcpy(buf, "abc");
                    return 0;
                }
                """));

        assertTrue(generated.contains("#include <cstring>"), generated);
    }

    /** {@code <string.h>} и {@code <cstring>} — один файл, и в шапке ему место одно. */
    @Test
    void headerAlreadyIncludedUnderItsCSpellingIsNotAddedTwice() {
        CppTranslator translator = new CppTranslator(FULL);
        String generated = translator.getCode(translator.getMeaningTree("""
                #include <string.h>
                int main() {
                    char buf[64];
                    strcpy(buf, "abc");
                    return 0;
                }
                """));

        assertEquals(1, generated.lines().filter(line -> line.startsWith("#include")).count(), generated);
        assertTrue(generated.contains("#include <string.h>"), "написание автора сохраняется: " + generated);
    }

    private static MeaningTree parse(String source) {
        return new CppTranslator(FULL).getMeaningTree(source);
    }

    private static String render(Object node) {
        return new CppTranslator(FULL).getCode(
                new MeaningTree((org.vstu.meaningtree.nodes.Node) node));
    }

    private static StringType stringTypeOf(MeaningTree tree, String name) {
        Type type = declarationOf(tree, name).orElseThrow().getType();
        return assertInstanceOf(StringType.class, type, name + " must be inferred as a string");
    }

    private static Optional<VariableDeclaration> declarationOf(MeaningTree tree, String name) {
        return tree.iterate().stream()
                .filter(info -> info.node() instanceof VariableDeclaration)
                .map(info -> (VariableDeclaration) info.node())
                .filter(declaration -> declaration.getFirstDeclarator() != null
                        && declaration.getFirstDeclarator().getIdentifier().getName().equals(name))
                .findFirst();
    }
}
