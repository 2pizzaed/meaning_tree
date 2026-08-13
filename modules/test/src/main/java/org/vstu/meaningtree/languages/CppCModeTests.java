package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
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
    void usesCMainForSyntheticEntryPointAndPreservesExplicitArguments() {
        String synthetic = translate("int helper(int value) { return value; }", C_MODE);
        assertTrue(synthetic.contains("int main(void)"));

        String explicit = translate("int main(int argc, char *argv[]) { return argc; }", C_MODE);
        assertTrue(explicit.contains("int main(int argc, char * argv[])"));
    }

    @Test
    void leavesReferencesUnchangedForNow() {
        String generated = translate("int read(int &value) { return value; }", C_MODE);
        assertTrue(generated.contains("int & value"));
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

    private static String translate(String source, Map<String, Object> config) {
        CppTranslator translator = new CppTranslator(config);
        return translator.getCode(translator.getMeaningTree(source));
    }
}
