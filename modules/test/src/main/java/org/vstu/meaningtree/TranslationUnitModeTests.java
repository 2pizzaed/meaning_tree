package org.vstu.meaningtree;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TranslationUnitModeTests {
    private static final Map<String, Object> PROCEDURAL_CONFIG = Map.of(
            "translationUnitMode", "procedural",
            "skipErrors", false
    );
    private static final Map<String, Object> FULL_CONFIG = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

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
}
