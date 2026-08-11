package org.vstu.meaningtree.rendering;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.BitwiseAndOp;
import org.vstu.meaningtree.nodes.expressions.bitwise.InversionOp;
import org.vstu.meaningtree.nodes.expressions.bitwise.LeftShiftOp;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.EqOp;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.DivOp;
import org.vstu.meaningtree.nodes.expressions.math.MulOp;
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryMinusOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryPlusOp;
import org.vstu.meaningtree.nodes.types.builtin.IntType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Проверяет расстановку скобок при рендеринге — {@link org.vstu.meaningtree.utils.ParenthesesFiller},
 * подключённый к viewer'ам как {@code registerPreRenderPreparation}.
 * <p>
 * Тесты идут по деревьям, собранным программно, а не по разбору исходника: в исходнике
 * скобки уже расставлены и парсер сохраняет их как {@link ParenthesizedExpression}, поэтому
 * конверсионный прогон этот код почти не задевает. Именно поэтому он не поймал ни промах по
 * ключу оператора в Java (унарные плюс и минус не получали токен и не расставляли скобки),
 * ни склейку {@code -(-a)} в {@code --a}.
 * <p>
 * Ошибка здесь тихая: код компилируется и выглядит правдоподобно, но означает другое —
 * поэтому проверка идёт по точному тексту вывода.
 */
public class ParenthesesFillerTests {

    /**
     * Ожидаемый вывод по языкам. {@code null} означает «этот случай для языка не проверяем».
     */
    private record Case(String name, Supplier<Expression> tree, String java, String cpp, String python) {}

    private static SimpleIdentifier a() { return new SimpleIdentifier("a"); }
    private static SimpleIdentifier b() { return new SimpleIdentifier("b"); }
    private static SimpleIdentifier c() { return new SimpleIdentifier("c"); }

    private static final List<Case> CASES = List.of(
            // --- приоритет: операнд ниже приоритетом, чем оператор ---
            new Case("(a + b) * c",
                    () -> new MulOp(new AddOp(a(), b()), c()),
                    "(a + b) * c", "(a + b) * c", "(a + b) * c"),
            new Case("a * (b + c)",
                    () -> new MulOp(a(), new AddOp(b(), c())),
                    "a * (b + c)", "a * (b + c)", "a * (b + c)"),
            // --- приоритет: операнд выше приоритетом, скобки не нужны ---
            new Case("a * b + c",
                    () -> new AddOp(new MulOp(a(), b()), c()),
                    "a * b + c", "a * b + c", "a * b + c"),
            new Case("a + b == c",
                    () -> new EqOp(new AddOp(a(), b()), c()),
                    "a + b == c", "a + b == c", "a + b == c"),

            // --- равный приоритет, левая ассоциативность ---
            // правый операнд требует скобок, левый — нет
            new Case("a - (b - c)",
                    () -> new SubOp(a(), new SubOp(b(), c())),
                    "a - (b - c)", "a - (b - c)", "a - (b - c)"),
            new Case("a - b - c",
                    () -> new SubOp(new SubOp(a(), b()), c()),
                    "a - b - c", "a - b - c", "a - b - c"),
            new Case("a / (b * c)",
                    () -> new DivOp(a(), new MulOp(b(), c())),
                    "a / (b * c)", "a / (b * c)", "a / (b * c)"),

            // --- сдвиг ниже приоритетом сложения ---
            new Case("a + b << c",
                    () -> new LeftShiftOp(new AddOp(a(), b()), c()),
                    "a + b << c", "a + b << c", "a + b << c"),
            new Case("(a << b) + c",
                    () -> new AddOp(new LeftShiftOp(a(), b()), c()),
                    "(a << b) + c", "(a << b) + c", "(a << b) + c"),

            // --- логика ---
            new Case("!(a || b)",
                    () -> new NotOp(new ShortCircuitOrOp(a(), b())),
                    "!(a || b)", "!(a || b)", "not (a or b)"),
            new Case("(a || b) && c",
                    () -> new ShortCircuitAndOp(new ShortCircuitOrOp(a(), b()), c()),
                    "(a || b) && c", "(a || b) && c", "(a or b) and c"),
            new Case("a && b || c",
                    () -> new ShortCircuitOrOp(new ShortCircuitAndOp(a(), b()), c()),
                    "a && b || c", "a && b || c", "a and b or c"),
            // в Java и C++ побитовое И ниже приоритетом равенства, в Python — выше
            new Case("a == b & c",
                    () -> new BitwiseAndOp(new EqOp(a(), b()), c()),
                    "a == b & c", "a == b & c", "(a == b) & c"),

            // --- унарный оператор над бинарным аргументом ---
            new Case("-(a + b)",
                    () -> new UnaryMinusOp(new AddOp(a(), b())),
                    "-(a + b)", "-(a + b)", "-(a + b)"),
            new Case("+(a + b)",
                    () -> new UnaryPlusOp(new AddOp(a(), b())),
                    "+(a + b)", "+(a + b)", "+(a + b)"),
            // --- бинарный над унарным: скобки не нужны ---
            new Case("-a * b",
                    () -> new MulOp(new UnaryMinusOp(a()), b()),
                    "-a * b", "-a * b", "-a * b"),

            // --- унарный над унарным: скобки нужны только там, где знаки склеятся ---
            // В Java и C++ `--` — префиксный декремент, поэтому `--a` означало бы другое.
            // В Python операторов `--` и `++` нет, склейки не возникает и `--a` читается верно.
            new Case("-(-a): в Java и C++ склеилось бы в декремент",
                    () -> new UnaryMinusOp(new UnaryMinusOp(a())),
                    "-(-a)", "-(-a)", "--a"),
            new Case("+(+a): в Java и C++ склеилось бы в инкремент",
                    () -> new UnaryPlusOp(new UnaryPlusOp(a())),
                    "+(+a)", "+(+a)", "++a"),
            new Case("!!a склейки не образует",
                    () -> new NotOp(new NotOp(a())),
                    "!!a", "!!a", "not not a"),
            new Case("~~a склейки не образует",
                    () -> new InversionOp(new InversionOp(a())),
                    "~~a", "~~a", "~~a"),
            new Case("-+a склейки не образует",
                    () -> new UnaryMinusOp(new UnaryPlusOp(a())),
                    "-+a", "-+a", "-+a"),

            // --- тернарный оператор ---
            new Case("тернарный с бинарным условием",
                    () -> new TernaryOperator(new AddOp(a(), b()), a(), b()),
                    "a + b ? a : b", "a + b ? a : b", "a if a + b else b"),
            new Case("тернарный внутри бинарного",
                    () -> new AddOp(new TernaryOperator(a(), b(), c()), a()),
                    "(a ? b : c) + a", "(a ? b : c) + a", "(b if a else c) + a"),

            // --- по одной ветке process() на каждый оставшийся вид узла ---
            new Case("IndexExpression",
                    () -> new IndexExpression(new AddOp(a(), b()), c()),
                    "(a + b)[c]", "(a + b)[c]", "(a + b)[c]"),
            new Case("MemberAccess",
                    () -> new MemberAccess(new AddOp(a(), b()), new SimpleIdentifier("m")),
                    "(a + b).m", "(a + b).m", "(a + b).m"),
            new Case("CastTypeExpression",
                    () -> new CastTypeExpression(new IntType(), new AddOp(a(), b())),
                    "(int) (a + b)", "(int) (a + b)", "int(a + b)"),
            // C++ не проверяем: CppViewer теряет объект вызова, дефект не в расстановке скобок
            new Case("MethodCall",
                    () -> new MethodCall(new AddOp(a(), b()), new SimpleIdentifier("f")),
                    "(a + b).f()", null, "(a + b).f()"),
            new Case("QualifiedIdentifier",
                    () -> new QualifiedIdentifier(a(), b()),
                    "a::b", "a::b", "a.b"),
            new Case("AssignmentExpression",
                    () -> new AssignmentExpression(a(), new AddOp(b(), c())),
                    "a = b + c", "a = b + c", "a := b + c"),

            // --- идемпотентность: уже расставленные скобки не удваиваются ---
            new Case("(a + b) * c из готового ParenthesizedExpression",
                    () -> new MulOp(new ParenthesizedExpression(new AddOp(a(), b())), c()),
                    "(a + b) * c", "(a + b) * c", "(a + b) * c"),
            new Case("-(a + b) из готового ParenthesizedExpression",
                    () -> new UnaryMinusOp(new ParenthesizedExpression(new AddOp(a(), b()))),
                    "-(a + b)", "-(a + b)", "-(a + b)"),

            // --- операнд, который не является оператором, остаётся нетронутым ---
            new Case("литерал как операнд",
                    () -> new AddOp(new IntegerLiteral(1), b()),
                    "1L + b", "1L + b", "1 + b")
    );

    @Test
    void javaPlacesParenthesesCorrectly() {
        assertMatrix(new JavaTranslator(), Case::java);
    }

    @Test
    void cppPlacesParenthesesCorrectly() {
        assertMatrix(new CppTranslator(), Case::cpp);
    }

    @Test
    void pythonPlacesParenthesesCorrectly() {
        assertMatrix(new PythonTranslator(), Case::python);
    }

    private void assertMatrix(LanguageTranslator translator, java.util.function.Function<Case, String> expected) {
        List<String> mismatches = new ArrayList<>();
        for (Case testCase : CASES) {
            String want = expected.apply(testCase);
            if (want == null) {
                continue;
            }
            String got;
            try {
                got = translator.getCode(new MeaningTree(testCase.tree().get())).trim();
            } catch (Exception e) {
                got = "<%s: %s>".formatted(e.getClass().getSimpleName(), e.getMessage());
            }
            if (!want.equals(got)) {
                mismatches.add("  %s%n    ожидалось: %s%n    получено:  %s".formatted(testCase.name(), want, got));
            }
        }
        if (!mismatches.isEmpty()) {
            fail("Расстановка скобок разошлась с ожидаемой в %d случаях из %d:%n%s".formatted(
                    mismatches.size(), CASES.size(), String.join(System.lineSeparator(), mismatches)));
        }
    }
}
