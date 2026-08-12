package org.vstu.meaningtree;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TranslationUnitModeTests {
    private static final Map<String, Object> EXPRESSION_CONFIG = Map.of(
            "translationUnitMode", "expression",
            "skipErrors", false
    );
    private static final Map<String, Object> SIMPLE_CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );
    private static final Map<String, Object> PROCEDURAL_CONFIG = Map.of(
            "translationUnitMode", "procedural",
            "skipErrors", false
    );
    private static final Map<String, Object> FULL_CONFIG = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    /** Программа с вспомогательной функцией и точкой входа — одна и та же логика на трёх языках. */
    private static final String JAVA_PROGRAM = """
            class Main {
                static int helper(int x) {
                    return x;
                }

                public static void main(String[] args) {
                    int y = helper(1);
                }
            }
            """;
    private static final String PYTHON_PROGRAM = """
            def helper(x):
                return x

            def run():
                y = helper(1)

            if __name__ == "__main__":
                run()
            """;
    private static final String CPP_PROGRAM = """
            int helper(int x) {
                return x;
            }

            int main() {
                int y = helper(1);
                return 0;
            }
            """;
    /** Тот же код, но без точки входа: режимы обязаны как-то её достроить или сохранить как есть. */
    private static final String JAVA_WITHOUT_ENTRY_POINT = """
            class Box {
                int get() {
                    return 1;
                }
            }
            """;
    private static final String CPP_WITHOUT_ENTRY_POINT = """
            int helper(int x) {
                return x;
            }
            """;

    @Test
    void cppProceduralKeepsTopLevelFunctions() {
        String code = """
                int helper(int x) { return x; }
                int main() { return helper(1); }
                """;

        ProgramEntryPoint entryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                new CppTranslator(PROCEDURAL_CONFIG).getMeaningTree(code).getRootNode()
        );

        assertEquals(2, entryPoint.getBody().size());
        assertInstanceOf(FunctionDefinition.class, entryPoint.getBody().get(0));
        assertInstanceOf(FunctionDefinition.class, entryPoint.getBody().get(1));
    }

    @Test
    void javaProceduralHoistsStaticMembersFromEntryPointClass() {
        String code = """
                class Main {
                    static int helper(int x) { return x; }
                    static int value = 1;
                    public static void main(String[] args) { helper(value); }
                }
                """;

        JavaTranslator translator = new JavaTranslator(PROCEDURAL_CONFIG);
        ProgramEntryPoint entryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                translator.getMeaningTree(code).getRootNode()
        );

        assertEquals(3, entryPoint.getBody().size());
        assertInstanceOf(FunctionDefinition.class, entryPoint.getBody().get(0));
        assertTrue(entryPoint.getBody().get(1) instanceof org.vstu.meaningtree.nodes.declarations.FieldDeclaration);
        assertInstanceOf(FunctionDefinition.class, entryPoint.getBody().get(2));

        String generated = translator.getCode(new MeaningTree(entryPoint));
        assertFalse(generated.contains("class Main"));
        assertTrue(generated.contains("public static int helper"));
        assertTrue(generated.contains("static int value = 1;"));
        assertTrue(generated.contains("public static void main(String[] args)"));
    }

    @Test
    void javaProceduralReducesSingleMainClassToSimpleBody() {
        String code = """
                class Main {
                    public static void main(String[] args) {
                        int x = 1;
                        x++;
                    }
                }
                """;

        JavaTranslator translator = new JavaTranslator(PROCEDURAL_CONFIG);
        ProgramEntryPoint entryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                translator.getMeaningTree(code).getRootNode()
        );

        assertEquals(2, entryPoint.getBody().size());
        assertInstanceOf(VariableDeclaration.class, entryPoint.getBody().get(0));

        String generated = translator.getCode(new MeaningTree(entryPoint));
        assertFalse(generated.contains("class Main"));
        assertFalse(generated.contains("main("));
        assertTrue(generated.contains("int x = 1;"));
        assertTrue(generated.contains("x++;"));
    }

    @Test
    void javaProceduralKeepsResidualInstanceLogicInClass() {
        String code = """
                class Main {
                    int value() { return 1; }
                    public static void main(String[] args) { }
                }
                """;

        JavaTranslator translator = new JavaTranslator(PROCEDURAL_CONFIG);
        ProgramEntryPoint entryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                translator.getMeaningTree(code).getRootNode()
        );

        assertEquals(2, entryPoint.getBody().size());
        assertInstanceOf(ClassDefinition.class, entryPoint.getBody().get(1));

        String generated = translator.getCode(new MeaningTree(entryPoint));
        assertTrue(generated.contains("class Main"));
        assertTrue(generated.contains("int value()"));
        assertTrue(generated.contains("public static void main(String[] args)"));
    }

    @Test
    void javaProceduralHoistsStaticMethodsContainingIfWithoutElse() {
        String code = """
                class Main {
                    static int fact(int n) {
                        if (n <= 1) {
                            return 1;
                        }
                        return n * fact(n - 1);
                    }

                    public static void main(String[] args) {
                        int x = fact(3);
                    }
                }
                """;

        JavaTranslator translator = new JavaTranslator(PROCEDURAL_CONFIG);
        ProgramEntryPoint entryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                translator.getMeaningTree(code).getRootNode()
        );

        assertEquals(2, entryPoint.getBody().size());
        assertInstanceOf(FunctionDefinition.class, entryPoint.getBody().get(0));
        assertInstanceOf(FunctionDefinition.class, entryPoint.getBody().get(1));

        String generated = translator.getCode(new MeaningTree(entryPoint));
        assertTrue(generated.contains("if (n <= 1)"));
        assertTrue(generated.contains("return n * fact(n - 1);"));
        assertTrue(generated.contains("int x = fact(3);"));
    }

    @Test
    void javaViewerProceduralTransformsClassBasedEntryPoint() {
        String code = """
                class Main {
                    static int helper(int x) { return x; }
                    public static void main(String[] args) { helper(1); }
                }
                """;

        ProgramEntryPoint classBasedEntryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                new JavaTranslator(FULL_CONFIG).getMeaningTree(code).getRootNode()
        );

        String generated = new JavaTranslator(PROCEDURAL_CONFIG).getCode(new MeaningTree(classBasedEntryPoint));
        assertFalse(generated.contains("class Main"));
        assertTrue(generated.contains("public static int helper"));
        assertTrue(generated.contains("public static void main(String[] args)"));
    }

    @Test
    void pythonProceduralOmitsNameMainWrapperAndDoesNotEmitSyntheticCall() {
        String code = """
                from funcs import func
                a = 1

                def run():
                    func(a)
                    return 0

                if __name__ == "__main__":
                    run()
                """;

        String generated = new PythonTranslator(PROCEDURAL_CONFIG).getCode(
                new PythonTranslator(PROCEDURAL_CONFIG).getMeaningTree(code)
        );

        assertTrue(generated.contains("def run():"));
        assertFalse(generated.contains("if __name__"));
        assertFalse(generated.contains("\nrun()\n"));
    }

    @Test
    void pythonProceduralFlattensEntryPointFunctionWithoutReturns() {
        String code = """
                value = 1

                def run():
                    print(value)

                if __name__ == "__main__":
                    run()
                """;

        String generated = new PythonTranslator(PROCEDURAL_CONFIG).getCode(
                new PythonTranslator(PROCEDURAL_CONFIG).getMeaningTree(code)
        );

        assertFalse(generated.contains("if __name__"));
        assertFalse(generated.contains("def run():"));
        assertTrue(generated.contains("print(value)"));
    }

    @Test
    void pythonFullKeepsNameMainWrapper() {
        String code = """
                def run():
                    return 0

                if __name__ == "__main__":
                    run()
                """;

        String generated = new PythonTranslator(FULL_CONFIG).getCode(
                new PythonTranslator(FULL_CONFIG).getMeaningTree(code)
        );

        assertTrue(generated.contains("def run():"));
        assertTrue(generated.contains("if __name__ == \"__main__\":"));
        assertTrue(generated.contains("run()"));
    }

    /* -----------------------------
    |     Режим expression          |
    ------------------------------ */

    @Test
    void expressionModeParsesBareExpressionInEveryLanguage() {
        // Транслятор сам оборачивает выражение в заглушку точки входа и разворачивает результат
        for (LanguageTranslator translator : List.of(
                new JavaTranslator(EXPRESSION_CONFIG),
                new PythonTranslator(EXPRESSION_CONFIG),
                new CppTranslator(EXPRESSION_CONFIG))) {
            MeaningTree tree = translator.getMeaningTree("1 + 2 * 3");

            assertInstanceOf(AddOp.class, tree.getRootNode(),
                    "In expression mode the root must be the expression itself (" + translator.getLanguageName() + ")");
            assertEquals("1 + 2 * 3", translator.getCode(tree).strip(),
                    "Wrong expression generation for " + translator.getLanguageName());
        }
    }

    @Test
    void expressionModeIsReportedByTranslator() {
        assertTrue(new JavaTranslator(EXPRESSION_CONFIG).isExpressionMode());
        assertTrue(new PythonTranslator(EXPRESSION_CONFIG).isExpressionMode());
        assertTrue(new CppTranslator(EXPRESSION_CONFIG).isExpressionMode());

        assertFalse(new JavaTranslator(SIMPLE_CONFIG).isExpressionMode());
        assertFalse(new PythonTranslator(PROCEDURAL_CONFIG).isExpressionMode());
        assertFalse(new CppTranslator(FULL_CONFIG).isExpressionMode());
    }

    @Test
    void expressionModeRejectsWholeProgram() {
        assertThrows(UnsupportedParsingException.class,
                () -> new JavaTranslator(EXPRESSION_CONFIG).getMeaningTree(JAVA_PROGRAM));
        assertThrows(UnsupportedParsingException.class,
                () -> new PythonTranslator(EXPRESSION_CONFIG).getMeaningTree(PYTHON_PROGRAM));
        assertThrows(UnsupportedParsingException.class,
                () -> new CppTranslator(EXPRESSION_CONFIG).getMeaningTree(CPP_PROGRAM));
    }

    /* -----------------------------
    |        Режим simple           |
    ------------------------------ */

    @Test
    void simpleModeKeepsOnlyEntryPointBody() {
        String javaCode = new JavaTranslator(SIMPLE_CONFIG).getCode(
                new JavaTranslator(SIMPLE_CONFIG).getMeaningTree(JAVA_PROGRAM));
        assertFalse(javaCode.contains("class Main"));
        assertFalse(javaCode.contains("void main"));
        assertTrue(javaCode.contains("int y = helper(1);"));

        String cppCode = new CppTranslator(SIMPLE_CONFIG).getCode(
                new CppTranslator(SIMPLE_CONFIG).getMeaningTree(CPP_PROGRAM));
        assertFalse(cppCode.contains("int main("));
        assertTrue(cppCode.contains("int y = helper(1);"));

        String pythonCode = new PythonTranslator(SIMPLE_CONFIG).getCode(
                new PythonTranslator(SIMPLE_CONFIG).getMeaningTree(PYTHON_PROGRAM));
        assertFalse(pythonCode.contains("if __name__"));
        assertTrue(pythonCode.contains("def helper(x):"));
    }

    @Test
    void simpleModeBuildsEntryPointNodeWithReferences() {
        ProgramEntryPoint javaEntryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                new JavaTranslator(SIMPLE_CONFIG).getMeaningTree(JAVA_PROGRAM).getRootNode()
        );
        assertTrue(javaEntryPoint.hasMainClass(), "Main class must stay reachable by reference");
        assertTrue(javaEntryPoint.hasEntryPoint());
        assertEquals(1, javaEntryPoint.getBody().size(), "Only statements of main remain in the body");

        ProgramEntryPoint cppEntryPoint = assertInstanceOf(
                ProgramEntryPoint.class,
                new CppTranslator(SIMPLE_CONFIG).getMeaningTree(CPP_PROGRAM).getRootNode()
        );
        assertTrue(cppEntryPoint.hasEntryPoint());
    }

    @Test
    void pythonSimpleDoesNotDuplicateAlreadyFlattenedForeignEntryPoint() {
        String javaCode = new PythonTranslator(SIMPLE_CONFIG).getCode(
                new JavaTranslator(SIMPLE_CONFIG).getMeaningTree(
                        "class Main { public static void main(String[] args) { int s = 0; s++; } }"
                )
        );
        assertEquals("s: int = 0\ns += 1", javaCode.strip());

        String cppCode = new PythonTranslator(SIMPLE_CONFIG).getCode(
                new CppTranslator(SIMPLE_CONFIG).getMeaningTree(
                        "int main() { int s = 0; s++; }"
                )
        );
        assertEquals("s: int = 0\ns += 1", cppCode.strip());
    }

    @Test
    void simpleModeKeepsCodeWithoutEntryPointAsIs() {
        String javaCode = new JavaTranslator(SIMPLE_CONFIG).getCode(
                new JavaTranslator(SIMPLE_CONFIG).getMeaningTree(JAVA_WITHOUT_ENTRY_POINT));
        assertTrue(javaCode.contains("class Box"));
        assertFalse(javaCode.contains("void main"), "Simple mode must not synthesize an entry point");

        String cppCode = new CppTranslator(SIMPLE_CONFIG).getCode(
                new CppTranslator(SIMPLE_CONFIG).getMeaningTree(CPP_WITHOUT_ENTRY_POINT));
        assertTrue(cppCode.contains("int helper(int x)"));
        assertFalse(cppCode.contains("int main("));
    }

    /* -----------------------------
    |      Режим procedural         |
    ------------------------------ */

    @Test
    void proceduralModeKeepsFunctionsAndEntryPointFlat() {
        String javaCode = new JavaTranslator(PROCEDURAL_CONFIG).getCode(
                new JavaTranslator(PROCEDURAL_CONFIG).getMeaningTree(JAVA_PROGRAM));
        assertFalse(javaCode.contains("class Main"));
        assertTrue(javaCode.contains("public static int helper(int x)"));
        assertTrue(javaCode.contains("public static void main(String[] args)"));

        String cppCode = new CppTranslator(PROCEDURAL_CONFIG).getCode(
                new CppTranslator(PROCEDURAL_CONFIG).getMeaningTree(CPP_PROGRAM));
        assertTrue(cppCode.contains("int helper(int x)"));
        assertTrue(cppCode.contains("int main("));

        String pythonCode = new PythonTranslator(PROCEDURAL_CONFIG).getCode(
                new PythonTranslator(PROCEDURAL_CONFIG).getMeaningTree(PYTHON_PROGRAM));
        assertTrue(pythonCode.contains("def helper(x):"));
        assertFalse(pythonCode.contains("if __name__"));
    }

    @Test
    void proceduralAndSimpleModesAgreeForPython() {
        // В Python различаются только full и не-full: procedural и simple дают один результат
        String simpleCode = new PythonTranslator(SIMPLE_CONFIG).getCode(
                new PythonTranslator(SIMPLE_CONFIG).getMeaningTree(PYTHON_PROGRAM));
        String proceduralCode = new PythonTranslator(PROCEDURAL_CONFIG).getCode(
                new PythonTranslator(PROCEDURAL_CONFIG).getMeaningTree(PYTHON_PROGRAM));

        assertEquals(simpleCode, proceduralCode);
    }

    /* -----------------------------
    |         Режим full            |
    ------------------------------ */

    @Test
    void fullModeKeepsWholeTranslationUnit() {
        String javaCode = new JavaTranslator(FULL_CONFIG).getCode(
                new JavaTranslator(FULL_CONFIG).getMeaningTree(JAVA_PROGRAM));
        assertTrue(javaCode.contains("class Main"));
        assertTrue(javaCode.contains("static int helper(int x)"));
        assertTrue(javaCode.contains("public static void main(String[] args)"));

        String cppCode = new CppTranslator(FULL_CONFIG).getCode(
                new CppTranslator(FULL_CONFIG).getMeaningTree(CPP_PROGRAM));
        assertTrue(cppCode.contains("int helper(int x)"));
        assertTrue(cppCode.contains("int main("));

        String pythonCode = new PythonTranslator(FULL_CONFIG).getCode(
                new PythonTranslator(FULL_CONFIG).getMeaningTree(PYTHON_PROGRAM));
        assertTrue(pythonCode.contains("def run():"));
        assertTrue(pythonCode.contains("if __name__ == \"__main__\":"));
    }

    @Test
    void fullModeBuildsMissingEntryPoint() {
        String javaCode = new JavaTranslator(FULL_CONFIG).getCode(
                new JavaTranslator(FULL_CONFIG).getMeaningTree(JAVA_WITHOUT_ENTRY_POINT));
        assertTrue(javaCode.contains("public static void main(String[] args)"),
                "Java in full mode synthesizes an entry point");
        assertTrue(javaCode.contains("class Box"), "Original code must not be lost");

        String cppCode = new CppTranslator(FULL_CONFIG).getCode(
                new CppTranslator(FULL_CONFIG).getMeaningTree(CPP_WITHOUT_ENTRY_POINT));
        assertTrue(cppCode.contains("int main("), "C++ in full mode synthesizes an entry point");
        assertTrue(cppCode.contains("int helper(int x)"), "Function definitions must not be lost");
    }

    /* -----------------------------
    |   Режим на стороне Viewer     |
    ------------------------------ */

    @Test
    void viewerModeChangesOutputForTreeParsedInFullMode() {
        // Дерево разобрано один раз, а печатается в разных режимах: режим влияет и на Viewer
        MeaningTree javaTree = new JavaTranslator(FULL_CONFIG).getMeaningTree(JAVA_PROGRAM);

        assertTrue(new JavaTranslator(FULL_CONFIG).getCode(javaTree).contains("class Main"));
        assertFalse(new JavaTranslator(PROCEDURAL_CONFIG).getCode(javaTree).contains("class Main"));

        MeaningTree pythonTree = new PythonTranslator(FULL_CONFIG).getMeaningTree(PYTHON_PROGRAM);

        assertTrue(new PythonTranslator(FULL_CONFIG).getCode(pythonTree).contains("if __name__"));
        assertFalse(new PythonTranslator(SIMPLE_CONFIG).getCode(pythonTree).contains("if __name__"));
        assertFalse(new PythonTranslator(PROCEDURAL_CONFIG).getCode(pythonTree).contains("if __name__"));

        MeaningTree cppTree = new CppTranslator(FULL_CONFIG).getMeaningTree(CPP_WITHOUT_ENTRY_POINT);

        assertTrue(new CppTranslator(FULL_CONFIG).getCode(cppTree).contains("int main("));
        assertFalse(new CppTranslator(SIMPLE_CONFIG).getCode(cppTree).contains("int main("));
    }

    @Test
    void everyModeValueFromConfigRegistryIsAccepted() {
        // Страховка от расхождения между списком допустимых значений и реализацией режимов
        for (String mode : List.of("expression", "simple", "procedural", "full")) {
            Map<String, Object> config = Map.of("translationUnitMode", mode, "skipErrors", false);
            for (LanguageTranslator translator : List.of(
                    new JavaTranslator(config), new PythonTranslator(config), new CppTranslator(config))) {
                assertEquals(mode, translator.getConfigParameter("translationUnitMode").asString(),
                        "Mode " + mode + " was not applied for " + translator.getLanguageName());
            }
        }
    }
}
