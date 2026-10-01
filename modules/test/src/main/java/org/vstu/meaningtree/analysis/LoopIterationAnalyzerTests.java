package org.vstu.meaningtree.analysis;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.comparison.LtOp;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.BoolLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.ListLiteral;
import org.vstu.meaningtree.nodes.expressions.other.Range;
import org.vstu.meaningtree.nodes.expressions.unary.PostfixIncrementOp;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.analysis.loops.LoopIterationAnalyzer;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoopIterationAnalyzerTests {
    private static final java.util.Map<String, Object> DEFAULT_CONFIG = java.util.Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    @Test
    void rangeForLoopCountsAscendingExclusiveRange() {
        RangeForLoop loop = new RangeForLoop(
                new Range(new IntegerLiteral(0), new IntegerLiteral(10), new IntegerLiteral(2), false, true, Range.Direction.UNKNOWN),
                new SimpleIdentifier("i"),
                new CompoundStatement()
        );

        LoopIterationEstimate estimate = analyzeLoop(loop);

        assertEstimate(estimate, LoopIterationCount.FIXED, 5, true, Range.Direction.UP);
        assertEstimate(loop.getRange().getIterationEstimate().orElseThrow(), LoopIterationCount.FIXED, 5, true, Range.Direction.UP);
    }

    @Test
    void rangeForLoopCountsDescendingInclusiveRange() {
        RangeForLoop loop = new RangeForLoop(
                new Range(new IntegerLiteral(10), new IntegerLiteral(0), new IntegerLiteral(-2), false, false, Range.Direction.UNKNOWN),
                new SimpleIdentifier("i"),
                new CompoundStatement()
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.FIXED, 6, true, Range.Direction.DOWN);
    }

    @Test
    void rangeForLoopWithDirectionStepMismatchBecomesInfinite() {
        RangeForLoop loop = new RangeForLoop(
                new Range(new IntegerLiteral(10), new IntegerLiteral(0), new IntegerLiteral(1), false, true, Range.Direction.UNKNOWN),
                new SimpleIdentifier("i"),
                new CompoundStatement()
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.INFINITE, null, true, Range.Direction.DOWN);
    }

    @Test
    void rangeForLoopReturnsInfiniteWhenStepIsZeroAndConditionHolds() {
        RangeForLoop loop = new RangeForLoop(
                new Range(new IntegerLiteral(0), new IntegerLiteral(10), new IntegerLiteral(0), false, true, Range.Direction.UNKNOWN),
                new SimpleIdentifier("i"),
                new CompoundStatement()
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.INFINITE, null, true, Range.Direction.UP);
    }

    @Test
    void rangeForLoopReturnsManyWhenBoundsAreNotConstant() {
        RangeForLoop loop = new RangeForLoop(
                new Range(new SimpleIdentifier("start"), new IntegerLiteral(10), null, false, true, Range.Direction.UNKNOWN),
                new SimpleIdentifier("i"),
                new CompoundStatement()
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.MANY, null, false, Range.Direction.UNKNOWN);
    }

    @Test
    void infiniteLoopWithoutEarlyExitIsInfinite() {
        InfiniteLoop loop = new InfiniteLoop(new CompoundStatement(), LoopType.WHILE);

        assertEstimate(analyzeLoop(loop), LoopIterationCount.INFINITE, null, true, Range.Direction.UNKNOWN);
    }

    @Test
    void infiniteLoopWithBreakIsUndefined() {
        InfiniteLoop loop = new InfiniteLoop(new CompoundStatement(new BreakStatement()), LoopType.WHILE);

        assertEstimate(analyzeLoop(loop), LoopIterationCount.UNDEFINED, null, false, Range.Direction.UNKNOWN);
    }

    @Test
    void forEachLoopCountsLiteralCollection() {
        ForEachLoop loop = new ForEachLoop(
                new VariableDeclaration(new IntType(), new SimpleIdentifier("item")),
                new ListLiteral(new IntegerLiteral(1), new IntegerLiteral(2), new IntegerLiteral(3)),
                new CompoundStatement()
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.FIXED, 3, true, Range.Direction.UNKNOWN);
    }

    @Test
    void generalForLoopCountsCanonicalIterationCount() {
        SimpleIdentifier i = new SimpleIdentifier("i");
        GeneralForLoop loop = new GeneralForLoop(
                new VariableDeclaration(new IntType(), i, new IntegerLiteral(0)),
                new LtOp(new SimpleIdentifier("i"), new IntegerLiteral(10)),
                new PostfixIncrementOp(new SimpleIdentifier("i")),
                new CompoundStatement()
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.FIXED, 10, true, Range.Direction.UP);
    }

    @Test
    void generalForLoopBecomesUndefinedWhenBodyWritesInductionVariableTwice() {
        SimpleIdentifier i = new SimpleIdentifier("i");
        GeneralForLoop loop = new GeneralForLoop(
                new VariableDeclaration(new IntType(), i, new IntegerLiteral(0)),
                new LtOp(new SimpleIdentifier("i"), new IntegerLiteral(10)),
                new PostfixIncrementOp(new SimpleIdentifier("i")),
                new CompoundStatement(
                        new ExpressionStatement(new PostfixIncrementOp(new SimpleIdentifier("i"))),
                        new AssignmentStatement(new SimpleIdentifier("i"), new IntegerLiteral(3))
                )
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.UNDEFINED, null, false, Range.Direction.UNKNOWN);
    }

    @Test
    void generalForLoopWithoutConditionIsInfinite() {
        SimpleIdentifier i = new SimpleIdentifier("i");
        GeneralForLoop loop = new GeneralForLoop(
                new VariableDeclaration(new IntType(), i, new IntegerLiteral(0)),
                null,
                new PostfixIncrementOp(new SimpleIdentifier("i")),
                new CompoundStatement()
        );

        assertEstimate(analyzeLoop(loop), LoopIterationCount.INFINITE, null, true, Range.Direction.UNKNOWN);
    }

    @Test
    void whileLoopWithConstantFalseHasZeroIterations() {
        WhileLoop loop = new WhileLoop(new BoolLiteral(false), new CompoundStatement());

        assertEstimate(analyzeLoop(loop), LoopIterationCount.ZERO, 0, true, Range.Direction.UNKNOWN);
    }

    @Test
    void whileLoopWithInitializerAndIncrementIsCounted() {
        LoopIterationEstimate estimate = analyzeFirstJavaLoop("""
                class Main {
                    void test() {
                        int i = 0;
                        while (i < 3) {
                            i++;
                        }
                    }
                }
                """);

        assertEstimate(estimate, LoopIterationCount.FIXED, 3, true, Range.Direction.UP);
    }

    @Test
    void doWhileLoopWithConstantFalseHasOneIteration() {
        DoWhileLoop loop = new DoWhileLoop(new BoolLiteral(false), new CompoundStatement());

        assertEstimate(analyzeLoop(loop), LoopIterationCount.ONE, 1, true, Range.Direction.UNKNOWN);
    }

    @Test
    void doWhileLoopWithInitializerAndIncrementIsCounted() {
        LoopIterationEstimate estimate = analyzeFirstJavaLoop("""
                class Main {
                    void test() {
                        int i = 0;
                        do {
                            i++;
                        } while (i <= 2);
                    }
                }
                """);

        assertEstimate(estimate, LoopIterationCount.FIXED, 3, true, Range.Direction.UP);
    }

    /* -----------------------------------------------------------------
    |  Охранные проверки: оценка не должна обещать больше, чем даёт код  |
    ------------------------------------------------------------------ */

    @Test
    void rangeForLoopWithBreakIsNotCounted() {
        assertUnknown(cppLoop("for (int m = 0; m < 10; m++) { if (m == 5) break; }", RangeForLoop.class));
    }

    @Test
    void rangeForLoopWithReturnIsNotCounted() {
        assertUnknown(cppLoop("for (int p = 0; p < 4; p++) { return 1; }", RangeForLoop.class));
    }

    @Test
    void rangeForLoopWithRaiseIsNotCounted() {
        assertUnknown(cppLoop("for (int p = 0; p < 4; p++) { throw 1; }", RangeForLoop.class));
        assertUnknown(analyzeLoopOf(new JavaTranslator(DEFAULT_CONFIG), """
                class Main { void test() { for (int p = 0; p < 4; p++) { throw new RuntimeException(); } } }
                """, RangeForLoop.class));
        assertUnknown(analyzeLoopOf(new PythonTranslator(FULL_CONFIG), """
                def test():
                    for p in range(4):
                        raise ValueError()
                """, RangeForLoop.class));
    }

    @Test
    void rangeForLoopWithWriteToCounterIsNotCounted() {
        assertUnknown(cppLoop("for (int i = 0; i < 10; i++) { i += 2; }", RangeForLoop.class));
    }

    @Test
    void rangeForLoopWithContinueIsStillCounted() {
        assertFixed(cppLoop("for (int i = 0; i < 3; i++) { if (i == 1) continue; }", RangeForLoop.class), 3);
    }

    @Test
    void rangeForLoopWithBreakInNestedLoopIsStillCounted() {
        assertFixed(cppLoop("for (int i = 0; i < 3; i++) { for (int j = 0; j < 3; j++) { break; } }", RangeForLoop.class), 3);
    }

    @Test
    void breakWithoutBracesIsAnExitOfTheLoop() {
        assertUnknown(cppLoop("for (int i = 0; i < 3; i++) break;", RangeForLoop.class));
    }

    @Test
    void whileLoopIgnoresBreakOfNestedLoop() {
        assertFixed(cppLoop("int a = 3; while (a > 0) { a--; for (int j = 0; j < 3; j++) { break; } }", WhileLoop.class), 3);
    }

    @Test
    void emptyRangeStaysZeroEvenWithBreakInBody() {
        LoopIterationEstimate estimate = cppLoop("for (int i = 5; i < 5; i++) { break; }", RangeForLoop.class);
        assertEquals(LoopIterationCount.ZERO, estimate.kind());
        assertTrue(estimate.reliable());
    }

    @Test
    void notEqualConditionCountsStepsToTheBound() {
        assertFixed(cppLoop("for (int m = 0; m != 4; m++) { }", GeneralForLoop.class), 4);
        assertFixed(cppLoop("for (int m = 0; m != 6; m += 2) { }", GeneralForLoop.class), 3);
        assertFixed(cppLoop("for (int m = 4; m != 0; m--) { }", GeneralForLoop.class), 4);
    }

    @Test
    void notEqualConditionThatStepsOverTheBoundIsNotCounted() {
        assertUnknown(cppLoop("for (int m = 0; m != 5; m += 2) { }", GeneralForLoop.class));
        assertUnknown(cppLoop("for (int m = 0; m != 4; m--) { }", GeneralForLoop.class));
    }

    @Test
    void notEqualConditionAlreadyFalseIsZero() {
        assertEquals(LoopIterationCount.ZERO, cppLoop("for (int m = 4; m != 4; m++) { }", GeneralForLoop.class).kind());
    }

    @Test
    void equalConditionRunsOnceOrNever() {
        assertFixed(cppLoop("for (int m = 0; m == 0; m++) { }", GeneralForLoop.class), 1);
        assertEquals(LoopIterationCount.ZERO, cppLoop("for (int m = 1; m == 0; m++) { }", GeneralForLoop.class).kind());
    }

    @Test
    void generalForLoopWithExtraWriteInBodyIsNotCounted() {
        assertUnknown(cppLoop("for (int i = 0; i < 10; i = i + 1) { i += 2; }", GeneralForLoop.class));
    }

    @Test
    void whileLoopCountsEveryFormOfUnconditionalStep() {
        for (String step : List.of("a--;", "--a;", "a -= 1;", "a = a - 1;")) {
            LoopIterationEstimate estimate = cppLoop("int a = 3; while (a > 0) { " + step + " }", WhileLoop.class);
            assertEquals(Range.Direction.DOWN, estimate.direction(), step);
            assertFixed(estimate, 3);
        }
        assertFixed(cppLoop("int a = 0; while (a < 6) { a += 2; }", WhileLoop.class), 3);
        assertFixed(cppLoop("int a = 0; while (a < 6) { a = 2 + a; }", WhileLoop.class), 3);
    }

    @Test
    void doWhileLoopCountsPostfixIncrement() {
        assertFixed(cppLoop("int g = 0; do { g++; } while (g < 3);", DoWhileLoop.class), 3);
    }

    @Test
    void whileLoopWithConditionalStepIsNotCounted() {
        assertUnknown(cppLoop("bool c = true; int a = 3; while (a > 0) { if (c) a -= 1; }", WhileLoop.class));
    }

    @Test
    void whileLoopWithStepInsideNestedLoopIsNotCounted() {
        assertUnknown(cppLoop("int a = 3; while (a > 0) { for (int j = 0; j < 2; j++) { a -= 1; } }", WhileLoop.class));
    }

    @Test
    void whileLoopWithTwoStepsIsNotCounted() {
        assertUnknown(cppLoop("int a = 6; while (a > 0) { a -= 1; a -= 1; }", WhileLoop.class));
    }

    @Test
    void whileLoopWhoseAssignmentIsNotAStepIsNotCounted() {
        assertUnknown(cppLoop("int a = 3; while (a < 10) { a = 5; }", WhileLoop.class));
        assertUnknown(cppLoop("int a = 1; while (a < 10) { a += a; }", WhileLoop.class));
        assertUnknown(cppLoop("int a = 3; while (a > 0) { a = 10 - a; }", WhileLoop.class));
    }

    @Test
    void whileLoopWithBreakOrRaiseIsNotCounted() {
        assertUnknown(cppLoop("int a = 3; while (a > 0) { a--; if (a == 1) break; }", WhileLoop.class));
        assertUnknown(cppLoop("int a = 3; while (a > 0) { a--; throw 1; }", WhileLoop.class));
    }

    @Test
    void whileLoopWithCounterAddressTakenIsNotCounted() {
        assertUnknown(cppLoop("int a = 3; int *p = &a; while (a > 0) { a--; *p = 5; }", WhileLoop.class));
    }

    @Test
    void infiniteLoopThatRaisesIsNotInfinite() {
        assertEquals(LoopIterationCount.UNDEFINED, cppLoop("while (true) { throw 1; }", InfiniteLoop.class).kind());
    }

    @Test
    void neverWrittenVariableBoundIsCounted() {
        assertFixed(cppLoop("int n = 5; for (int i = 0; i < n; i++) { }", RangeForLoop.class), 5);
        assertFixed(cppLoop("const int n = 4; for (int i = 0; i < n; i++) { }", RangeForLoop.class), 4);
    }

    @Test
    void reassignedVariableBoundTakesTheNearestAssignment() {
        assertFixed(cppLoop("int n = 5; n = 2; for (int i = 0; i < n; i++) { }", RangeForLoop.class), 2);
        assertFixed(cppLoop("int n = 4; int m = 1; n = 5; m = 7; for (int i = 0; i < n; i++) { }", RangeForLoop.class), 5);
        assertFixed(cppLoop("int n = 5; n = 2; int k = 0; while (k < n) { k += 1; }", WhileLoop.class), 2);
        assertFixed(analyzeLoopOf(new JavaTranslator(DEFAULT_CONFIG), """
                class Main { void test() { int n = 5; n = 2; for (int i = 0; i < n; i++) { } } }
                """, RangeForLoop.class), 2);
        assertFixed(analyzeLoopOf(new PythonTranslator(FULL_CONFIG), """
                def test():
                    n = 5
                    n = 2
                    for i in range(n):
                        pass
                """, RangeForLoop.class), 2);
    }

    @Test
    void boundCopiedFromAVariableKeepsTheValueOfTheCopyPoint() {
        // k получила 5 до того, как n стало 2: нужно значение n в месте копирования, а не у цикла
        assertFixed(cppLoop("int n = 5; int k = n; n = 2; for (int i = 0; i < k; i++) { }", RangeForLoop.class), 5);
        assertFixed(cppLoop("int n = 5; n = 2; int k = n; n = 9; for (int i = 0; i < k; i++) { }", RangeForLoop.class), 2);
        assertFixed(cppLoop("int a = 2; int n = 1; n = a + 3; for (int i = 0; i < n; i++) { }", RangeForLoop.class), 5);
    }

    @Test
    void boundAssignedConditionallyIsNotCounted() {
        assertManyOrUnknown(cppLoop("bool c = true; int n = 5; if (c) { n = 2; } for (int i = 0; i < n; i++) { }", RangeForLoop.class));
        assertManyOrUnknown(cppLoop("bool c = true; int n = 5; n = 2; if (c) { n++; } for (int i = 0; i < n; i++) { }", RangeForLoop.class));
        assertManyOrUnknown(cppLoop("int n = 5; n = 2; n += 1; for (int i = 0; i < n; i++) { }", RangeForLoop.class));
        assertManyOrUnknown(cppLoop("int n = 5; n = 2; n = n + 1; for (int i = 0; i < n; i++) { }", RangeForLoop.class));
    }

    @Test
    void boundIsFoundThroughEnclosingIfButNotThroughEnclosingLoop() {
        assertFixed(cppLoop("bool c = true; int n = 5; n = 2; if (c) { for (int i = 0; i < n; i++) { } }", RangeForLoop.class), 2);
        assertFixed(cppLoop("bool c = true; int n = 5; n = 2; if (c) { n = 3; } else { for (int i = 0; i < n; i++) { } }", RangeForLoop.class), 2);
        // Внешний цикл может записать n после внутреннего: на второй заход значение уже другое
        assertManyOrUnknown(cppInnermostLoop(
                "int n = 5; n = 2; for (int r = 0; r < 3; r++) { for (int i = 0; i < n; i++) { } n++; }", RangeForLoop.class));
        // Без записи во внешнем цикле то же n верно на каждом заходе
        assertFixed(cppInnermostLoop(
                "int n = 5; n = 2; for (int r = 0; r < 3; r++) { for (int i = 0; i < n; i++) { } }", RangeForLoop.class), 2);
    }

    @Test
    void boundChangedByIfConditionIsNotCounted() {
        assertManyOrUnknown(cppLoop("int n = 5; n = 2; if (n-- > 0) { for (int i = 0; i < n; i++) { } }", RangeForLoop.class));
    }

    @Test
    void chainedAssignmentWritesTheBound() {
        assertManyOrUnknown(analyzeLoopOf(new PythonTranslator(FULL_CONFIG), """
                def test():
                    n = 5
                    n = m = 2
                    for i in range(n):
                        pass
                """, RangeForLoop.class));
    }

    @Test
    void pythonCounterWriteInRangeBodyIsHarmless() {
        assertFixed(analyzeLoopOf(new PythonTranslator(FULL_CONFIG), """
                def test():
                    for i in range(3):
                        i += 1
                """, RangeForLoop.class), 3);
        // В C++ та же запись сдвигает цикл
        assertUnknown(cppLoop("for (int i = 0; i < 3; i++) { i += 1; }", RangeForLoop.class));
        // Выход из цикла от языка не зависит
        assertUnknown(analyzeLoopOf(new PythonTranslator(FULL_CONFIG), """
                def test():
                    for i in range(3):
                        break
                """, RangeForLoop.class));
    }

    @Test
    void boundWrittenInsideTheLoopIsNotCounted() {
        assertManyOrUnknown(cppLoop("int n = 5; for (int i = 0; i < n; i++) { n--; }", RangeForLoop.class));
    }

    @Test
    void boundWithAliasIsNotCounted() {
        assertManyOrUnknown(cppLoop("int n = 5; int *p = &n; *p = 2; for (int i = 0; i < n; i++) { }", RangeForLoop.class));
        assertManyOrUnknown(cppLoop("int n = 5; int &r = n; r = 2; for (int i = 0; i < n; i++) { }", RangeForLoop.class));
    }

    @Test
    void boundReadFromInputIsNotCounted() {
        assertManyOrUnknown(cppLoop("int n = 5; std::cin >> n; for (int i = 0; i < n; i++) { }", RangeForLoop.class));
    }

    @Test
    void boundPassedByReferenceIsNotCountedButByValueIs() {
        String byReference = "void g(int &r) { r = 2; } int main() { int n = 5; g(n); for (int i = 0; i < n; i++) { } return 0; }";
        String byValue = "void h(int v) { } int main() { int n = 5; h(n); for (int i = 0; i < n; i++) { } return 0; }";
        assertManyOrUnknown(analyzeLoopOf(new CppTranslator(FULL_CONFIG), byReference, RangeForLoop.class));
        assertFixed(analyzeLoopOf(new CppTranslator(FULL_CONFIG), byValue, RangeForLoop.class), 5);
    }

    @Test
    void forEachOverMutatedCollectionIsNotCounted() {
        PythonTranslator python = new PythonTranslator(FULL_CONFIG);
        assertFixed(analyzeLoopOf(python, """
                def test():
                    xs = [1, 2, 3]
                    for x in xs:
                        pass
                """, ForEachLoop.class), 3);
        assertManyOrUnknown(analyzeLoopOf(python, """
                def test():
                    xs = [1, 2, 3]
                    xs.append(4)
                    for x in xs:
                        pass
                """, ForEachLoop.class));
    }

    @Test
    void forEachOverCollectionUsedByPureBuiltinsIsStillCounted() {
        PythonTranslator python = new PythonTranslator(FULL_CONFIG);
        for (String use : List.of("print(xs)", "n = len(xs)", "ys = sorted(xs)", "c = xs.count(1)", "t = max(xs)")) {
            assertFixed(analyzeLoopOf(python, "def test():\n    xs = [1, 2, 3]\n    " + use
                    + "\n    for x in xs:\n        pass\n", ForEachLoop.class), 3);
        }
    }

    @Test
    void forEachOverCollectionPassedToUnknownOrMutatingCallIsNotCounted() {
        PythonTranslator python = new PythonTranslator(FULL_CONFIG);
        for (String use : List.of("xs.pop()", "xs.extend([4])", "unknown(xs)", "xs.sort()")) {
            assertManyOrUnknown(analyzeLoopOf(python, "def test():\n    xs = [1, 2, 3]\n    " + use
                    + "\n    for x in xs:\n        pass\n", ForEachLoop.class));
        }
        // Пользовательская функция с именем встроенной встроенной не является
        assertManyOrUnknown(analyzeLoopOf(python, """
                def sum(v):
                    v.append(1)

                def test():
                    xs = [1, 2, 3]
                    sum(xs)
                    for x in xs:
                        pass
                """, ForEachLoop.class));
    }

    @Test
    void forEachLoopWithBreakIsNotCounted() {
        assertUnknown(analyzeLoopOf(new PythonTranslator(FULL_CONFIG), """
                def test():
                    for x in [1, 2, 3]:
                        break
                """, ForEachLoop.class));
    }

    private static final java.util.Map<String, Object> FULL_CONFIG = java.util.Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    /** Фрагмент, завёрнутый в {@code main}: так же, как пишут его сами тесты на C++. */
    private static LoopIterationEstimate cppLoop(String body, Class<? extends Loop> type) {
        String code = "#include <iostream>\nint main() {\n" + body + "\nreturn 0;\n}\n";
        return analyzeLoopOf(new CppTranslator(FULL_CONFIG), code, type);
    }

    /** Внутренний цикл: у него глубина в дереве наибольшая. */
    private static LoopIterationEstimate cppInnermostLoop(String body, Class<? extends Loop> type) {
        String code = "#include <iostream>\nint main() {\n" + body + "\nreturn 0;\n}\n";
        return analyzeLoopOf(new CppTranslator(FULL_CONFIG), code, type, true);
    }

    private static LoopIterationEstimate analyzeLoopOf(LanguageTranslator translator,
                                                       String code,
                                                       Class<? extends Loop> type) {
        return analyzeLoopOf(translator, code, type, false);
    }

    private static LoopIterationEstimate analyzeLoopOf(LanguageTranslator translator,
                                                       String code,
                                                       Class<? extends Loop> type,
                                                       boolean innermost) {
        MeaningTree tree = translator.getMeaningTree(code);
        List<Loop> loops = StreamSupport.stream(tree.spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(type::isInstance)
                .map(type::cast)
                .map(loop -> (Loop) loop)
                .toList();
        if (loops.isEmpty()) {
            throw new AssertionError("no " + type.getSimpleName() + " in: " + code);
        }
        // При вложенности по умолчанию проверяется внешний цикл: порядок обхода дерева на это не рассчитан
        java.util.Comparator<Loop> byDepth = java.util.Comparator.comparingInt(loop -> depthOf(tree, loop));
        Loop chosen = innermost ? loops.stream().max(byDepth).orElseThrow() : loops.stream().min(byDepth).orElseThrow();
        return chosen.getIterationEstimate().orElseThrow();
    }

    private static int depthOf(MeaningTree tree, Node node) {
        int depth = 0;
        org.vstu.meaningtree.iterators.utils.NodeInfo info = tree.getNodeById(node.getId());
        while (info != null && info.parentNode() != null) {
            depth++;
            info = tree.getNodeById(info.parentNode().getId());
        }
        return depth;
    }

    private static void assertFixed(LoopIterationEstimate estimate, long iterations) {
        assertTrue(estimate.reliable(), "reliable: " + estimate);
        assertTrue(estimate.exactIterations().isPresent(), "exact iterations: " + estimate);
        assertEquals(iterations, estimate.exactIterations().getAsLong(), estimate.toString());
    }

    /** Код не определяет число итераций: ни числа, ни обещания достоверности. */
    private static void assertUnknown(LoopIterationEstimate estimate) {
        assertFalse(estimate.reliable(), "must not be reliable: " + estimate);
        assertTrue(estimate.exactIterations().isEmpty(), "must have no exact count: " + estimate);
    }

    private static void assertManyOrUnknown(LoopIterationEstimate estimate) {
        assertUnknown(estimate);
        assertTrue(estimate.kind() == LoopIterationCount.MANY || estimate.kind() == LoopIterationCount.UNDEFINED,
                "unexpected kind: " + estimate);
    }

    private static LoopIterationEstimate analyzeLoop(Loop loop) {
        MeaningTree tree = new MeaningTree(new ProgramEntryPoint(List.of(loop)));
        new LoopIterationAnalyzer().analyze(tree, new ScopeTable());
        return loop.getIterationEstimate().orElseThrow();
    }

    private static LoopIterationEstimate analyzeLoopInCompound(CompoundStatement compound) {
        ScopeTable scopeTable = new ScopeTable();
        scopeTable.enter(compound);
        for (Node node : compound.getNodeList()) {
            if (node instanceof VariableDeclaration declaration) {
                scopeTable.registerVariable(declaration);
            }
        }
        MeaningTree tree = new MeaningTree(new ProgramEntryPoint(List.of(compound)));
        new LoopIterationAnalyzer().analyze(tree, scopeTable);
        Loop loop = compound.getNodeList().stream()
                .filter(Loop.class::isInstance)
                .map(Loop.class::cast)
                .findFirst()
                .orElseThrow();
        return loop.getIterationEstimate().orElseThrow();
    }

    private static LoopIterationEstimate analyzeFirstJavaLoop(String code) {
        MeaningTree tree = new JavaTranslator(DEFAULT_CONFIG).getMeaningTree(code);
        return StreamSupport.stream(tree.spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(Loop.class::isInstance)
                .map(Loop.class::cast)
                .findFirst()
                .orElseThrow()
                .getIterationEstimate()
                .orElseThrow();
    }

    private static void assertEstimate(LoopIterationEstimate estimate,
                                       LoopIterationCount kind,
                                       Integer exactIterations,
                                       boolean reliable,
                                       Range.Direction direction) {
        assertEquals(kind, estimate.kind());
        if (exactIterations == null) {
            assertTrue(estimate.exactIterations().isEmpty());
        } else {
            assertTrue(estimate.exactIterations().isPresent());
            assertEquals(exactIterations.longValue(), estimate.exactIterations().getAsLong());
        }
        assertEquals(reliable, estimate.reliable());
        assertEquals(direction, estimate.direction());
    }
}
