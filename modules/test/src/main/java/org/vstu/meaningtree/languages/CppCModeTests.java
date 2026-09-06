package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedConversionException;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.utils.SourceMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CppCModeTests {
    private static final Map<String, Object> C_MODE = Map.of(
            "translationUnitMode", "full",
            "preferC", true,
            "skipErrors", false
    );

    @Test
    void rendersCMainStringsArraysAndStructures() {
        String source = """
                struct Point { int x; int y; };
                int main() {
                    std::string text = "ok";
                    int values[3];
                    return 0;
                }
                """;

        String generated = translate(source, C_MODE);

        assertTrue(generated.contains("typedef struct Point"));
        assertTrue(generated.contains("} Point;"));
        assertTrue(generated.contains("int main(void)"));
        assertTrue(generated.contains("char * text = \"ok\";"));
        assertTrue(generated.contains("int values[3];"));
        assertFalse(generated.contains("std::string"));
    }

    @Test
    void dropsCppOnlyIncludesLeftOverFromTheSource() {
        // #include <string> и <vector> из исходника не собрались бы в чистом Си, а сама
        // std::string уже переписана в char * — заголовок ей больше не нужен
        String source = """
                #include <string>
                #include <vector>
                int main() {
                    std::string text = "ok";
                    return 0;
                }
                """;

        String generated = translate(source, C_MODE);

        assertFalse(generated.contains("#include <string>"), generated);
        assertFalse(generated.contains("#include <vector>"), generated);
        assertTrue(generated.contains("char * text = \"ok\";"), generated);
    }

    @Test
    void usesCMainForSyntheticEntryPointAndPreservesExplicitArguments() {
        String synthetic = translate("int helper(int value) { return value; }", C_MODE);
        assertTrue(synthetic.contains("int main(void)"));

        String explicit = translate("int main(int argc, char *argv[]) { return argc; }", C_MODE);
        assertTrue(explicit.contains("int main(int argc, char * argv[])"));
    }

    @Test
    void rejectsReferencesUnlessTheyAreRepresentedAsPointers() {
        assertThrows(UnsupportedConversionException.class,
                () -> translate("int read(int &value) { return value; }", C_MODE));
    }

    @Test
    void rejectsCollectionsAndObjectOrientedStructures() {
        assertThrows(UnsupportedViewingException.class,
                () -> translate("int main() { std::vector<int> xs; }", C_MODE));
        assertThrows(UnsupportedViewingException.class,
                () -> translate("struct Box { int get() { return 1; } };", C_MODE));
    }

    @Test
    void recognizesAndRendersMallocCallocAndFree() {
        CppTranslator translator = new CppTranslator(C_MODE);
        MeaningTree tree = translator.getMeaningTree("""
                int main() {
                    int *a = malloc(sizeof(int) * 3);
                    int *b = calloc(4, sizeof(int));
                    free(a);
                    return 0;
                }
                """);
        ProgramEntryPoint root = assertInstanceOf(ProgramEntryPoint.class, tree.getRootNode());
        var body = ((org.vstu.meaningtree.nodes.definitions.FunctionDefinition) root.getEntryPoint()).getBody();
        VariableDeclaration mallocDeclaration = assertInstanceOf(VariableDeclaration.class, body.getNodes()[0]);
        VariableDeclaration callocDeclaration = assertInstanceOf(VariableDeclaration.class, body.getNodes()[1]);
        assertInstanceOf(MemoryAllocationCall.class, mallocDeclaration.getFirstDeclarator().getRValue());
        assertInstanceOf(MemoryAllocationCall.class, callocDeclaration.getFirstDeclarator().getRValue());
        ExpressionStatement freeStatement = assertInstanceOf(ExpressionStatement.class, body.getNodes()[2]);
        assertInstanceOf(MemoryFreeCall.class, freeStatement.getExpression());

        String generated = translator.getCode(tree);
        assertTrue(generated.contains("malloc(sizeof(int) * 3)"));
        assertTrue(generated.contains("calloc(4, sizeof(int))"));
        assertTrue(generated.contains("free(a)"));
    }

    @Test
    void lowersRepresentableNewAndDeleteButRejectsConstruction() {
        String generated = translate("""
                int main() {
                    int *values = new int[3];
                    delete[] values;
                    return 0;
                }
                """, C_MODE);
        assertTrue(generated.contains("malloc(sizeof(int) * 3)"));
        assertTrue(generated.contains("free(values);"));
        assertTrue(generated.startsWith("#include <stdlib.h>"));
        assertFalse(generated.contains("new int"));
        assertFalse(generated.contains("delete[]"));

        assertThrows(UnsupportedViewingException.class,
                () -> translate("struct Box { int value; }; int main() { Box *b = new Box(1); }", C_MODE));
    }

    @Test
    void cppModeRemainsUnchangedAndRenderingDoesNotMutateTree() {
        Map<String, Object> cppMode = Map.of("translationUnitMode", "full", "skipErrors", false);
        assertTrue(translate("int main() { std::string s = \"x\"; }", cppMode).contains("std::string"));

        CppTranslator translator = new CppTranslator(C_MODE);
        MeaningTree tree = translator.getMeaningTree("int main() { int *p = new int[2]; delete[] p; }");
        MeaningTree snapshot = tree.clone();
        String first = translator.getCode(tree);
        String second = translator.getCode(tree);
        assertEquals(first, second);
        assertEquals(snapshot, tree);

        SourceMap firstMap = new SourceMapGenerator(translator).process(tree);
        SourceMap secondMap = new SourceMapGenerator(translator).process(tree);
        assertEquals(first, firstMap.code());
        assertEquals(firstMap.code(), secondMap.code());
        assertEquals(firstMap.bytePositions(), secondMap.bytePositions());
    }

    @Test
    void doesNotDuplicateStdlibIncludeAlreadyPresentInSource() {
        String generated = translate("""
                #include <stdlib.h>
                int main() {
                    int *a = malloc(sizeof(int) * 3);
                    free(a);
                    return 0;
                }
                """, C_MODE);
        long includeCount = generated.lines()
                .filter(line -> line.trim().equals("#include <stdlib.h>"))
                .count();
        assertEquals(1, includeCount);
    }

    @Test
    void cModeTakesPrecedenceOverHeapArrayPreference() {
        Map<String, Object> config = Map.of(
                "translationUnitMode", "full",
                "preferC", true,
                "preferHeapAlloc", true,
                "skipErrors", false
        );
        String generated = translate("int main() { int values[2]; }", config);
        assertTrue(generated.contains("int values[2];"));
        assertFalse(generated.contains("auto*"));
    }

    /**
     * Допустимость системного заголовка в C-режиме проверяется по списку Си-заголовков, а не по
     * перечню запрещённых C++-заголовков. Прежний чёрный список содержал только те заголовки,
     * которые реестр умеет выдавать сам, поэтому в Си-вывод проходили {@code <iostream>},
     * {@code <memory>}, {@code <sstream>}, {@code <thread>} — файл с ними не собирается.
     */
    @Test
    void dropsCppOnlyHeadersTheRegistryNeverEmits() {
        for (String header : new String[] {"iostream", "memory", "sstream", "thread", "optional"}) {
            String generated = translate(
                    "#include <%s>\nint main() { return 0; }".formatted(header), C_MODE);
            assertFalse(generated.contains(header), () -> header + " остался в C-выводе: " + generated);
        }
    }

    /** Настоящий Си-заголовок остаётся: выбрасывать его не за что. */
    @Test
    void keepsCStandardHeaders() {
        String generated = translate("#include <stdio.h>\nint main() { return 0; }", C_MODE);
        assertTrue(generated.contains("#include <stdio.h>"), generated);
    }

    /**
     * {@code <cstdio>} — это C++-написание {@code <stdio.h>}: тот же заголовок, недоступный под
     * этим именем в Си. Он переписывается, а не выбрасывается — содержимое коду нужно.
     */
    @Test
    void rewritesCppSpellingsOfCHeaders() {
        assertTrue(translate("#include <cstdio>\nint main() { return 0; }", C_MODE)
                .contains("#include <stdio.h>"));
        assertTrue(translate("#include <cmath>\nint main() { return 0; }", C_MODE)
                .contains("#include <math.h>"));
    }

    /**
     * Если в исходнике уже стоит {@code <cstdlib>}, а {@code malloc}/{@code free} требуют
     * {@code stdlib.h}, дедупликация не должна путать написание и печатать заголовок дважды.
     */
    @Test
    void doesNotDuplicateHeaderWhenSourceUsesCppSpellingAlready() {
        String source = """
                #include <cstdlib>
                int main() {
                    int *p = (int*)malloc(sizeof(int));
                    free(p);
                    return 0;
                }
                """;
        String generated = translate(source, C_MODE);
        assertEquals(1, generated.lines().filter(line -> line.contains("#include <stdlib.h>")).count(), generated);
    }

    private static String translate(String source, Map<String, Object> config) {
        CppTranslator translator = new CppTranslator(config);
        return translator.getCode(translator.getMeaningTree(source));
    }
}
