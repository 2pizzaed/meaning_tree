package org.vstu.meaningtree.languages.helpers;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedConversionException;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.definitions.GeneratorDefinition;
import org.vstu.meaningtree.nodes.definitions.IteratorDefinition;
import org.vstu.meaningtree.nodes.statements.YieldStatement;

import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Покрывает понижение генератора в класс-итератор: обе распознаваемые формы, разворачивание
 * делегирования, переписывание вызовов, каждый вид отказа и то, что языки без генераторов
 * действительно отвергают узлы, а не отрисовывают их надклассом.
 */
public class GeneratorLowererTests {
    private static final Map<String, Object> CONFIG = Map.of(
            "translationUnitMode", "simple",
            "skipErrors", false
    );

    private static String toJava(String python) {
        return new JavaTranslator(CONFIG).getCode(new PythonTranslator(CONFIG).getMeaningTree(python));
    }

    private static String toPython(String python) {
        return new PythonTranslator(CONFIG).getCode(new PythonTranslator(CONFIG).getMeaningTree(python));
    }

    private static long countNodes(MeaningTree tree, java.util.function.Predicate<Object> predicate) {
        return StreamSupport.stream(tree.spliterator(), false)
                .filter(info -> predicate.test(info.node()))
                .count();
    }

    /* ---------------- разбор ---------------- */

    @Test
    void functionWithYieldBecomesGeneratorWithElementType() {
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree("""
                def gen(n: int) -> Iterator[int]:
                    yield n
                """);

        assertEquals(1, countNodes(tree, node -> node instanceof GeneratorDefinition));
        assertEquals(1, countNodes(tree, node -> node instanceof YieldStatement));
        GeneratorDefinition generator = StreamSupport.stream(tree.spliterator(), false)
                .map(info -> info.node())
                .filter(GeneratorDefinition.class::isInstance)
                .map(GeneratorDefinition.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals("int", generator.getElementType().toString().contains("Int") ? "int"
                : generator.getElementType().toString(),
                "Iterator[int] must be unwrapped to the element type");
    }

    @Test
    void functionWithoutYieldStaysPlainFunction() {
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree("""
                def plain(n: int) -> int:
                    return n
                """);
        assertEquals(0, countNodes(tree, node -> node instanceof GeneratorDefinition));
    }

    @Test
    void yieldOfNestedFunctionDoesNotMakeOuterFunctionAGenerator() {
        MeaningTree tree = new PythonTranslator(CONFIG).getMeaningTree("""
                def outer(n: int) -> int:
                    def inner() -> Iterator[int]:
                        yield n

                    return n
                """);
        assertEquals(1, countNodes(tree, node -> node instanceof GeneratorDefinition),
                "only the nested function yields, so only it is a generator");
    }

    @Test
    void yieldInExpressionPositionIsRejected() {
        assertThrows(UnsupportedParsingException.class, () -> new PythonTranslator(CONFIG).getMeaningTree("""
                def gen():
                    x = yield 1
                """));
    }

    @Test
    void generatorMethodIsRejected() {
        assertThrows(UnsupportedParsingException.class, () -> new PythonTranslator(CONFIG).getMeaningTree("""
                class Holder:
                    def items(self) -> Iterator[int]:
                        yield 1
                """));
    }

    /* ---------------- python отрисовывает генератор как есть ---------------- */

    @Test
    void pythonKeepsGeneratorAndDelegation() {
        String source = """
                def gen(n: int) -> Iterator[int]:
                    i: int = 0
                    while i < n:
                        yield i
                        i = i + 1
                """;
        assertEquals(source.strip(), toPython(source).strip());

        String delegating = """
                def d(x) -> Iterator[int]:
                    yield from x
                """;
        assertEquals(delegating.strip(), toPython(delegating).strip(),
                "yield from must survive a python round trip, not be expanded into a loop");
    }

    /* ---------------- цикловая форма ---------------- */

    @Test
    void whileFormBecomesIteratorClass() {
        String java = toJava("""
                def gen(n: int) -> Iterator[int]:
                    i: int = 0
                    while i < n:
                        yield i
                        i = i + 1
                """);

        assertTrue(java.contains("class gen implements java.util.Iterator<Integer>"), java);
        assertTrue(java.contains("public gen(int n)"), java);
        assertTrue(java.contains("this.n = n;"), "constructor must copy parameters into fields: " + java);
        assertTrue(java.contains("return this.i < this.n;"), "hasNext is the loop condition: " + java);
        assertTrue(java.contains("int _value = this.i;"), java);
        assertTrue(java.contains("return _value;"), java);
        assertFalse(java.contains("yield"), java);
    }

    @Test
    void rangeFormAdvancesTheLoopVariableInNext() {
        String java = toJava("""
                def r(n: int) -> Iterator[int]:
                    for i in range(n):
                        yield i * 2
                """);

        assertTrue(java.contains("private int i;"), java);
        assertTrue(java.contains("this.i = 0;"), "range start goes to the constructor: " + java);
        assertTrue(java.contains("return this.i < this.n;"), java);
        assertTrue(java.contains("this.i = this.i + 1;"), "the range step is the advance in next(): " + java);
    }

    @Test
    void statementsAfterYieldRunBeforeTheNextValueIsProduced() {
        String java = toJava("""
                def gen(n: int) -> Iterator[int]:
                    i: int = 0
                    while i < n:
                        yield i
                        i = i + 2
                """);

        int valueIndex = java.indexOf("int _value = this.i;");
        int advanceIndex = java.indexOf("this.i = this.i + 2;");
        int returnIndex = java.indexOf("return _value;");
        assertTrue(valueIndex >= 0 && advanceIndex > valueIndex && returnIndex > advanceIndex,
                "the value must be captured before the loop tail runs: " + java);
    }

    /* ---------------- плоская форма ---------------- */

    @Test
    void flatFormBecomesStateCounter() {
        String java = toJava("""
                def flat() -> Iterator[int]:
                    yield 1
                    yield 2
                """);

        assertTrue(java.contains("private int _state;"), java);
        assertTrue(java.contains("return this._state < 2;"), java);
        assertTrue(java.contains("if (this._state == 0)"), java);
        assertTrue(java.contains("if (this._state == 1)"), java);
        assertFalse(java.contains("0L"), "state literals must stay int, not long: " + java);
    }

    /* ---------------- делегирование ---------------- */

    @Test
    void delegationBecomesNestedIterator() {
        String java = toJava("""
                def d(x: list) -> Iterator[int]:
                    yield from x
                """);

        assertTrue(java.contains("this._source = this.x.iterator();"), java);
        assertTrue(java.contains("return this._source.hasNext();"), java);
        assertTrue(java.contains("this._source.next()"), java);
    }

    /* ---------------- вызовы ---------------- */

    @Test
    void callOfALoweredGeneratorBecomesObjectCreation() {
        String java = toJava("""
                def flat() -> Iterator[int]:
                    yield 1

                g = flat()
                """);
        assertTrue(java.contains("new flat()"), "a generator call must become object creation: " + java);
    }

    /* ---------------- отказы ---------------- */

    @Test
    void yieldUnderConditionIsRefusedWithItsReason() {
        var error = assertThrows(UnsupportedConversionException.class, () -> toJava("""
                def bad(n: int) -> Iterator[int]:
                    if n > 0:
                        yield n
                """));
        assertTrue(error.getMessage().contains("IfStatement"), error.getMessage());
    }

    @Test
    void yieldInTwoLoopsIsRefused() {
        var error = assertThrows(UnsupportedConversionException.class, () -> toJava("""
                def bad(n: int) -> Iterator[int]:
                    for i in range(n):
                        yield i
                    for j in range(n):
                        yield j
                """));
        assertTrue(error.getMessage().contains("after the yielding loop"), error.getMessage());
    }

    @Test
    void twoYieldsInOneLoopAreRefused() {
        var error = assertThrows(UnsupportedConversionException.class, () -> toJava("""
                def bad(n: int) -> Iterator[int]:
                    for i in range(n):
                        yield i
                        yield i + 1
                """));
        assertTrue(error.getMessage().contains("exactly one yield"), error.getMessage());
    }

    @Test
    void breakInsideAYieldingLoopIsRefused() {
        var error = assertThrows(UnsupportedConversionException.class, () -> toJava("""
                def bad(n: int) -> Iterator[int]:
                    while True:
                        yield n
                        break
                """));
        assertTrue(error.getMessage().contains("break, continue or return"), error.getMessage());
    }

    @Test
    void statementsBetweenYieldsOutsideALoopAreRefused() {
        var error = assertThrows(UnsupportedConversionException.class, () -> toJava("""
                def bad(n: int) -> Iterator[int]:
                    yield n
                    n = n + 1
                    yield n
                """));
        assertTrue(error.getMessage().contains("between or after yields"), error.getMessage());
    }

    /* ---------------- C++ отвергает все три узла ---------------- */

    @Test
    void cppRefusesGeneratorsAndIterators() {
        var generatorError = assertThrows(UnsupportedConversionException.class,
                () -> new CppTranslator(CONFIG).getCode(new PythonTranslator(CONFIG).getMeaningTree("""
                        def gen() -> Iterator[int]:
                            yield 1
                        """)));
        assertTrue(generatorError.getMessage().contains("GeneratorDefinition"), generatorError.getMessage());
        assertTrue(generatorError.getMessage().contains("YieldStatement"), generatorError.getMessage());

        var iteratorError = assertThrows(UnsupportedConversionException.class,
                () -> new CppTranslator(CONFIG).getCode(new JavaTranslator(CONFIG).getMeaningTree("""
                        class Counter implements Iterator<Integer> {
                            public boolean hasNext() { return false; }
                            public Integer next() { return 0; }
                        }
                        """)));
        assertTrue(iteratorError.getMessage().contains("IteratorDefinition"), iteratorError.getMessage());
    }

    /* ---------------- java разбирает класс-итератор ---------------- */

    @Test
    void javaIteratorClassIsRecognizedAndRoundTrips() {
        String source = """
                class Counter implements Iterator<Integer> {
                    private int i;
                    public boolean hasNext() {
                        return this.i < 10;
                    }
                    public Integer next() {
                        return this.i;
                    }
                }
                """;
        MeaningTree tree = new JavaTranslator(CONFIG).getMeaningTree(source);
        assertEquals(1, countNodes(tree, node -> node instanceof IteratorDefinition));

        String java = new JavaTranslator(CONFIG).getCode(new JavaTranslator(CONFIG).getMeaningTree(source));
        assertTrue(java.contains("implements Iterator<Integer>"),
                "the iterator interface must render through implements, not extends: " + java);
    }

    @Test
    void javaClassWithoutBothProtocolMethodsStaysAPlainClass() {
        MeaningTree tree = new JavaTranslator(CONFIG).getMeaningTree("""
                class Half implements Iterator<Integer> {
                    public boolean hasNext() {
                        return false;
                    }
                }
                """);
        assertEquals(0, countNodes(tree, node -> node instanceof IteratorDefinition));
    }

    @Test
    void pythonRendersAJavaIteratorWithItsOwnProtocol() {
        String python = new PythonTranslator(CONFIG).getCode(new JavaTranslator(CONFIG).getMeaningTree("""
                class Counter implements Iterator<Integer> {
                    private int i;
                    public boolean hasNext() {
                        return this.i < 10;
                    }
                    public Integer next() {
                        return this.i;
                    }
                }
                """));

        assertTrue(python.contains("class Counter:"),
                "the iterator interface carries nothing in Python and must not become a base class: " + python);
        assertTrue(python.contains("def __iter__(self):"), python);
        assertTrue(python.contains("raise StopIteration"), python);
        assertTrue(python.contains("return self.next()"), python);
        assertTrue(python.contains("def hasNext(self)"),
                "the class body is rendered as it is, methods keep their names: " + python);
    }
}
