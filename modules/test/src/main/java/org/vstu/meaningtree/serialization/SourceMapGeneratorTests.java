package org.vstu.meaningtree.serialization;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.*;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.loops.WhileLoop;
import org.vstu.meaningtree.utils.SourceMap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты разметки сгенерированного кода. {@link SourceMapGenerator} оборачивает каждый узел
 * невидимыми маркерами, поэтому проверяется и чистота итогового кода, и то, что байтовые
 * границы действительно указывают на текст соответствующего узла.
 */
public class SourceMapGeneratorTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    /** Маркеры разметки: если хоть один остался в коде, границы посчитаны неверно. */
    private static final String WORD_JOINER = "⁠";

    @Test
    void generatedCodeMatchesPlainGenerationForEveryLanguage() {
        for (Sample sample : samples()) {
            LanguageTranslator translator = sample.translator();
            MeaningTree tree = translator.getMeaningTree(sample.code());

            SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);

            assertEquals(translator.getCode(tree), sourceMap.code(),
                    "Watermarking changed generated code for " + sample.language());
            assertFalse(sourceMap.code().contains(WORD_JOINER),
                    "Watermark markers left in generated code for " + sample.language());
            assertEquals(sample.language(), sourceMap.language());
            assertFalse(sourceMap.bytePositions().isEmpty(),
                    "Source map is empty for " + sample.language());
        }
    }

    @Test
    void bytePositionsPointToRenderedTextOfNode() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree("""
                class Main {
                    public static void main(String[] args) {
                        int counter = 1;
                        while (counter < 10) {
                            counter = counter + 1;
                        }
                    }
                }
                """);
        SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);
        byte[] code = sourceMap.code().getBytes(StandardCharsets.UTF_8);

        WhileLoop loop = nodesOf(tree, WhileLoop.class).getFirst();
        String loopText = textOf(code, sourceMap.bytePositions().get(loop.getId()));
        assertTrue(loopText.startsWith("while"), "Expected loop text, got: " + loopText);
        assertTrue(loopText.contains("counter = counter + 1"));

        List<SimpleIdentifier> identifiers = nodesOf(tree, SimpleIdentifier.class).stream()
                .filter(identifier -> identifier.getName().equals("counter"))
                .toList();
        assertFalse(identifiers.isEmpty());
        for (SimpleIdentifier identifier : identifiers) {
            Pair<Integer, Integer> position = sourceMap.bytePositions().get(identifier.getId());
            if (position != null) {
                assertEquals("counter", textOf(code, position));
            }
        }
    }

    @Test
    void bytePositionsAreCountedInBytesNotCharacters() {
        PythonTranslator translator = new PythonTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree("""
                текст = "многобайтовая строка"
                конец = 1
                """);
        SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);
        byte[] code = sourceMap.code().getBytes(StandardCharsets.UTF_8);

        StringLiteral literal = nodesOf(tree, StringLiteral.class).getFirst();
        Pair<Integer, Integer> position = sourceMap.bytePositions().get(literal.getId());
        assertNotNull(position, "String literal must be present in the source map");

        String text = textOf(code, position);
        assertTrue(text.contains("многобайтовая строка"), "Expected literal text, got: " + text);
        assertTrue(position.getRight() > "многобайтовая строка".length(),
                "Length must be measured in UTF-8 bytes, not characters");
    }

    @Test
    void childPositionsAreNestedInsideParentPosition() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree("""
                class Main {
                    public static void main(String[] args) {
                        int x = 1;
                        if (x > 0) {
                            x = 2;
                        }
                    }
                }
                """);
        SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);

        IfStatement ifStatement = nodesOf(tree, IfStatement.class).getFirst();
        Pair<Integer, Integer> parent = sourceMap.bytePositions().get(ifStatement.getId());
        assertNotNull(parent);

        for (var info : ifStatement) {
            Pair<Integer, Integer> child = sourceMap.bytePositions().get(info.node().getId());
            if (child == null) {
                continue;
            }
            assertTrue(child.getLeft() >= parent.getLeft() && childEnd(child) <= childEnd(parent),
                    "Child position of " + info.node() + " escapes its parent bounds");
        }
    }

    @Test
    void metricsContainCyclomaticComplexity() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        MeaningTree straightLine = translator.getMeaningTree(
                "class Main { public static void main(String[] args) { int x = 1; } }"
        );
        MeaningTree branching = translator.getMeaningTree("""
                class Main {
                    public static void main(String[] args) {
                        int x = 1;
                        if (x > 0) {
                            x = 2;
                        }
                        while (x > 0) {
                            x--;
                        }
                    }
                }
                """);

        int simpleComplexity = new SourceMapGenerator(translator).process(straightLine)
                .metrics().get("cyclomatic").intValue();
        int branchingComplexity = new SourceMapGenerator(translator).process(branching)
                .metrics().get("cyclomatic").intValue();

        assertEquals(1, simpleComplexity);
        assertEquals(3, branchingComplexity, "One if branch and one loop add one each");
    }

    @Test
    void scopeTableIsAttachedToSourceMap() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree("""
                class Box {
                    int value;
                    int get() {
                        return value;
                    }
                }
                """);

        SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);

        assertNotNull(sourceMap.scopeTable());
        assertFalse(sourceMap.scopeTable().allScopes().isEmpty(),
                "Scope table must be attached to the source map");
    }

    @Test
    void processAcceptsBothTreeAndRootNode() {
        PythonTranslator translator = new PythonTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree("x = 1\ny = x + 2\n");
        Node root = tree.getRootNode();

        SourceMap fromTree = new SourceMapGenerator(translator).process(tree);
        SourceMap fromNode = new SourceMapGenerator(translator).process(root);

        assertEquals(fromTree.code(), fromNode.code());
        assertEquals(statementPositions(fromTree, tree), statementPositions(fromNode, tree));
        assertEquals(fromTree.metrics().get("cyclomatic"), fromNode.metrics().get("cyclomatic"));
        assertSame(tree, fromTree.root());
        assertSame(root, fromNode.root());
    }

    @Test
    void repeatedGenerationProducesIdenticalSourceMaps() {
        CppTranslator translator = new CppTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree("""
                int main() {
                    int x = 1;
                    for (int i = 0; i < 3; i++) {
                        x += i;
                    }
                    return x;
                }
                """);

        SourceMap first = new SourceMapGenerator(translator).process(tree);
        SourceMap second = new SourceMapGenerator(translator).process(tree);

        assertEquals(first.code(), second.code());
        assertEquals(statementPositions(first, tree), statementPositions(second, tree));
    }

    @Test
    void generatorDoesNotLeaveMarkersInTranslatorUsedAfterwards() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree(
                "class Main { public static void main(String[] args) { int x = 1; } }"
        );

        new SourceMapGenerator(translator).process(tree);
        String plainCode = translator.getCode(tree);

        assertFalse(plainCode.contains(WORD_JOINER),
                "Watermarking hook must not outlive the generator");
    }

    @Test
    void sourceContextOfTranslatorIsCarriedIntoSourceMap() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree(
                "class Main { public static void main(String[] args) { int x = 1; } }"
        );
        // Разбор сбрасывает привязку к файлу (см. finalizeParsingState), поэтому контекст
        // выставляется перед генерацией — генератор обязан перенести его на свою копию транслятора
        translator.withSourceContext(Path.of("/projects/demo").toAbsolutePath(), Path.of("src/Main.java"));

        SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);

        assertEquals(Path.of("/projects/demo").toAbsolutePath().toString(), sourceMap.projectRootPath());
        assertEquals(Path.of("src/Main.java").toString(), sourceMap.projectFileRelPath());
    }

    @Test
    void rootAndTopLevelStatementsAreMarked() {
        for (Sample sample : samples()) {
            LanguageTranslator translator = sample.translator();
            MeaningTree tree = translator.getMeaningTree(sample.code());
            SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);

            assertTrue(sourceMap.bytePositions().containsKey(tree.getRootNode().getId()),
                    "Tree root is not marked (" + sample.language() + ")");

            ProgramEntryPoint entryPoint = assertInstanceOf(ProgramEntryPoint.class, tree.getRootNode());
            for (Node statement : entryPoint.getBody()) {
                assertTrue(sourceMap.bytePositions().containsKey(statement.getId()),
                        "Top level statement is not marked (" + sample.language() + "): " + statement);
            }
        }
    }

    @Test
    void positionsStayWithinGeneratedCode() {
        for (Sample sample : samples()) {
            LanguageTranslator translator = sample.translator();
            SourceMap sourceMap = new SourceMapGenerator(translator)
                    .process(translator.getMeaningTree(sample.code()));
            int length = sourceMap.code().getBytes(StandardCharsets.UTF_8).length;

            for (var entry : sourceMap.bytePositions().entrySet()) {
                Pair<Integer, Integer> position = entry.getValue();
                assertTrue(position.getLeft() >= 0, "Negative offset in " + sample.language());
                assertTrue(position.getRight() >= 0, "Negative length in " + sample.language());
                assertTrue(childEnd(position) <= length,
                        "Position escapes generated code in " + sample.language());
            }
        }
    }

    /* -----------------------------
    |          Инструменты          |
    ------------------------------ */

    private record Sample(String language, String code, java.util.function.Supplier<LanguageTranslator> factory) {
        LanguageTranslator translator() {
            return factory.get();
        }
    }

    private static List<Sample> samples() {
        return List.of(
                new Sample("java", """
                        class Main {
                            public static void main(String[] args) {
                                int x = 1;
                                if (x > 0) {
                                    x = 2;
                                } else {
                                    x = 3;
                                }
                                while (x > 0) {
                                    x--;
                                }
                            }
                        }
                        """, () -> new JavaTranslator(CONFIG)),
                new Sample("python", """
                        x = 1
                        if x > 0:
                            x = 2
                        else:
                            x = 3
                        for i in range(0, 3):
                            print(i)
                        """, () -> new PythonTranslator(CONFIG)),
                new Sample("c++", """
                        int main() {
                            int x = 1;
                            if (x > 0) {
                                x = 2;
                            }
                            for (int i = 0; i < 3; i++) {
                                x += i;
                            }
                            return x;
                        }
                        """, () -> new CppTranslator(CONFIG))
        );
    }

    /**
     * Позиции корня и операторов верхнего уровня — та часть разметки, которая обязана быть
     * одинаковой при каждом прогоне.
     * <p>
     * Полную карту между прогонами сравнивать нельзя: viewer\'ы создают при отрисовке
     * вспомогательные узлы (идентификатор {@code print} в Python, узлы типов в C++), и эти
     * записи получают новые id на каждый прогон.
     */
    private static Map<Long, Pair<Integer, Integer>> statementPositions(SourceMap sourceMap, MeaningTree tree) {
        List<Long> ids = new java.util.ArrayList<>();
        ids.add(tree.getRootNode().getId());
        if (tree.getRootNode() instanceof ProgramEntryPoint entryPoint) {
            entryPoint.getBody().forEach(statement -> ids.add(statement.getId()));
        }
        return sourceMap.bytePositions().entrySet().stream()
                .filter(entry -> ids.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static String textOf(byte[] code, Pair<Integer, Integer> position) {
        assertNotNull(position, "Node has no byte position");
        return new String(code, position.getLeft(), position.getRight(), StandardCharsets.UTF_8);
    }

    private static int childEnd(Pair<Integer, Integer> position) {
        return position.getLeft() + position.getRight();
    }

    private static <T extends Node> List<T> nodesOf(MeaningTree tree, Class<T> nodeClass) {
        return StreamSupport.stream(tree.spliterator(), false)
                .map(info -> info.node())
                .filter(nodeClass::isInstance)
                .map(nodeClass::cast)
                .toList();
    }
}
