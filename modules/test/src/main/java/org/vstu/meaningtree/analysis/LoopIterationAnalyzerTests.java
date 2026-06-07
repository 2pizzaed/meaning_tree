package org.vstu.meaningtree.analysis;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.JavaTranslator;
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
    void whileLoopWithInitializerAndIncrementCurrentlyDegradesToUndefined() {
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

        assertEstimate(estimate, LoopIterationCount.UNDEFINED, null, false, Range.Direction.UNKNOWN);
    }

    @Test
    void doWhileLoopWithConstantFalseHasOneIteration() {
        DoWhileLoop loop = new DoWhileLoop(new BoolLiteral(false), new CompoundStatement());

        assertEstimate(analyzeLoop(loop), LoopIterationCount.ONE, 1, true, Range.Direction.UNKNOWN);
    }

    @Test
    void doWhileLoopWithInitializerAndIncrementCurrentlyDegradesToUndefined() {
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

        assertEstimate(estimate, LoopIterationCount.UNDEFINED, null, false, Range.Direction.UNKNOWN);
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
