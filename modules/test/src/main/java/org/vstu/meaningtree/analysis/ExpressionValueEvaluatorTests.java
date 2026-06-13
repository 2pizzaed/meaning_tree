package org.vstu.meaningtree.analysis;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.comparison.GtOp;
import org.vstu.meaningtree.nodes.expressions.comparison.LtOp;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryMinusOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryPlusOp;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEstimate;
import org.vstu.meaningtree.utils.analysis.expressions.ExpressionValueEvaluator;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

public class ExpressionValueEvaluatorTests {
    private static final Map<String, Object> DEFAULT_CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    @Test
    void boolLiteralGetsExactEstimate() {
        BoolLiteral expression = new BoolLiteral(true);
        ExpressionValueEstimate<?> estimate = evaluatorFor(expression).estimate(expression, expression);

        assertEquals(java.util.Optional.of(true), estimate.exactValue());
        assertEquals(Set.of(Boolean.TRUE), estimate.possibleValues());
        assertTrue(estimate.reliable());
    }

    @Test
    void unknownComparisonGetsPossibleBooleanEstimate() {
        GtOp expression = new GtOp(new SimpleIdentifier("a"), new IntegerLiteral(0));
        ExpressionValueEstimate<?> estimate = evaluatorFor(expression).estimate(expression, expression);

        assertTrue(expression.getValueEstimate().isPresent());
        assertTrue(estimate.exactValue().isEmpty());
        assertEquals(Set.of(Boolean.TRUE, Boolean.FALSE), estimate.possibleValues());
        assertFalse(estimate.reliable());
    }

    @Test
    void analyzePopulatesValueEstimateForExpressionsAcrossTree() {
        GtOp condition = new GtOp(new SimpleIdentifier("a"), new IntegerLiteral(0));
        CompoundStatement context = bindScopedContext(new ExpressionStatement(condition));
        ExpressionValueEvaluator evaluator = evaluatorFor(context, condition);

        evaluator.analyze();

        ExpressionValueEstimate<?> estimate = condition.getValueEstimate().orElseThrow();
        assertTrue(estimate.exactValue().isEmpty());
        assertEquals(Set.of(Boolean.TRUE, Boolean.FALSE), estimate.possibleValues());
        assertFalse(estimate.reliable());
    }

    @Test
    void evaluateAsBooleanUsesNumericEnvironmentForComparison() {
        GtOp expression = new GtOp(new SimpleIdentifier("i"), new IntegerLiteral(0));
        ExpressionValueEvaluator evaluator = evaluatorFor(expression);

        assertEquals(java.util.Optional.of(true), evaluator.evaluateAsBoolean(expression, Map.of("i", 1L), expression));
        assertEquals(java.util.Optional.of(false), evaluator.evaluateAsBoolean(expression, Map.of("i", 0L), expression));
    }

    @Test
    void evaluateAsBooleanInvertsComparisonWhenIdentifierIsOnRight() {
        LtOp expression = new LtOp(new IntegerLiteral(0), new SimpleIdentifier("i"));
        ExpressionValueEvaluator evaluator = evaluatorFor(expression);

        assertEquals(java.util.Optional.of(true), evaluator.evaluateAsBoolean(expression, Map.of("i", 1L), expression));
        assertEquals(java.util.Optional.of(false), evaluator.evaluateAsBoolean(expression, Map.of("i", 0L), expression));
    }

    @Test
    void arithmeticExpressionsGetExactLongEstimate() {
        SubOp expression = new SubOp(
                new AddOp(new IntegerLiteral(5), new IntegerLiteral(3)),
                new UnaryMinusOp(new IntegerLiteral(2))
        );
        ExpressionValueEvaluator evaluator = evaluatorFor(expression);

        OptionalLong result = evaluator.evaluateAsLong(expression, Map.of(), expression);
        ExpressionValueEstimate<?> estimate = expression.getValueEstimate().orElseThrow();

        assertTrue(result.isPresent());
        assertEquals(10L, result.getAsLong());
        assertEquals(java.util.Optional.of(10L), estimate.exactValue());
        assertEquals(Set.of(10L), estimate.possibleValues());
        assertTrue(estimate.reliable());
    }

    @Test
    void unaryPlusAndParenthesesAreUnwrappedForLongEstimation() {
        ParenthesizedExpression expression = new ParenthesizedExpression(
                new UnaryPlusOp(new IntegerLiteral(7))
        );

        ExpressionValueEstimate<?> estimate = evaluatorFor(expression).estimate(expression, expression);

        assertEquals(java.util.Optional.of(7L), estimate.exactValue());
        assertEquals(Set.of(7L), estimate.possibleValues());
        assertTrue(estimate.reliable());
    }

    @Test
    void booleanCompositionsGetExactEstimateWhenChildrenAreExact() {
        ShortCircuitAndOp expression = new ShortCircuitAndOp(
                new BoolLiteral(true),
                new NotOp(new BoolLiteral(false))
        );
        ExpressionValueEstimate<?> estimate = evaluatorFor(expression).estimate(expression, expression);

        assertEquals(java.util.Optional.of(true), estimate.exactValue());
        assertEquals(Set.of(Boolean.TRUE), estimate.possibleValues());
        assertTrue(estimate.reliable());
    }

    @Test
    void shortCircuitOrGetsExactEstimateWhenChildrenAreExact() {
        ShortCircuitOrOp expression = new ShortCircuitOrOp(
                new BoolLiteral(false),
                new BoolLiteral(true)
        );

        ExpressionValueEstimate<?> estimate = evaluatorFor(expression).estimate(expression, expression);

        assertEquals(java.util.Optional.of(true), estimate.exactValue());
        assertEquals(Set.of(Boolean.TRUE), estimate.possibleValues());
        assertTrue(estimate.reliable());
    }

    @Test
    void notWithUnknownOperandGetsPossibleBooleanEstimate() {
        NotOp expression = new NotOp(new GtOp(new SimpleIdentifier("a"), new IntegerLiteral(0)));

        ExpressionValueEstimate<?> estimate = evaluatorFor(expression).estimate(expression, expression);

        assertTrue(estimate.exactValue().isEmpty());
        assertEquals(Set.of(Boolean.TRUE, Boolean.FALSE), estimate.possibleValues());
        assertFalse(estimate.reliable());
    }

    @Test
    void identifierResolvesFromVisibleScope() {
        SimpleIdentifier expression = new SimpleIdentifier("x");
        VariableDeclaration declaration = new VariableDeclaration(new IntType(), new SimpleIdentifier("x"), new IntegerLiteral(42));
        CompoundStatement context = bindScopedContext(declaration, new ExpressionStatement(expression));

        ExpressionValueEstimate<?> estimate = evaluatorFor(context, expression).estimate(expression, context);

        assertEquals(java.util.Optional.of(42L), estimate.exactValue());
        assertEquals(Set.of(42L), estimate.possibleValues());
        assertTrue(estimate.reliable());
    }

    @Test
    void collectionSizeIsComputedForLiteralsAndVisibleIdentifiers() {
        ListLiteral listLiteral = new ListLiteral(new IntegerLiteral(1), new IntegerLiteral(2), new IntegerLiteral(3));
        DictionaryLiteral dictionaryLiteral = new DictionaryLiteral(new LinkedHashMap<>() {{
            put(new IntegerLiteral(1), new IntegerLiteral(10));
            put(new IntegerLiteral(2), new IntegerLiteral(20));
        }});
        StringLiteral stringLiteral = StringLiteral.fromUnescaped("test", StringLiteral.Type.NONE);
        SimpleIdentifier identifier = new SimpleIdentifier("items");
        VariableDeclaration declaration = new VariableDeclaration(new IntType(), new SimpleIdentifier("items"), listLiteral.clone());
        CompoundStatement context = bindScopedContext(declaration, new ExpressionStatement(identifier));
        ExpressionValueEvaluator evaluator = evaluatorFor(context, identifier);

        assertEquals(3L, evaluator.evaluateCollectionSize(listLiteral, listLiteral).orElseThrow());
        assertEquals(2L, evaluator.evaluateCollectionSize(dictionaryLiteral, dictionaryLiteral).orElseThrow());
        assertEquals(4L, evaluator.evaluateCollectionSize(stringLiteral, stringLiteral).orElseThrow());
        assertEquals(3L, evaluator.evaluateCollectionSize(identifier, context).orElseThrow());
    }

    @Test
    void envBasedEvaluationDoesNotPersistValueEstimateOnAstNode() {
        GtOp expression = new GtOp(new SimpleIdentifier("i"), new IntegerLiteral(0));
        ExpressionValueEvaluator evaluator = evaluatorFor(expression);

        assertEquals(java.util.Optional.of(true), evaluator.evaluateAsBoolean(expression, Map.of("i", 1L), expression));
        assertTrue(expression.getValueEstimate().isEmpty());
    }

    @Test
    void unsupportedExpressionRemainsUnknown() {
        SimpleIdentifier expression = new SimpleIdentifier("missing");

        ExpressionValueEstimate<?> estimate = evaluatorFor(expression).estimate(expression, expression);

        assertTrue(estimate.exactValue().isEmpty());
        assertTrue(estimate.possibleValues().isEmpty());
        assertFalse(estimate.reliable());
    }

    @Test
    void cppTranslatorPostProcessAssignsValueEstimateForSimpleCondition() {
        String code = """
                if (a > 0) {
                }
                """;

        MeaningTree mt = new CppTranslator(DEFAULT_CONFIG).getMeaningTree(code);

        GtOp comparison = StreamSupport.stream(mt.spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(GtOp.class::isInstance)
                .map(GtOp.class::cast)
                .findFirst()
                .orElseThrow();

        ExpressionValueEstimate<?> estimate = comparison.getValueEstimate().orElseThrow();
        assertTrue(estimate.exactValue().isEmpty());
        assertEquals(Set.of(Boolean.TRUE, Boolean.FALSE), estimate.possibleValues());
        assertFalse(estimate.reliable());
    }

    private static ExpressionValueEvaluator evaluatorFor(org.vstu.meaningtree.nodes.Expression expression) {
        return new ExpressionValueEvaluator(new MeaningTree(expression), new ScopeTable());
    }

    private static ExpressionValueEvaluator evaluatorFor(CompoundStatement context, org.vstu.meaningtree.nodes.Expression expression) {
        ScopeTable scopeTable = new ScopeTable();
        scopeTable.enter(context);
        for (var node : context.getNodeList()) {
            if (node instanceof VariableDeclaration declaration) {
                scopeTable.registerVariable(declaration);
            }
        }
        return new ExpressionValueEvaluator(new MeaningTree(new ProgramEntryPoint(java.util.List.of(context))), scopeTable);
    }

    private static CompoundStatement bindScopedContext(org.vstu.meaningtree.nodes.Node... nodes) {
        return new CompoundStatement(nodes);
    }
}
