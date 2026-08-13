package org.vstu.meaningtree;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.languages.SourceMapGenerator;
import org.vstu.meaningtree.nodes.expressions.comprehensions.ContainerBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.RangeBasedComprehension;
import org.vstu.meaningtree.utils.SourceMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ComprehensionConversionTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    @Test
    void pythonPreservesListSetAndDictionaryComprehensions() {
        PythonTranslator python = new PythonTranslator(CONFIG);

        assertTrue(python.getCode(python.getMeaningTree("result = [x for x in range(n)]"))
                .contains("[x for x in range(n)]"));
        assertTrue(python.getCode(python.getMeaningTree("result = {x for x in range(n)}"))
                .contains("{x for x in range(n)}"));
        assertTrue(python.getCode(python.getMeaningTree("result = {x: x for x in range(n)}"))
                .contains("{x: x for x in range(n)}"));
    }

    @Test
    void pythonParsesRangeAndContainerComprehensionsIntoTheirDedicatedNodes() {
        PythonTranslator python = new PythonTranslator(CONFIG);

        MeaningTree rangeTree = python.getMeaningTree("result = [x for x in range(n) if x > 0]");
        MeaningTree containerTree = python.getMeaningTree("result = [x for x in values if x > 0]");

        assertTrue(rangeTree.hasNodeType(RangeBasedComprehension.class));
        assertTrue(containerTree.hasNodeType(ContainerBasedComprehension.class));
    }

    @Test
    void javaAndCppLowerRangeBasedListComprehensionsWithoutMutatingTheSourceTree() {
        PythonTranslator python = new PythonTranslator(CONFIG);
        MeaningTree tree = python.getMeaningTree("""
                for x in range(m):
                    if x > func([next(k) for _item in range(t) if x > 5]):
                        print("Good")
                """);

        String javaCode = new JavaTranslator(CONFIG).getCode(tree);
        String cppCode = new CppTranslator(CONFIG).getCode(tree);

        assertTrue(javaCode.contains("java.util.ArrayList<Object> _tmp_compreh_1"), javaCode);
        assertTrue(javaCode.contains("_tmp_compreh_1.add(next(k))"), javaCode);
        assertTrue(cppCode.contains("std::vector<object> _tmp_compreh_1"), cppCode);
        assertTrue(cppCode.contains("_tmp_compreh_1.push_back(next(k))"), cppCode);
        assertTrue(python.getCode(tree).contains("[next(k) for _item in range(t) if x > 5]"));
    }

    @Test
    void javaAndCppLowerContainerSetAndDictionaryComprehensions() {
        PythonTranslator python = new PythonTranslator(CONFIG);
        JavaTranslator java = new JavaTranslator(CONFIG);
        CppTranslator cpp = new CppTranslator(CONFIG);

        MeaningTree container = python.getMeaningTree("result = [x for x in values]");
        MeaningTree set = python.getMeaningTree("result = {x for x in range(n)}");
        MeaningTree dictionary = python.getMeaningTree("result = {x: x for x in range(n)}");

        assertTrue(java.getCode(container).contains("for (Object x : values)"));
        assertTrue(cpp.getCode(container).contains("for (auto x : values)"));
        assertTrue(java.getCode(set).contains("java.util.HashSet<Object> _tmp_compreh_1"));
        assertTrue(cpp.getCode(set).contains("std::set<object> _tmp_compreh_1"));
        assertTrue(java.getCode(dictionary).contains("_tmp_compreh_1.put(x, x)"));
        assertTrue(cpp.getCode(dictionary).contains("_tmp_compreh_1.insert(std::make_pair(x, x))"));
    }

    @Test
    void loweredComprehensionsKeepSourceMapsBoundToTheOriginalTree() {
        PythonTranslator python = new PythonTranslator(CONFIG);
        MeaningTree tree = python.getMeaningTree("result = [x for x in range(n) if x > 0]");
        long comprehensionId = tree.iterate().stream()
                .filter(info -> info.node() instanceof RangeBasedComprehension)
                .findFirst()
                .orElseThrow()
                .node()
                .getId();

        for (var translator : new org.vstu.meaningtree.languages.LanguageTranslator[] {
                new JavaTranslator(CONFIG), new CppTranslator(CONFIG)
        }) {
            SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);
            assertTrue(sourceMap.bytePositions().containsKey(comprehensionId),
                    "The lowered comprehension is absent from the source map: " + sourceMap.bytePositions().keySet());
        }
    }
}
