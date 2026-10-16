package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.loops.WhileLoop;
import org.vstu.meaningtree.utils.Label;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Параметр {@code forceCompoundBodies}: парсер оборачивает одиночные тела циклов и веток
 * {@code if} в блок, viewer печатает такие тела в скобках, не меняя дерево.
 */
class ForceCompoundBodiesTests {
    private static final Map<String, Object> DEFAULT = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );
    private static final Map<String, Object> FORCED = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false,
            "forceCompoundBodies", true
    );

    private static final String JAVA_SOURCE = """
            int s = 0;
            for (int i = 0; i < 10; i++)
                if (i % 2 == 0)
                    s += i;
                else if (i == 3)
                    s -= 1;
                else
                    s *= 2;
            while (s > 100)
                s--;
            do
                s++;
            while (s < 5);
            for (int x : values)
                s += x;
            """;

    private static final String CPP_SOURCE = """
            int s = 0;
            for (int i = 0; i < 10; i++)
                if (i % 2 == 0)
                    s += i;
                else if (i == 3)
                    s -= 1;
                else
                    s *= 2;
            while (s > 100)
                s--;
            while (true)
                break;
            while (s > 0);
            """;

    @Test
    void parserWrapsEveryLoopBodyAndIfBranch() {
        MeaningTree tree = new JavaTranslator(FORCED).getMeaningTree(JAVA_SOURCE);

        List<Statement> bodies = loopAndBranchBodies(tree);
        assertFalse(bodies.isEmpty());
        for (Statement body : bodies) {
            assertInstanceOf(CompoundStatement.class, body);
        }
    }

    @Test
    void parserLeavesTreeAsIsByDefault() {
        MeaningTree tree = new JavaTranslator(DEFAULT).getMeaningTree(JAVA_SOURCE);

        assertTrue(loopAndBranchBodies(tree).stream().anyMatch(body -> !(body instanceof CompoundStatement)));
    }

    @Test
    void parserKeepsElseIfChain() {
        MeaningTree tree = new CppTranslator(FORCED).getMeaningTree("""
                if (a)
                    f();
                else
                    if (b)
                        g();
                """);

        IfStatement outer = firstOf(tree, IfStatement.class);
        // else, который сам является if, не оборачивается — иначе вышло бы else { if ... }
        if (outer.hasElseBranch()) {
            assertInstanceOf(IfStatement.class, outer.getElseBranch());
        } else {
            assertEquals(2, outer.getBranches().size());
        }
        String generated = new CppTranslator(FORCED).getCode(tree);
        assertTrue(generated.contains("else if (b)"), generated);
    }

    @Test
    void wrappingBlockIsRemappedToTheWrappedBody() {
        MeaningTree plain = new JavaTranslator(DEFAULT).getMeaningTree("while (c) x();");
        MeaningTree forced = new JavaTranslator(FORCED).getMeaningTree("while (c) x();");
        assertFalse(firstOf(plain, WhileLoop.class).getBody() instanceof CompoundStatement);

        CompoundStatement wrapped = assertInstanceOf(CompoundStatement.class, firstOf(forced, WhileLoop.class).getBody());
        Node body = wrapped.getNodes()[0];
        assertEquals(body.getId(), wrapped.getLabel(Label.REMAPPED).getAttribute());
    }

    @Test
    void javaViewerBracesSingleStatementBodies() {
        String generated = renderParsedWithoutFlag(new JavaTranslator(DEFAULT), new JavaTranslator(FORCED), JAVA_SOURCE);

        assertTrue(generated.contains("for (int i = 0; i < 10; i++) {"), generated);
        assertTrue(generated.contains("if (i % 2 == 0) {"), generated);
        assertTrue(generated.contains("else if (i == 3) {"), generated);
        assertTrue(generated.contains("else {"), generated);
        assertTrue(generated.contains("while (s > 100) {"), generated);
        assertTrue(generated.contains("for (int x : values) {"), generated);
    }

    @Test
    void cppViewerBracesSingleStatementBodies() {
        String generated = renderParsedWithoutFlag(new CppTranslator(DEFAULT), new CppTranslator(FORCED), CPP_SOURCE);

        assertTrue(generated.contains("while (s > 100)\n{\n    s--;\n}"), generated);
        assertTrue(generated.contains("while (true)\n{\n    break;\n}"), generated);
        assertTrue(generated.contains("while (s > 0)\n{\n    ;\n}"), generated);
        assertTrue(generated.contains("else if (i == 3)\n    {"), generated);
    }

    @Test
    void viewerAloneMatchesParserAndViewerTogether() {
        // Скобки, дописанные viewer'ом текстом, должны совпадать с выводом настоящего блока
        assertEquals(
                new JavaTranslator(FORCED).getCode(new JavaTranslator(FORCED).getMeaningTree(JAVA_SOURCE)),
                renderParsedWithoutFlag(new JavaTranslator(DEFAULT), new JavaTranslator(FORCED), JAVA_SOURCE)
        );
        assertEquals(
                new CppTranslator(FORCED).getCode(new CppTranslator(FORCED).getMeaningTree(CPP_SOURCE)),
                renderParsedWithoutFlag(new CppTranslator(DEFAULT), new CppTranslator(FORCED), CPP_SOURCE)
        );
    }

    @Test
    void viewerDoesNotChangeTheTree() {
        JavaTranslator plain = new JavaTranslator(DEFAULT);
        MeaningTree tree = plain.getMeaningTree(JAVA_SOURCE);
        long before = loopAndBranchBodies(tree).stream().filter(CompoundStatement.class::isInstance).count();

        new JavaTranslator(FORCED).getCode(tree);

        assertEquals(before, loopAndBranchBodies(tree).stream().filter(CompoundStatement.class::isInstance).count());
    }

    @Test
    void viewerKeepsSingleStatementBodiesByDefault() {
        String generated = renderParsedWithoutFlag(new JavaTranslator(DEFAULT), new JavaTranslator(DEFAULT), JAVA_SOURCE);

        assertTrue(generated.contains("while (s > 100)\n"), generated);
        assertFalse(generated.contains("while (s > 100) {"), generated);
    }

    private static String renderParsedWithoutFlag(LanguageTranslator parser, LanguageTranslator viewer, String source) {
        return viewer.getCode(parser.getMeaningTree(source));
    }

    private static List<Statement> loopAndBranchBodies(MeaningTree tree) {
        List<Statement> bodies = new ArrayList<>();
        for (NodeInfo info : tree) {
            Node node = info.node();
            if (node instanceof Loop loop) {
                bodies.add(loop.getBody());
            } else if (node instanceof IfStatement ifStatement) {
                for (ConditionBranch branch : ifStatement.getBranches()) {
                    bodies.add(branch.getBody());
                }
                if (ifStatement.hasElseBranch() && !(ifStatement.getElseBranch() instanceof IfStatement)) {
                    bodies.add(ifStatement.getElseBranch());
                }
            }
        }
        return bodies;
    }

    private static <T extends Node> T firstOf(MeaningTree tree, Class<T> type) {
        for (NodeInfo info : tree) {
            if (type.isInstance(info.node())) {
                return type.cast(info.node());
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " in tree");
    }
}
