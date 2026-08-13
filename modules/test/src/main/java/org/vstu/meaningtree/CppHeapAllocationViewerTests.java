package org.vstu.meaningtree;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CppHeapAllocationViewerTests {
    private static final Map<String, Object> DEFAULT_CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );
    private static final Map<String, Object> HEAP_CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false,
            "preferHeapAlloc", true
    );

    @Test
    void preferHeapAllocIsDisabledByDefault() {
        MeaningTree tree = new JavaTranslator(DEFAULT_CONFIG).getMeaningTree("""
                int[] values = new int[size];
                int[] seeded = new int[] {1, 2};
                """);

        String code = new CppTranslator(DEFAULT_CONFIG).getCode(tree);

        assertTrue(code.contains("int values[size];"), code);
        assertTrue(code.contains("int seeded[] = {1, 2};"), code);
    }

    @Test
    void preferHeapAllocUsesHeapForObjectAndArrayInitializations() {
        MeaningTree tree = new JavaTranslator(DEFAULT_CONFIG).getMeaningTree("""
                Box value = new Box(1);
                int[] values = new int[size];
                int[] seeded = new int[] {1, 2};
                """);

        String code = new CppTranslator(HEAP_CONFIG).getCode(tree);

        assertTrue(code.contains("Box value = new Box(1);"), code);
        assertTrue(code.contains("auto* values = new int[size];"), code);
        assertTrue(code.contains("auto* seeded = new int[] {1, 2};"), code);
    }

    @Test
    void preferHeapAllocKeepsExplicitStackAllocationOnTheStack() {
        MeaningTree tree = new CppTranslator(DEFAULT_CONFIG).getMeaningTree("Box value(1);");

        String code = new CppTranslator(HEAP_CONFIG).getCode(tree);

        assertTrue(code.contains("Box value(1);"), code);
    }
}
