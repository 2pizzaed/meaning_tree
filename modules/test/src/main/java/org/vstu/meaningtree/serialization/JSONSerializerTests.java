package org.vstu.meaningtree.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeSerializationException;
import org.vstu.meaningtree.languages.*;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.GeneratorDefinition;
import org.vstu.meaningtree.nodes.definitions.IteratorDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.ListLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.ResourceContextStatement;
import org.vstu.meaningtree.nodes.statements.ScopeDeclarationStatement;
import org.vstu.meaningtree.nodes.statements.YieldStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.ChainedAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;
import org.vstu.meaningtree.serializers.json.JsonDeserializer;
import org.vstu.meaningtree.serializers.json.JsonSerializer;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E-тесты JSON сериализации: каждый сниппет разбирается транслятором, сериализуется,
 * десериализуется и сериализуется повторно. Проверяются два инварианта:
 * <ul>
 *     <li>повторная сериализация даёт тот же JSON (дерево восстановлено полностью,
 *         включая id узлов и хеши содержимого);</li>
 *     <li>сгенерированный из восстановленного дерева код совпадает с исходным.</li>
 * </ul>
 */
public class JSONSerializerTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    private record Snippet(String language, String name, String code) {}

    /* -----------------------------
    |      Round trip корпуса       |
    ------------------------------ */

    @TestFactory
    List<DynamicTest> jsonRoundTripPreservesTreeAndGeneratedCode() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Snippet snippet : corpus()) {
            tests.add(DynamicTest.dynamicTest(
                    snippet.language() + "/" + snippet.name(),
                    () -> assertRoundTrip(snippet)
            ));
        }
        return tests;
    }

    private void assertRoundTrip(Snippet snippet) {
        LanguageTranslator translator = translator(snippet.language());
        MeaningTree original = translator.getMeaningTree(snippet.code());

        JsonObject firstPass = new JsonSerializer().serialize(original);
        MeaningTree restored = new JsonDeserializer().deserializeTree(firstPass);
        JsonObject secondPass = new JsonSerializer().serialize(restored);

        assertEquals(firstPass, secondPass,
                "Re-serialized JSON differs for " + snippet.language() + "/" + snippet.name());
        assertEquals(translator.getCode(original), translator.getCode(restored),
                "Code generated from the restored tree differs for " + snippet.language() + "/" + snippet.name());
    }

    private static List<Snippet> corpus() {
        List<Snippet> snippets = new ArrayList<>();

        // ---------- java ----------
        java(snippets, "literals", """
                int i = 42;
                long l = 10000000000L;
                double d = 1.5;
                float f = 1.5f;
                char c = 'a';
                String s = "текст";
                boolean b = true;
                Object o = null;
                int hex = 0xFF;
                """);
        java(snippets, "arithmetic", "int x = 1 + 2 * 3 - 4 / 5 % 6;");
        java(snippets, "bitwise", "int x = (1 << 2) | (3 & 4) ^ ~5 >> 1;");
        java(snippets, "logical", "boolean b = (1 < 2 && 3 > 4) || !(5 <= 6) || 7 >= 8 || 1 == 2 || 3 != 4;");
        java(snippets, "unary", """
                int x = 1;
                x++;
                ++x;
                x--;
                --x;
                int y = -x;
                int z = +x;
                """);
        java(snippets, "ternary", "int x = 1 > 2 ? 3 : 4;");
        java(snippets, "augmentedAssignments", """
                int x = 1;
                x += 2;
                x -= 3;
                x *= 4;
                x /= 5;
                x %= 6;
                """);
        java(snippets, "branches", """
                int x = 1;
                if (x > 0) {
                    x = 2;
                } else if (x < 0) {
                    x = 3;
                } else {
                    x = 4;
                }
                """);
        java(snippets, "whileAndDoWhile", """
                int x = 0;
                while (x < 10) {
                    x++;
                }
                do {
                    x--;
                } while (x > 0);
                """);
        java(snippets, "loops", """
                int[] xs = {1, 2, 3};
                for (int i = 0; i < 3; i++) {
                    xs[i] = i;
                }
                for (int v : xs) {
                    System.out.println(v);
                }
                while (true) {
                    break;
                }
                """);
        java(snippets, "switch", """
                int x = 1;
                switch (x) {
                    case 1:
                        x = 2;
                        break;
                    case 2:
                    case 3:
                        x = 4;
                        break;
                    default:
                        x = 5;
                        break;
                }
                """);
        java(snippets, "io", """
                System.out.println("hi");
                System.out.print(1);
                """);
        java(snippets, "arraysAndCalls", """
                int[] xs = new int[3];
                int y = xs[0];
                String s = "a".substring(0, 1);
                """);
        java(snippets, "objectCreation", "Object o = new Object();");
        java(snippets, "casts", """
                double d = 1.5;
                int i = (int) d;
                """);
        snippets.add(new Snippet("java", "classDefinition", """
                class Box {
                    private int value;
                    public Box(int value) {
                        this.value = value;
                    }
                    public int get() {
                        return value;
                    }
                }
                """));
        snippets.add(new Snippet("java", "nestedClassDefinition", """
                class Outer {
                    static class Animal {
                        public int speak() {
                            return 0;
                        }
                    }
                    static class Dog extends Animal {
                        public Dog() {
                        }
                        public int speak() {
                            return 1;
                        }
                    }
                }
                """));
        snippets.add(new Snippet("java", "interfaceDefinition", """
                interface Provider {
                    int get();
                    default int fallback() {
                        return 0;
                    }
                }
                """));
        snippets.add(new Snippet("java", "entryPointClass", """
                class Main {
                    static int helper(int x) {
                        return x;
                    }
                    public static void main(String[] args) {
                        helper(1);
                    }
                }
                """));
        snippets.add(new Snippet("java", "enumDeclaration", """
                enum Color {
                    RED,
                    GREEN,
                    BLUE
                }
                """));
        snippets.add(new Snippet("java", "imports", """
                import java.util.List;
                import java.util.*;
                import static java.lang.Math.abs;

                class Main {
                    public static void main(String[] args) {
                        int x = 1;
                    }
                }
                """));
        snippets.add(new Snippet("java", "comments", """
                class Main {
                    public static void main(String[] args) {
                        // строчный
                        int x = 1;
                        /* блочный
                           многострочный */
                        int y = 2;
                    }
                }
                """));

        // ---------- python ----------
        python(snippets, "literals", """
                i = 42
                d = 1.5
                s = "текст"
                b = True
                n = None
                """);
        python(snippets, "collections", """
                lst = [1, 2, 3]
                st = {1, 2}
                dct = {"a": 1}
                tpl = (1, 2)
                """);
        python(snippets, "comprehensionWithoutCondition", "squares = [x * x for x in range(10)]");
        python(snippets, "comprehensionWithCondition", "squares = [x * x for x in range(10) if x > 2]");
        python(snippets, "dictComprehension", "d = {x: x for x in range(3)}");
        python(snippets, "slices", """
                lst = [1, 2, 3]
                a = lst[0]
                b = lst[0:2]
                """);
        python(snippets, "controlFlow", """
                x = 1
                if x > 0:
                    x = 2
                elif x < 0:
                    x = 3
                else:
                    x = 4
                while x > 0:
                    x -= 1
                for i in range(0, 10, 2):
                    print(i)
                for v in [1, 2]:
                    continue
                """);
        java(snippets, "exceptions", """
                try {
                    g();
                }
                catch (IOError | ValueError e) {
                    h();
                }
                catch (Exception e) {
                    throw new ValueError("x");
                }
                finally {
                    k();
                }
                """);
        python(snippets, "exceptions", """
                try:
                    g()
                except (IOError, ValueError) as e:
                    h()
                except Exception:
                    raise
                except:
                    pass
                else:
                    m()
                finally:
                    k()
                """);
        cpp(snippets, "exceptions", """
                try {
                    g();
                } catch (const std::exception& e) {
                    h();
                } catch (...) {
                    throw;
                }
                throw ValueError("x");
                """);
        python(snippets, "functions", """
                def add(a, b):
                    return a + b

                def defaults(a, b=1):
                    return a
                """);
        python(snippets, "classDefinition", """
                class Box:
                    def __init__(self, value):
                        self.value = value

                    def get(self):
                        return self.value
                """);
        python(snippets, "nestedClassDefinition", """
                class Outer:
                    class Animal:
                        def speak(self):
                            return 0

                    class Dog(Animal):
                        def speak(self):
                            return 1
                """);
        python(snippets, "enumDeclaration", """
                class Color(Enum):
                    RED = auto()
                    GREEN = 5
                """);
        python(snippets, "imports", """
                import math
                import os.path
                from typing import List
                from math import *
                """);
        python(snippets, "entryPoint", """
                def run():
                    return 0

                if __name__ == "__main__":
                    run()
                """);
        python(snippets, "comments", """
                # строчный
                x = 1
                """);
        python(snippets, "loopElse", """
                for i in range(5):
                    if i == 3:
                        break
                else:
                    x = 1

                y = 0
                while y < 5:
                    y += 1
                else:
                    x = 2
                """);
        python(snippets, "chainedComparison", "b = 1 < 2 < 3");
        python(snippets, "containsOperator", "b = 1 in [1, 2]");
        python(snippets, "io", """
                print("hi")
                x = input()
                """);

        // ---------- c++ ----------
        cpp(snippets, "literals", """
                int i = 42;
                double d = 1.5;
                char c = 'a';
                bool b = true;
                const char* s = "text";
                """);
        cpp(snippets, "arithmetic", "int x = 1 + 2 * 3 - 4 / 5 % 6;");
        cpp(snippets, "branchesAndLoops", """
                int x = 1;
                if (x > 0) {
                    x = 2;
                } else {
                    x = 3;
                }
                while (x > 0) {
                    x--;
                }
                for (int i = 0; i < 3; i++) {
                    x += i;
                }
                switch (x) {
                    case 1:
                        break;
                    default:
                        break;
                }
                """);
        cpp(snippets, "io", """
                int x;
                std::cin >> x;
                std::cout << x << std::endl;
                """);
        cpp(snippets, "arrays", """
                int xs[3] = {1, 2, 3};
                int y = xs[0];
                """);
        cpp(snippets, "pointers", """
                int x = 1;
                int* p = &x;
                int y = *p;
                """);
        cpp(snippets, "memory", """
                int* p = new int(5);
                delete p;
                """);
        cpp(snippets, "stack_allocation", """
                Box a(1);
                Box b(1, 2 + 3);
                Box* c = new Box(1);
                """);
        snippets.add(new Snippet("c++", "functions", """
                int add(int a, int b) {
                    return a + b;
                }

                int main() {
                    return add(1, 2);
                }
                """));
        snippets.add(new Snippet("c++", "enumDeclaration", """
                enum Color {
                    RED = 1,
                    GREEN,
                    BLUE = 4
                };

                int main() {
                    Color c = Color::RED;
                    return 0;
                }
                """));
        snippets.add(new Snippet("c++", "scopedEnumDeclaration", """
                enum class Color {
                    RED,
                    GREEN
                };

                int main() {
                    return 0;
                }
                """));
        snippets.add(new Snippet("c++", "abstractInterfaceDefinition", """
                class Provider {
                public:
                    virtual int get() = 0;
                    virtual int fallback() {
                        return 0;
                    }
                };
                """));
        snippets.add(new Snippet("c++", "includes", """
                #include <iostream>
                #include "local.h"

                int main() {
                    return 0;
                }
                """));

        // ---------- generators and iterators ----------
        python(snippets, "generatorLoopForm", """
                def gen(n: int) -> Iterator[int]:
                    i: int = 0
                    while i < n:
                        yield i
                        i = i + 1
                """);
        python(snippets, "generatorFlatFormAndBareYield", """
                def flat() -> Iterator[int]:
                    yield 1
                    yield
                    yield 3
                """);
        python(snippets, "generatorDelegation", """
                def d(x) -> Iterator[int]:
                    yield from x
                """);
        java(snippets, "iteratorClass", """
                class Counter implements Iterator<Integer> {
                    private int i;
                    public boolean hasNext() {
                        return this.i < 10;
                    }
                    public Integer next() {
                        return this.i;
                    }
                }
                """);

        return snippets;
    }

    /* -----------------------------
    |     Отдельные инварианты      |
    ------------------------------ */

    @Test
    void integerLiteralKeepsRepresentationAndSizeModifiers() {
        MeaningTree tree = new JavaTranslator(CONFIG).getMeaningTree(
                "class Main { public static void main(String[] args) { long a = 10L; int b = 0xFF; int c = 7; } }"
        );

        List<IntegerLiteral> restored = nodesOf(
                new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree)),
                IntegerLiteral.class
        );

        assertEquals(3, restored.size());
        assertTrue(restored.get(0).isLong());
        assertEquals(IntegerLiteral.Representation.DECIMAL, restored.get(0).getIntegerRepresentation());
        assertEquals(10L, restored.get(0).getLongValue());

        assertEquals(IntegerLiteral.Representation.HEX, restored.get(1).getIntegerRepresentation());
        assertEquals(255L, restored.get(1).getLongValue());

        assertFalse(restored.get(2).isLong());
        assertEquals(7L, restored.get(2).getLongValue());
    }

    /**
     * Признаки строки записаны в тип, а не выведены заново при чтении: без них Си-строка после
     * круга через JSON молча превратилась бы в {@code std::string}, а ёмкость буфера исчезла.
     */
    @Test
    void inferredCStringKeepsItsRepresentationAndCapacity() {
        MeaningTree tree = new CppTranslator(Map.of(
                "translationUnitMode", "full", "skipErrors", false)).getMeaningTree("""
                #include <string.h>
                int main() {
                    char buf[64];
                    strcpy(buf, "abc");
                    return 0;
                }
                """);

        List<StringType> restored = nodesOf(
                new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree)),
                StringType.class
        );

        assertEquals(1, restored.size());
        assertTrue(restored.get(0).isCStyleString());
        assertFalse(restored.get(0).isImmutable());
        assertEquals(8, restored.get(0).getCharSize());
        assertEquals(64L, assertInstanceOf(IntegerLiteral.class, restored.get(0).getMaxLength()).getLongValue());
    }

    @Test
    void javaStringIsRestoredAsImmutable() {
        MeaningTree tree = new JavaTranslator(CONFIG).getMeaningTree(
                "class Main { public static void main(String[] args) { String s = \"a\"; } }");

        List<StringType> restored = nodesOf(
                new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree)),
                StringType.class
        );

        assertFalse(restored.isEmpty());
        assertTrue(restored.get(0).isImmutable());
        assertFalse(restored.get(0).isCStyleString());
    }

    @Test
    void augmentedAssignmentOperatorSurvivesRoundTrip() {
        AssignmentStatement statement = new AssignmentStatement(
                new SimpleIdentifier("x"),
                new IntegerLiteral(2),
                AugmentedAssignmentOperator.ADD
        );

        Node restored = new JsonDeserializer().deserialize(new JsonSerializer().serialize(statement));

        assertEquals(AugmentedAssignmentOperator.ADD,
                assertInstanceOf(AssignmentStatement.class, restored).getAugmentedOperator());
    }

    @Test
    void chainedAssignmentSurvivesRoundTrip() {
        ChainedAssignmentStatement statement = new ChainedAssignmentStatement(
                List.of(new SimpleIdentifier("a"), new SimpleIdentifier("b")),
                new IntegerLiteral(1),
                List.of(
                        new VariableDeclaration(new IntType(), new SimpleIdentifier("a")),
                        new VariableDeclaration(new IntType(), new SimpleIdentifier("b"))
                )
        );

        Node restored = new JsonDeserializer().deserialize(new JsonSerializer().serialize(statement));

        assertEquals(statement, statement.clone());
        assertEquals(statement, restored);
    }

    @Test
    void collectionLiteralKeepsElementOrderAndTypeHint() {
        ListLiteral literal = new ListLiteral(
                new IntegerLiteral(1), new IntegerLiteral(2), new IntegerLiteral(3)
        );
        literal.setTypeHint(new IntType());

        ListLiteral restored = assertInstanceOf(ListLiteral.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(literal)));

        assertEquals(
                List.of(1L, 2L, 3L),
                restored.getList().stream().map(item -> ((IntegerLiteral) item).getLongValue()).toList()
        );
        assertInstanceOf(IntType.class, restored.getTypeHint());
    }

    @Test
    void entryPointReferencesOutsideBodyAreRestored() {
        // В режиме simple тело точки входа состоит из операторов main, а сам главный класс
        // и метод main лежат вне body — по одним id их восстановить нельзя
        MeaningTree tree = new JavaTranslator(CONFIG).getMeaningTree("""
                class Main {
                    public static void main(String[] args) {
                        int x = 1;
                    }
                }
                """);

        ProgramEntryPoint original = assertInstanceOf(ProgramEntryPoint.class, tree.getRootNode());
        assertTrue(original.hasMainClass());
        assertTrue(original.hasEntryPoint());

        ProgramEntryPoint restored = assertInstanceOf(
                ProgramEntryPoint.class,
                new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree)).getRootNode()
        );

        assertTrue(restored.hasMainClass());
        assertTrue(restored.hasEntryPoint());
        assertEquals(original.getMainClass().getId(), restored.getMainClass().getId());
        assertEquals(original.getEntryPoint().getId(), restored.getEntryPoint().getId());
        assertInstanceOf(FunctionDefinition.class, restored.getEntryPoint());
    }

    @Test
    void overriddenFromSurvivesRoundTrip() {
        MethodDeclaration parentMethod = overrideRoundTripMethod("Animal", "speak", null);
        ClassDefinition parent = overrideRoundTripClass(parentMethod);

        MethodDeclaration childMethod = overrideRoundTripMethod("Dog", "speak", parentMethod.getOwner());
        childMethod.setOverriddenFrom(parentMethod);
        ClassDefinition child = overrideRoundTripClass(childMethod);

        MeaningTree tree = new MeaningTree(new ProgramEntryPoint(List.of(parent, child)));

        MeaningTree restored = new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree));
        MethodDeclaration restoredChild = findMethodNode(restored, "speak", "Dog");
        MethodDeclaration restoredParent = findMethodNode(restored, "speak", "Animal");

        assertNotNull(restoredChild.getOverriddenFromSingle());
        assertEquals(restoredParent.getId(), restoredChild.getOverriddenFromSingle().getId());
    }

    @Test
    void overriddenFromResolvesWhenBaseClassIsDeclaredAfterDerivedClass() {
        // Базовый класс лежит в дереве после наследника — разрешение по id не может быть
        // немедленным, как для parent_decl_id, а должно быть отложено до конца разбора дерева.
        MethodDeclaration parentMethod = overrideRoundTripMethod("Animal", "speak", null);
        ClassDefinition parent = overrideRoundTripClass(parentMethod);

        MethodDeclaration childMethod = overrideRoundTripMethod("Dog", "speak", parentMethod.getOwner());
        childMethod.setOverriddenFrom(parentMethod);
        ClassDefinition child = overrideRoundTripClass(childMethod);

        MeaningTree tree = new MeaningTree(new ProgramEntryPoint(List.of(child, parent)));

        MeaningTree restored = new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree));
        MethodDeclaration restoredChild = findMethodNode(restored, "speak", "Dog");
        MethodDeclaration restoredParent = findMethodNode(restored, "speak", "Animal");

        assertNotNull(restoredChild.getOverriddenFromSingle());
        assertEquals(restoredParent.getId(), restoredChild.getOverriddenFromSingle().getId());
    }

    private static MethodDeclaration overrideRoundTripMethod(String ownerName, String methodName,
                                                             UserType parentOwner) {
        var owner = parentOwner == null
                ? new ClassDeclaration(new SimpleIdentifier(ownerName))
                : new ClassDeclaration(List.of(), new SimpleIdentifier(ownerName), List.of(), parentOwner);
        return new MethodDeclaration(
                owner.getTypeNode(), new SimpleIdentifier(methodName), new IntType(),
                List.of(), List.of(DeclarationModifier.PUBLIC)
        );
    }

    private static ClassDefinition overrideRoundTripClass(MethodDeclaration method) {
        return new ClassDefinition(
                ClassDeclaration.withTypeNode(
                        List.of(), (SimpleIdentifier) method.getOwner().getName(), List.of(), method.getOwner()
                ),
                new CompoundStatement(new MethodDefinition(method, new CompoundStatement()))
        );
    }

    private static MethodDeclaration findMethodNode(MeaningTree tree, String methodName, String ownerName) {
        return StreamSupport.stream(tree.spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(MethodDeclaration.class::isInstance)
                .map(MethodDeclaration.class::cast)
                .filter(m -> m.getName().getName().equals(methodName)
                        && m.getOwner() != null && m.getOwner().getName().getName().equals(ownerName))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void nodeIdentifiersAreStableAcrossRoundTrip() {
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree("""
                def add(a, b=1):
                    return a + b
                """);

        List<Long> originalIds = idsOf(tree);
        List<Long> restoredIds = idsOf(new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree)));

        assertEquals(originalIds, restoredIds);
    }

    @Test
    void bytePositionLabelsSurviveRoundTripAsIntArray() {
        MeaningTree tree = new JavaTranslator(CONFIG).getMeaningTree(
                "class Main { public static void main(String[] args) { int x = 1; } }"
        );

        Node restored = new JsonDeserializer().deserializeTree(new JsonSerializer().serialize(tree)).getRootNode();
        VariableDeclaration declaration = nodesOf(restored, VariableDeclaration.class).getFirst();

        Label bytePosition = declaration.getLabel(Label.BYTEPOS_ANNOTATED);
        assertNotNull(bytePosition, "Byte position label must be restored");
        int[] position = bytePosition.attributeAsIntArray();
        assertEquals(2, position.length);
        assertTrue(position[1] > 0);
    }

    @Test
    void treeLabelsAreSerializedAndRestored() {
        MeaningTree tree = new MeaningTree(new AddOp(new IntegerLiteral(1), new IntegerLiteral(2)));
        tree.setLabel(new Label(Label.VALUE, "полезная нагрузка"));

        JsonObject json = new JsonSerializer().serialize(tree);
        assertTrue(json.has("labels"));

        MeaningTree restored = new JsonDeserializer().deserializeTree(json);
        assertEquals("полезная нагрузка", restored.getLabel(Label.VALUE).attributeAsString());
    }

    /* -----------------------------
    |          Source map           |
    ------------------------------ */

    @Test
    void sourceMapRoundTripKeepsCodePositionsAndMetrics() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        MeaningTree tree = translator.getMeaningTree("""
                class Main {
                    public static void main(String[] args) {
                        int x = 1;
                        if (x > 0) {
                            x = 2;
                        }
                    }
                }
                """);
        SourceMap sourceMap = new SourceMapGenerator(translator).process(tree);

        JsonObject json = new JsonSerializer().serialize(sourceMap);
        assertEquals("source_map", json.get("type").getAsString());

        SourceMap restored = new JsonDeserializer().deserializeSourceMap(json);

        assertEquals(sourceMap.code(), restored.code());
        assertEquals(sourceMap.language(), restored.language());
        assertEquals(sourceMap.bytePositions(), restored.bytePositions());
        assertEquals(sourceMap.metrics().keySet(), restored.metrics().keySet());
        assertEquals(
                sourceMap.metrics().get("cyclomatic").intValue(),
                restored.metrics().get("cyclomatic").intValue()
        );
        assertNull(restored.projectRootPath());
        assertNull(restored.projectFileRelPath());
        assertEquals(new JsonSerializer().serialize(sourceMap.root()), json.getAsJsonObject("origin"));
    }

    @Test
    void sourceMapPositionsPointToNodesOfRestoredTree() {
        PythonTranslator translator = new PythonTranslator(CONFIG);
        SourceMap sourceMap = new SourceMapGenerator(translator).process(
                translator.getMeaningTree("x = 1\ny = x + 2\n")
        );

        SourceMap restored = new JsonDeserializer()
                .deserializeSourceMap(new JsonSerializer().serialize(sourceMap));

        List<Long> restoredIds = StreamSupport.stream(restored.root().spliterator(), false)
                .map(info -> info.node().getId())
                .toList();
        assertTrue(restoredIds.containsAll(restored.bytePositions().keySet()),
                "Every marked id must be present in the restored tree");
    }

    /* -----------------------------
    |            Токены             |
    ------------------------------ */

    @Test
    void tokenListRoundTripKeepsValuesAndPositions() {
        JavaTranslator translator = new JavaTranslator(CONFIG);
        TokenList tokens = translator.getCodeAsTokens("int x = 1 + 2;", false, true);

        JsonObject json = new JsonSerializer().serialize(tokens);
        assertEquals("tokens", json.get("type").getAsString());

        TokenList restored = new JsonDeserializer().deserializeTokens(json);

        assertEquals(tokens.size(), restored.size());
        for (int i = 0; i < tokens.size(); i++) {
            Token expected = tokens.get(i);
            Token actual = restored.get(i);
            assertEquals(expected.value, actual.value);
            assertEquals(expected.type, actual.type);
            assertEquals(expected.bytePos(), actual.bytePos());
        }
    }

    @Test
    void exceptionCatchStatementSurvivesRoundTrip() {
        ExceptionCatchStatement statement = new ExceptionCatchStatement(
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("risky"))),
                List.of(
                        new CatchClause(
                                List.of(
                                        new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("IOException")),
                                        new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("SQLException"))
                                ),
                                new SimpleIdentifier("e"),
                                new CompoundStatement(new RaiseExceptionStatement())
                        ),
                        new CatchClause(new CompoundStatement())
                ),
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("ok"))),
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("cleanup")))
        );

        ExceptionCatchStatement restored = assertInstanceOf(ExceptionCatchStatement.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(statement)));

        assertEquals(statement, statement.clone());
        assertEquals(statement, restored);

        assertEquals(2, restored.getCatchClauses().size());
        CatchClause typed = restored.getCatchClauses().getFirst();
        assertEquals(2, typed.getExceptionTypes().size());
        assertEquals("e", typed.getName().getName());
        assertTrue(restored.getCatchClauses().get(1).catchesAny());
        assertFalse(restored.getCatchClauses().get(1).hasName());
        assertTrue(restored.hasElseBranch());
        assertTrue(restored.hasFinallyBranch());
    }

    @Test
    void exceptionCatchStatementChildrenAreReachableByTraversal() {
        // Все дочерние поля помечены @TreeNode, иначе обход дерева пропустит ветви
        ExceptionCatchStatement statement = new ExceptionCatchStatement(
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("body"))),
                List.of(new CatchClause(
                        new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("Error")),
                        new SimpleIdentifier("e"),
                        new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("handler")))
                )),
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("elseBody"))),
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("finallyBody")))
        );

        List<String> names = nodesOf(statement, SimpleIdentifier.class).stream()
                .map(SimpleIdentifier::getName)
                .toList();

        assertTrue(names.containsAll(List.of("body", "handler", "elseBody", "finallyBody", "e")),
                "Traversal missed some children: " + names);
        assertEquals(1, nodesOf(statement, CatchClause.class).size());
        assertEquals(1, nodesOf(statement, org.vstu.meaningtree.nodes.types.user.Class.class).size());
    }

    @Test
    void yieldStatementSurvivesRoundTripWithAndWithoutValue() {
        for (YieldStatement statement : List.of(
                new YieldStatement(new SimpleIdentifier("value")),
                YieldStatement.delegating(new SimpleIdentifier("source")),
                new YieldStatement())) {
            JsonObject json = new JsonSerializer().serialize(statement);
            YieldStatement restored = (YieldStatement) new JsonDeserializer().deserialize(json);

            assertEquals(statement.hasValue(), restored.hasValue());
            assertEquals(statement.isDelegated(), restored.isDelegated());
            assertEquals(statement, restored);
        }
    }

    @Test
    void iteratorDefinitionTraversalReachesEveryChild() {
        IteratorDefinition definition = new IteratorDefinition(
                new ClassDeclaration(new SimpleIdentifier("Counter")),
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("member"))),
                new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("Element")),
                new SimpleIdentifier("hasNext"),
                new SimpleIdentifier("next")
        );

        List<String> names = nodesOf(definition, SimpleIdentifier.class).stream()
                .map(SimpleIdentifier::getName)
                .toList();

        assertTrue(names.containsAll(List.of("Counter", "member", "Element", "hasNext", "next")),
                "Traversal missed some children: " + names);
    }

    @Test
    void iteratorDefinitionSurvivesRoundTrip() {
        IteratorDefinition definition = new IteratorDefinition(
                new ClassDeclaration(new SimpleIdentifier("Counter")),
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("member"))),
                new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("Element")),
                new SimpleIdentifier("hasNext"),
                new SimpleIdentifier("next")
        );

        JsonObject json = new JsonSerializer().serialize(definition);
        IteratorDefinition restored = (IteratorDefinition) new JsonDeserializer().deserialize(json);

        assertEquals("iterator_definition", json.get("type").getAsString(),
                "the subtype must not be serialized under its superclass name");
        assertEquals(definition.getElementType(), restored.getElementType());
        assertEquals(definition.getHasNextMethod(), restored.getHasNextMethod());
        assertEquals(definition.getNextMethod(), restored.getNextMethod());
        assertEquals(definition, restored);
    }

    @Test
    void generatorDefinitionIsNotSerializedAsAPlainFunction() {
        GeneratorDefinition definition = new GeneratorDefinition(
                new FunctionDeclaration(new SimpleIdentifier("gen"), new IntType(), List.of()),
                new CompoundStatement(new YieldStatement(new SimpleIdentifier("value")))
        );

        JsonObject json = new JsonSerializer().serialize(definition);
        assertEquals("generator_definition", json.get("type").getAsString());
        assertEquals(definition, new JsonDeserializer().deserialize(json));
    }

    @Test
    void resourceContextStatementSurvivesRoundTrip() {
        ResourceContextStatement statement = new ResourceContextStatement(
                List.of(
                        new VariableDeclaration(
                                new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("FileReader")),
                                new SimpleIdentifier("reader"),
                                new SimpleIdentifier("source")
                        ),
                        new SimpleIdentifier("lock")
                ),
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("body")))
        );

        ResourceContextStatement restored = assertInstanceOf(ResourceContextStatement.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(statement)));

        assertEquals(statement, statement.clone());
        assertEquals(statement, restored);

        assertEquals(2, restored.getResourceDeclarations().size());
        assertEquals("reader", ResourceContextStatement.resourceName(
                restored.getResourceDeclarations().getFirst()).getName());
        // Ресурс без имени — обычное выражение, освобождать в C++ его не по чему
        assertNull(ResourceContextStatement.resourceName(restored.getResourceDeclarations().get(1)));

        List<String> names = nodesOf(statement, SimpleIdentifier.class).stream()
                .map(SimpleIdentifier::getName)
                .toList();
        assertTrue(names.containsAll(List.of("reader", "source", "lock", "body")),
                "Traversal missed some children: " + names);
    }

    @Test
    void tryWithResourcesSurvivesRoundTrip() {
        ExceptionCatchStatement statement = new ExceptionCatchStatement(
                new CompoundStatement(new ExpressionStatement(new SimpleIdentifier("body"))),
                List.of(new VariableDeclaration(
                        new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("FileReader")),
                        new SimpleIdentifier("reader"),
                        new SimpleIdentifier("source")
                )),
                List.of(new CatchClause(
                        new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("IOException")),
                        new SimpleIdentifier("e"),
                        new CompoundStatement()
                )),
                null,
                null
        );

        ExceptionCatchStatement restored = assertInstanceOf(ExceptionCatchStatement.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(statement)));

        assertEquals(statement, statement.clone());
        assertEquals(statement, restored);
        assertTrue(restored.hasResourceDeclarations());
        assertEquals(1, restored.getResourceDeclarations().size());

        // Обычный try отличим от try-with-resources и после round trip
        ExceptionCatchStatement plain = new ExceptionCatchStatement(new CompoundStatement(), List.of());
        assertFalse(assertInstanceOf(ExceptionCatchStatement.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(plain)))
                .hasResourceDeclarations());

        List<String> names = nodesOf(statement, SimpleIdentifier.class).stream()
                .map(SimpleIdentifier::getName)
                .toList();
        assertTrue(names.containsAll(List.of("reader", "source", "body", "e")),
                "Traversal missed some children: " + names);
    }

    @Test
    void scopeDeclarationStatementSurvivesRoundTrip() {
        ScopeDeclarationStatement statement = new ScopeDeclarationStatement(
                ScopeDeclarationStatement.Kind.GLOBAL,
                List.of(new SimpleIdentifier("counter"), new SimpleIdentifier("total"))
        );

        ScopeDeclarationStatement restored = assertInstanceOf(ScopeDeclarationStatement.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(statement)));

        assertEquals(statement, statement.clone());
        assertEquals(statement, restored);
        assertEquals(ScopeDeclarationStatement.Kind.GLOBAL, restored.getKind());
        assertEquals(2, restored.getNames().size());

        // Вид объявления — часть значения узла, иначе nonlocal читался бы как global
        ScopeDeclarationStatement nonlocal = new ScopeDeclarationStatement(
                ScopeDeclarationStatement.Kind.NONLOCAL, new SimpleIdentifier("counter"));
        assertNotEquals(
                new ScopeDeclarationStatement(ScopeDeclarationStatement.Kind.GLOBAL, new SimpleIdentifier("counter")),
                nonlocal);
        assertEquals(ScopeDeclarationStatement.Kind.NONLOCAL,
                assertInstanceOf(ScopeDeclarationStatement.class,
                        new JsonDeserializer().deserialize(new JsonSerializer().serialize(nonlocal))).getKind());

        List<String> names = nodesOf(statement, SimpleIdentifier.class).stream()
                .map(SimpleIdentifier::getName)
                .toList();
        assertTrue(names.containsAll(List.of("counter", "total")),
                "Traversal missed some children: " + names);
    }

    @Test
    void scopeDeclarationRejectsEmptyNameList() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScopeDeclarationStatement(ScopeDeclarationStatement.Kind.GLOBAL, List.of()));
    }

    @Test
    void resourceContextRejectsNodesThatAreNeitherDeclarationNorExpression() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceContextStatement(
                List.of(new ExpressionStatement(new SimpleIdentifier("stmt"))), new CompoundStatement()));
        assertThrows(IllegalArgumentException.class,
                () -> new ResourceContextStatement(List.of(), new CompoundStatement()));
    }

    @Test
    void raiseExceptionStatementSurvivesRoundTripWithAndWithoutValue() {
        RaiseExceptionStatement withValue = new RaiseExceptionStatement(
                new ObjectNewExpression(new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("ValueError")), StringLiteral.fromUnescaped("boom", StringLiteral.Type.NONE))
        );
        RaiseExceptionStatement rethrow = new RaiseExceptionStatement();

        RaiseExceptionStatement restoredWithValue = assertInstanceOf(RaiseExceptionStatement.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(withValue)));
        RaiseExceptionStatement restoredRethrow = assertInstanceOf(RaiseExceptionStatement.class,
                new JsonDeserializer().deserialize(new JsonSerializer().serialize(rethrow)));

        assertEquals(withValue, withValue.clone());
        assertEquals(rethrow, rethrow.clone());
        assertEquals(withValue, restoredWithValue);
        assertEquals(rethrow, restoredRethrow);

        assertTrue(restoredWithValue.hasException());
        assertInstanceOf(ObjectNewExpression.class, restoredWithValue.getException());
        assertFalse(restoredRethrow.hasException());
        assertNull(restoredRethrow.getException());
    }

    /* -----------------------------
    |        Ошибочные данные       |
    ------------------------------ */

    @Test
    void unknownNodeTypeIsReportedAsSerializationException() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "definitely_unknown_node");
        json.addProperty("id", 1);

        assertThrows(MeaningTreeSerializationException.class, () -> new JsonDeserializer().deserialize(json));
    }

    @Test
    void wrongRootTypeIsRejected() {
        JsonObject notATree = new JsonObject();
        notATree.addProperty("type", "tokens");

        JsonObject notASourceMap = new JsonObject();
        notASourceMap.addProperty("type", "meaning_tree");

        assertThrows(MeaningTreeSerializationException.class, () -> new JsonDeserializer().deserializeTree(notATree));
        assertThrows(MeaningTreeSerializationException.class,
                () -> new JsonDeserializer().deserializeSourceMap(notASourceMap));
    }

    @Test
    void everyRegisteredNodeTypeInCorpusIsDeserializable() {
        // Страховка от рассинхронизации имён между JsonNodeTypeClassMapper и десериализатором:
        // каждый тип, встретившийся в корпусе, должен разбираться обратно
        for (Snippet snippet : corpus()) {
            LanguageTranslator translator = translator(snippet.language());
            JsonObject json = new JsonSerializer().serialize(translator.getMeaningTree(snippet.code()));
            for (String nodeType : collectNodeTypes(json)) {
                assertDoesNotThrow(
                        () -> new JsonDeserializer().deserializeTree(json),
                        "Node type " + nodeType + " cannot be deserialized back"
                );
            }
        }
    }

    /* -----------------------------
    |          Инструменты          |
    ------------------------------ */

    private static void java(List<Snippet> out, String name, String body) {
        out.add(new Snippet("java", name,
                "class Main {\n    public static void main(String[] args) {\n" + body.indent(8) + "    }\n}\n"));
    }

    private static void python(List<Snippet> out, String name, String body) {
        out.add(new Snippet("python", name, body));
    }

    private static void cpp(List<Snippet> out, String name, String body) {
        out.add(new Snippet("c++", name, "int main() {\n" + body.indent(4) + "}\n"));
    }

    private static LanguageTranslator translator(String language) {
        return switch (language) {
            case "java" -> new JavaTranslator(CONFIG);
            case "python" -> new PythonTranslator(CONFIG);
            case "c++" -> new CppTranslator(CONFIG);
            default -> throw new IllegalArgumentException("Unknown language: " + language);
        };
    }

    private static <T extends Node> List<T> nodesOf(MeaningTree tree, Class<T> nodeClass) {
        return StreamSupport.stream(tree.spliterator(), false)
                .map(info -> info.node())
                .filter(nodeClass::isInstance)
                .map(nodeClass::cast)
                .toList();
    }

    private static <T extends Node> List<T> nodesOf(Node root, Class<T> nodeClass) {
        return StreamSupport.stream(root.spliterator(), false)
                .map(info -> info.node())
                .filter(nodeClass::isInstance)
                .map(nodeClass::cast)
                .toList();
    }

    private static List<Long> idsOf(MeaningTree tree) {
        return StreamSupport.stream(tree.spliterator(), false)
                .map(info -> info.node().getId())
                .toList();
    }

    private static List<String> collectNodeTypes(JsonElement element) {
        List<String> types = new ArrayList<>();
        if (element instanceof JsonObject object) {
            if (object.has("type") && object.get("type").isJsonPrimitive()) {
                types.add(object.get("type").getAsString());
            }
            for (String key : object.keySet()) {
                types.addAll(collectNodeTypes(object.get(key)));
            }
        } else if (element instanceof JsonArray array) {
            for (JsonElement item : array) {
                types.addAll(collectNodeTypes(item));
            }
        }
        return types;
    }
}
