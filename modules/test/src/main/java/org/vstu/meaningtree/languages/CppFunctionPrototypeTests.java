package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedConversionException;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.serializers.json.JsonDeserializer;
import org.vstu.meaningtree.serializers.json.JsonSerializer;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Прототип функции C/C++ — объявление без тела: {@code double f(double x);}.
 * <p>
 * До этого он не разбирался вовсе (разбор падал на {@code function_declarator}), поэтому здесь
 * проверяется вся дорога: разбор в {@link FunctionDeclaration}, печать в каждом целевом языке и
 * то, ради чего он в основном и нужен, — запись в таблице областей видимости, по которой видно,
 * что одноимённая функция объявлена самой программой, а не взята из стандартной библиотеки.
 */
class CppFunctionPrototypeTests {
    private static final Map<String, Object> CPP = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    private static final Map<String, Object> C_MODE = Map.of(
            "translationUnitMode", "full",
            "preferC", true,
            "skipErrors", false
    );

    @Test
    void prototypeIsParsedAsFunctionDeclaration() {
        MeaningTree tree = new CppTranslator(CPP).getMeaningTree("double f(double x);\nint main() { return 0; }");

        assertTrue(anyNode(tree, node -> node instanceof FunctionDeclaration declaration
                        && declaration.getName().toString().equals("f")),
                "прототип должен стать узлом объявления функции");
    }

    /** Прототип завершается точкой с запятой, а тот же узел внутри определения — нет. */
    @Test
    void prototypeKeepsItsSemicolonAndDefinitionStaysWhole() {
        String generated = cpp("""
                double f(double x);
                int main() { return 0; }
                double f(double x) { return x; }
                """);

        assertTrue(generated.contains("double f(double x);"), generated);
        assertTrue(generated.contains("double f(double x)\n{"), generated);
    }

    @Test
    void modifiersAndPointerReturnTypeSurvive() {
        assertTrue(cpp("static double f(double a, double b);\nint main() { return 0; }")
                .contains("static double f(double a, double b);"));
        assertTrue(cpp("int *g(int x);\nint main() { return 0; }")
                .contains("int * g(int x);"));
    }

    /**
     * {@code (void)} — Си-запись пустого списка параметров, а не параметр типа {@code void}.
     * Так пишет и сам вьюер в C-режиме, поэтому без этого собственный вывод не разбирался
     * обратно: {@code int main(void)} падал на разборе параметра без имени.
     */
    @Test
    void voidParameterListMeansNoParametersAtAll() {
        assertTrue(cpp("int f(void);\nint main() { return 0; }").contains("int f();"));

        String cCode = translate("int main() { return 0; }", C_MODE);
        assertTrue(cCode.contains("int main(void)"), cCode);
        assertEquals(cCode, translate(cCode, C_MODE), "C-вывод должен разбираться обратно в себя");
    }

    /**
     * Имя параметра в прототипе необязательно — обращаться к нему негде, поэтому и в модели оно
     * необязательно ({@code DeclarationArgument.hasName}). Печатается один тип, ровно как в
     * исходнике: выдуманное имя было бы тем, чего в исходном коде не было.
     */
    @Test
    void unnamedParameterSurvivesAsTypeAlone() {
        assertTrue(cpp("double f(double);\nint main() { return 0; }").contains("double f(double);"));
        assertTrue(cpp("int f(const char *, int);\nint main() { return 0; }")
                .contains("int f(const char *, int);"));
        assertTrue(cpp("int f(int&);\nint main() { return 0; }").contains("int f(int &);"));
    }

    /**
     * Безымянный массив и указатель на функцию в параметре не поддерживаются: у первого нужен
     * размер, у второго — сигнатура, и выдать их за что-то другое молча нельзя.
     */
    @Test
    void unnamedParameterOfAnUnsupportedFormIsRefused() {
        UnsupportedParsingException error = assertThrows(UnsupportedParsingException.class,
                () -> new CppTranslator(CPP).getMeaningTree("int f(int[]);\nint main() { return 0; }"));

        assertTrue(error.getMessage().contains("Unnamed parameter"), error.getMessage());
    }

    /**
     * В Java и Python функция объявляется вместе с телом, безымянного параметра там не бывает:
     * перевод отказывается, а не подставляет выдуманное имя.
     */
    @Test
    void javaAndPythonRefuseAnUnnamedParameter() {
        String source = "double f(double);\nint main() { return 0; }";

        for (LanguageTranslator target : List.of(new JavaTranslator(CPP), new PythonTranslator(CPP))) {
            UnsupportedConversionException error = assertThrows(UnsupportedConversionException.class,
                    () -> translate(target, source),
                    () -> "перевод в " + target.getLanguageName() + " должен отказать");
            assertTrue(error.getMessage().contains("feature-unnamed-parameter"), error.getMessage());
        }
    }

    /** Безымянный параметр переживает сериализацию: имя в JSON пустое, а не выдуманное. */
    @Test
    void unnamedParameterSurvivesJsonRoundTrip() {
        MeaningTree tree = new CppTranslator(CPP).getMeaningTree("double f(double);\nint main() { return 0; }");

        MeaningTree restored = new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree));

        assertTrue(new CppTranslator(CPP).getCode(restored).contains("double f(double);"));
    }

    /** Прототип вперемешку с переменными — законный Си, но узла под несколько объявлений нет. */
    @Test
    void declarationMixingAPrototypeWithVariablesIsRefused() {
        UnsupportedParsingException error = assertThrows(UnsupportedParsingException.class,
                () -> new CppTranslator(CPP).getMeaningTree("int a, f(int x);\nint main() { return 0; }"));

        assertTrue(error.getMessage().contains("prototype"), error.getMessage());
    }

    /**
     * {@code int (*f)(int x);} — переменная-указатель на функцию, а не прототип: имя стоит не в
     * самом {@code function_declarator}, а в скобках внутри него. Поддержки таких переменных нет
     * и не появилось, но прототипом это объявление считаться не должно.
     */
    @Test
    void functionPointerVariableIsNotMistakenForAPrototype() {
        UnsupportedParsingException error = assertThrows(UnsupportedParsingException.class,
                () -> new CppTranslator(CPP).getMeaningTree("int (*f)(int x);\nint main() { return 0; }"));

        assertFalse(error.getMessage().contains("prototype"), error.getMessage());
    }

    /**
     * То, ради чего прототип и нужен вьюеру: объявленная программой функция не считается
     * библиотечной, и заголовок под неё не подключается, даже когда имя совпало с именем из
     * стандартной библиотеки.
     */
    @Test
    void prototypeMakesTheFunctionKnownToTheScopeTable() {
        String own = cpp("""
                double sqrt(double x);
                int main() { return (int) sqrt(4.0); }
                double sqrt(double x) { return x; }
                """);
        assertFalse(own.contains("#include"), own);

        String library = cpp("int main() { return (int) sqrt(4.0); }");
        assertTrue(library.contains("#include <cmath>"), library);
    }

    /**
     * В Java предварительных объявлений нет: метод объявляется вместе с телом, а сигнатура без
     * тела не компилируется. Прототип исчезает — всё, что он объявлял, несёт определение.
     */
    @Test
    void javaDropsThePrototypeAndKeepsTheDefinition() {
        String java = translate(new JavaTranslator(CPP), """
                double f(double x);
                int main() { return 0; }
                double f(double x) { return x; }
                """);

        assertFalse(java.contains("public static double f(double x)\n"), java);
        assertTrue(java.contains("public static double f(double x) {"), java);
    }

    /** В Python объявление без тела выражается функцией-заглушкой: своего прототипа там нет. */
    @Test
    void pythonRendersThePrototypeAsAStub() {
        String python = translate(new PythonTranslator(CPP), """
                double f(double x);
                int main() { return 0; }
                double f(double x) { return x; }
                """);

        assertTrue(python.contains("def f(x: float) -> float:"), python);
        assertTrue(python.contains("pass"), python);
    }

    private boolean anyNode(MeaningTree tree, java.util.function.Predicate<Node> predicate) {
        for (var info : tree) {
            if (predicate.test(info.node())) {
                return true;
            }
        }
        return false;
    }

    private String cpp(String source) {
        return translate(source, CPP);
    }

    private String translate(String source, Map<String, Object> config) {
        CppTranslator translator = new CppTranslator(config);
        return translator.getCode(translator.getMeaningTree(source));
    }

    private String translate(LanguageTranslator target, String source) {
        return target.getCode(new CppTranslator(CPP).getMeaningTree(source));
    }
}
