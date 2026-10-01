package org.vstu.meaningtree.analysis;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.comparison.EqOp;
import org.vstu.meaningtree.nodes.expressions.comparison.GeOp;
import org.vstu.meaningtree.nodes.expressions.comparison.GtOp;
import org.vstu.meaningtree.nodes.expressions.comparison.LeOp;
import org.vstu.meaningtree.nodes.expressions.comparison.NotEqOp;
import org.vstu.meaningtree.nodes.expressions.comparison.LtOp;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.MulOp;
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

    @Test
    void pythonTranslatorCanInferVariableTypeFromVisibleIdentifierWithoutDuplicatingTypeNodes() {
        String code = """
                if flag:
                    value = 1
                result = value
                """;

        MeaningTree mt = new PythonTranslator(DEFAULT_CONFIG).getMeaningTree(code);

        assertDoesNotThrow(mt::makeIndex);
    }

    /* -----------------------------------------------------------
    |  Свёртка констант: что считается точно, а что остаётся неизвестным  |
    ------------------------------------------------------------ */

    private static IntegerLiteral lit(long value) {
        return new IntegerLiteral(value);
    }

    private static IntegerLiteral unsignedLit(long value) {
        // Суффикс, а не флаг: конструктор со флагами перечитывает их из текста
        return new IntegerLiteral(value + "u");
    }

    private static Expression unknownCondition() {
        return new GtOp(new SimpleIdentifier("a"), lit(0));
    }

    private static void assertExactBoolean(boolean expected, Expression expression) {
        ExpressionValueEvaluator evaluator = evaluatorFor(expression);
        assertEquals(java.util.Optional.of(expected), evaluator.evaluateAsBoolean(expression, Map.of(), expression),
                expression.getClass().getSimpleName());
        ExpressionValueEstimate<?> estimate = expression.getValueEstimate().orElseThrow();
        assertEquals(java.util.Optional.of(expected), estimate.exactValue());
        assertTrue(estimate.reliable());
    }

    private static void assertNotExactBoolean(Expression expression) {
        assertTrue(evaluatorFor(expression).evaluateAsBoolean(expression, Map.of(), expression).isEmpty(),
                expression.getClass().getSimpleName());
    }

    private static void assertExactLong(long expected, Expression expression) {
        OptionalLong value = evaluatorFor(expression).evaluateAsLong(expression, Map.of(), expression);
        assertTrue(value.isPresent(), "must be computed: " + expression.getClass().getSimpleName());
        assertEquals(expected, value.getAsLong());
    }

    private static void assertUnknownLong(Expression expression) {
        assertTrue(evaluatorFor(expression).evaluateAsLong(expression, Map.of(), expression).isEmpty(),
                "must stay unknown: " + expression.getClass().getSimpleName());
    }

    @Test
    void comparisonOfTwoConstantsIsExact() {
        assertExactBoolean(true, new LtOp(lit(1), lit(2)));
        assertExactBoolean(false, new LtOp(lit(2), lit(1)));
        assertExactBoolean(true, new LeOp(lit(2), lit(2)));
        assertExactBoolean(false, new GtOp(lit(2), lit(2)));
        assertExactBoolean(true, new GeOp(lit(2), lit(2)));
        assertExactBoolean(true, new EqOp(lit(3), lit(3)));
        assertExactBoolean(false, new EqOp(lit(3), lit(4)));
        assertExactBoolean(true, new NotEqOp(lit(3), lit(4)));
        assertExactBoolean(false, new NotEqOp(lit(3), lit(3)));
        assertExactBoolean(true, new GtOp(lit(0), new UnaryMinusOp(lit(1))));
    }

    @Test
    void comparisonOfComputedValuesIsExact() {
        // (1 + 2) * 3 == 9
        assertExactBoolean(true, new EqOp(new MulOp(new AddOp(lit(1), lit(2)), lit(3)), lit(9)));
        // 2 * 3 < 2 + 3
        assertExactBoolean(false, new LtOp(new MulOp(lit(2), lit(3)), new AddOp(lit(2), lit(3))));
        assertExactBoolean(false, new NotOp(new LtOp(lit(1), lit(2))));
    }

    @Test
    void comparisonWithAnUnknownSideStaysUnknown() {
        assertNotExactBoolean(new LtOp(new SimpleIdentifier("a"), lit(2)));
        assertNotExactBoolean(new EqOp(lit(1), new SimpleIdentifier("a")));
        assertNotExactBoolean(new NotEqOp(new SimpleIdentifier("a"), new SimpleIdentifier("b")));
    }

    @Test
    void equalityOfTwoKnownBooleansIsExact() {
        assertExactBoolean(true, new EqOp(new BoolLiteral(true), new NotOp(new BoolLiteral(false))));
        assertExactBoolean(false, new EqOp(new BoolLiteral(true), new BoolLiteral(false)));
        assertExactBoolean(true, new NotEqOp(new BoolLiteral(true), new BoolLiteral(false)));
    }

    @Test
    void multiplicationOfIntegersIsExact() {
        assertExactLong(6, new MulOp(lit(2), lit(3)));
        assertExactLong(-6, new MulOp(new UnaryMinusOp(lit(2)), lit(3)));
        assertExactLong(0, new MulOp(lit(0), lit(7)));
        assertExactLong(2_147_395_600L, new MulOp(lit(46340), lit(46340)));
        assertUnknownLong(new MulOp(lit(2), new SimpleIdentifier("a")));
    }

    @Test
    void oneDecidingSideFixesAShortCircuitResult() {
        assertExactBoolean(false, new ShortCircuitAndOp(new BoolLiteral(false), unknownCondition()));
        assertExactBoolean(false, new ShortCircuitAndOp(unknownCondition(), new BoolLiteral(false)));
        assertExactBoolean(true, new ShortCircuitOrOp(new BoolLiteral(true), unknownCondition()));
        assertExactBoolean(true, new ShortCircuitOrOp(unknownCondition(), new BoolLiteral(true)));
    }

    @Test
    void nonDecidingSideLeavesAShortCircuitResultUnknown() {
        assertNotExactBoolean(new ShortCircuitAndOp(new BoolLiteral(true), unknownCondition()));
        assertNotExactBoolean(new ShortCircuitAndOp(unknownCondition(), new BoolLiteral(true)));
        assertNotExactBoolean(new ShortCircuitOrOp(new BoolLiteral(false), unknownCondition()));
        assertNotExactBoolean(new ShortCircuitOrOp(unknownCondition(), new BoolLiteral(false)));
        assertNotExactBoolean(new ShortCircuitAndOp(unknownCondition(), unknownCondition()));
    }

    @Test
    void arithmeticBeyondTheIntRangeIsNotTrusted() {
        // Вычисление идёт в long, а у выражения в C++ или Java тип уже: это переполнение, а не число
        assertUnknownLong(new AddOp(lit(Integer.MAX_VALUE), lit(1)));
        assertUnknownLong(new SubOp(lit(Integer.MIN_VALUE), lit(1)));
        assertUnknownLong(new MulOp(lit(46341), lit(46341)));
        assertUnknownLong(new MulOp(lit(65536), lit(65536)));
        assertUnknownLong(new AddOp(lit(Integer.MAX_VALUE), lit(Integer.MAX_VALUE)));
        assertExactLong(Integer.MAX_VALUE, new AddOp(lit(Integer.MAX_VALUE - 1), lit(1)));
        // Промежуточное переполнение тоже ломает результат: (MAX + 1) - 1 в программе не равно MAX
        assertUnknownLong(new SubOp(new AddOp(lit(Integer.MAX_VALUE), lit(1)), lit(1)));
    }

    @Test
    void aLoneLongLiteralKeepsItsValue() {
        assertExactLong(3_000_000_000L, new IntegerLiteral("3000000000l"));
    }

    @Test
    void unsignedArithmeticMustNotWrap() {
        assertExactLong(2, new SubOp(unsignedLit(5), unsignedLit(3)));
        assertUnknownLong(new SubOp(unsignedLit(3), unsignedLit(5)));
        assertUnknownLong(new UnaryMinusOp(unsignedLit(1)));
        assertExactLong(4_000_000_000L, new AddOp(unsignedLit(3_000_000_000L), unsignedLit(1_000_000_000L)));
        assertUnknownLong(new AddOp(unsignedLit(4_000_000_000L), unsignedLit(1_000_000_000L)));
    }

    @Test
    void comparingASignedNegativeWithAnUnsignedIsNotTrusted() {
        // В C++ -1 < 0u ложно: -1 превращается в большое беззнаковое
        assertNotExactBoolean(new LtOp(new UnaryMinusOp(lit(1)), unsignedLit(0)));
        assertExactBoolean(true, new LtOp(lit(1), unsignedLit(2)));
        assertExactBoolean(true, new LtOp(new UnaryMinusOp(lit(1)), lit(0)));
    }

    @Test
    void constantVariableMakesAComparisonExact() {
        assertEquals(java.util.Optional.of(true), conditionOf("const int N = 4; if (N > 3) { }"));
        assertEquals(java.util.Optional.of(false), conditionOf("int s = 3; if (s - 5 > 0) { }"));
        assertEquals(java.util.Optional.of(true), conditionOf("int a = 2; int b = 3; if (a * b == 6 && b > a) { }"));
    }

    @Test
    void variableThatIsWrittenKeepsAComparisonUnknown() {
        assertEquals(java.util.Optional.empty(), conditionOf("int n = 4; n++; if (n > 3) { }"));
        assertEquals(java.util.Optional.empty(), conditionOf("bool c = true; int n = 4; if (c) { n = 5; } if (n > 3) { }"));
        // Условие меняет само себя: значение у него не одно
        assertEquals(java.util.Optional.empty(), conditionOf("int n = 4; if (n-- > 3) { }"));
    }

    @Test
    void nearestAssignmentMakesAComparisonExact() {
        assertEquals(java.util.Optional.of(true), conditionOf("int n = 4; n = 5; if (n > 4) { }"));
        assertEquals(java.util.Optional.of(false), conditionOf("int n = 4; n = 1; if (n > 3) { }"));
    }

    @Test
    void unsignedVariableNeverWrapsIntoANegativeResult() {
        assertEquals(java.util.Optional.empty(), conditionOf("unsigned u = 3; if (u - 5 > 0) { }"));
        assertEquals(java.util.Optional.of(true), conditionOf("unsigned u = 5; if (u - 3 == 2) { }"));
        assertEquals(java.util.Optional.empty(), conditionOf("size_t n = 3; if (n - 5 < 0) { }"));
    }

    @Test
    void initializerThatMentionsItsOwnVariableDoesNotRecurse() {
        assertEquals(java.util.Optional.empty(), conditionOf("int x = x + 1; if (x > 0) { }"));
        // В Python первое присваивание параметру объявляет новую переменную, и она же стоит справа
        assertDoesNotThrow(() -> new PythonTranslator(Map.of("translationUnitMode", "full", "skipErrors", false))
                .getMeaningTree("def bad(n: int):\n    n = n + 1\n    if n > 0:\n        pass\n"));
    }

    /** Точное значение условия первого {@code if} в {@code main}, если оно известно. */
    private static java.util.Optional<Boolean> conditionOf(String body) {
        MeaningTree tree = new CppTranslator(Map.of("translationUnitMode", "full", "skipErrors", false))
                .getMeaningTree("#include <cstddef>\nint main() {\n" + body + "\nreturn 0;\n}\n");
        Expression condition = StreamSupport.stream(tree.spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(org.vstu.meaningtree.nodes.statements.conditions.IfStatement.class::isInstance)
                .map(org.vstu.meaningtree.nodes.statements.conditions.IfStatement.class::cast)
                .findFirst()
                .orElseThrow()
                .getBranches().get(0).getCondition();
        return condition.getValueEstimate().flatMap(estimate -> estimate.exactValue()).map(Boolean.class::cast);
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
