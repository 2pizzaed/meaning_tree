package org.vstu.meaningtree.languages.helpers;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.loops.WhileLoop;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers LoopElseLowerer, the common-module pass that desugars Python's loop else-clause
 * (executed when a loop completes without hitting a break belonging to it) into a boolean
 * flag + trailing `if`, for languages (Java, C++) without native else-on-loop syntax.
 */
public class LoopElseLowererTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    @Test
    void loopWithoutBreakSplicesElseBodyWithoutFlag() {
        WhileLoop loop = new WhileLoop(new org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral(true),
                new CompoundStatement());
        loop.setElseBranch(new AssignmentStatement(
                new org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier("x"),
                new org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral(1)
        ));
        MeaningTree tree = new MeaningTree(new CompoundStatement(loop));

        MeaningTree lowered = LoopElseLowerer.lower(tree);

        CompoundStatement root = (CompoundStatement) lowered.getRootNode();
        assertEquals(2, root.getLength(), "expected [loop, spliced else-statement], no flag declaration");
        assertTrue(root.getNodes()[0] instanceof WhileLoop);
        assertTrue(root.getNodes()[1] instanceof AssignmentStatement);
        assertFalse(((Loop) root.getNodes()[0]).hasElseBranch());
        assertNoBooleanFlagDeclaration(lowered);
    }

    @Test
    void loopWithBreakUsesFlagAndTrailingIf() {
        MeaningTree lowered = pythonToJavaAst("""
                while x < 10:
                    if x == 5:
                        break
                    x = x + 1
                else:
                    y = 1
                """);

        CompoundStatement root = rootBody(lowered);
        assertEquals(3, root.getLength(), "expected [flag decl, loop, guarded if]");
        assertTrue(root.getNodes()[1] instanceof Loop);
        assertTrue(root.getNodes()[2] instanceof IfStatement);
        assertFalse(((Loop) root.getNodes()[1]).hasElseBranch());

        long breakCount = StreamSupport.stream(root.getNodes()[1].spliterator(), false)
                .filter(info -> info.node() instanceof BreakStatement)
                .count();
        long assignmentCount = StreamSupport.stream(root.getNodes()[1].spliterator(), false)
                .filter(info -> info.node() instanceof AssignmentStatement)
                .count();
        assertEquals(1, breakCount);
        assertEquals(1, assignmentCount, "flag = false; must be injected exactly once, right before the break");
    }

    @Test
    void nestedLoopsIndependentBreaksSetOnlyTheirOwnFlag() {
        String java = pythonToJavaCode("""
                while True:
                    for i in range(2):
                        if i == 1:
                            break
                    else:
                        y = 1
                    break
                else:
                    x = 1
                """);

        assertTrue(java.contains("boolean _loop_else_1 = true;"));
        assertTrue(java.contains("boolean _loop_else_2 = true;"));
        // inner loop's break must not touch the outer loop's flag, and vice versa
        assertEquals(1, countOccurrences(java, "_loop_else_1 = false;"));
        assertEquals(1, countOccurrences(java, "_loop_else_2 = false;"));
    }

    @Test
    void javaAndCppRenderFlagBasedDesugaring() {
        String source = """
                for i in range(10):
                    if i == 5:
                        break
                else:
                    x = 1
                """;

        String java = new JavaTranslator(CONFIG).getCode(new PythonTranslator(CONFIG).getMeaningTree(source));
        String cpp = new CppTranslator(CONFIG).getCode(new PythonTranslator(CONFIG).getMeaningTree(source));

        for (String generated : new String[]{java, cpp}) {
            assertTrue(generated.contains("_loop_else_1 = true;"));
            assertTrue(generated.contains("_loop_else_1 = false;"));
            assertTrue(generated.contains("if (_loop_else_1)"));
            assertFalse(generated.contains("else:"), "Java/C++ output must not contain a Python-style loop else clause");
        }
    }

    @Test
    void pythonRendersElseNatively() {
        String source = """
                for i in range(10):
                    if i == 5:
                        break
                else:
                    x = 1
                """;
        String python = new PythonTranslator(CONFIG).getCode(new PythonTranslator(CONFIG).getMeaningTree(source));
        assertTrue(python.contains("else:\n    x"), "Python output must keep the native else clause: " + python);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static MeaningTree pythonToJavaAst(String pythonSource) {
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree(pythonSource);
        return LoopElseLowerer.lower(tree);
    }

    private static String pythonToJavaCode(String pythonSource) {
        return new JavaTranslator(CONFIG).getCode(new PythonTranslator(CONFIG).getMeaningTree(pythonSource));
    }

    private static CompoundStatement rootBody(MeaningTree tree) {
        Node root = tree.getRootNode();
        if (root instanceof CompoundStatement compound) {
            return compound;
        }
        // simple translationUnitMode wraps top-level statements in ProgramEntryPoint
        var entryPoint = (org.vstu.meaningtree.nodes.ProgramEntryPoint) root;
        return new CompoundStatement(entryPoint.getBody());
    }

    private static void assertNoBooleanFlagDeclaration(MeaningTree tree) {
        boolean hasFlagDecl = StreamSupport.stream(tree.spliterator(), false)
                .anyMatch(info -> info.node() instanceof org.vstu.meaningtree.nodes.declarations.VariableDeclaration decl
                        && decl.getType() instanceof org.vstu.meaningtree.nodes.types.builtin.BooleanType);
        assertFalse(hasFlagDecl);
    }
}
