package org.vstu.meaningtree.analysis;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.CppTranslator;
import org.vstu.meaningtree.languages.JavaTranslator;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.utils.analysis.ScopeTableBuilder;
import org.vstu.meaningtree.utils.analysis.symbols.VariableUsageIndex;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariableUsageIndexTests {
    private static final Map<String, Object> FULL = Map.of(
            "translationUnitMode", "full",
            "skipErrors", false
    );

    @Test
    void countsEveryUseOfALocalVariable() {
        Indexed indexed = index(new CppTranslator(FULL), """
                int main() {
                    int a = 1;
                    a = a + 1;
                    return a;
                }
                """);

        assertEquals(3, indexed.usagesOf("a").size(), "a on the left, a on the right, a in return");
    }

    /**
     * Параметров нет в {@link ScopeTable} вовсе, поэтому именно они проверяют, что индекс не
     * сводится к запросам в таблицу.
     */
    @Test
    void resolvesFunctionParametersWhichScopeTableDoesNotHold() {
        Indexed indexed = index(new CppTranslator(FULL), """
                int twice(int value) {
                    return value + value;
                }
                """);

        Declaration parameter = indexed.declaration("value");
        assertTrue(parameter instanceof DeclarationArgument, "parameter is indexed as its own declaration");
        assertEquals(2, indexed.usagesOf("value").size());
    }

    @Test
    void innerBlockDeclarationShadowsOuterOne() {
        Indexed indexed = index(new CppTranslator(FULL), """
                int main() {
                    int x = 1;
                    {
                        int x = 2;
                        x = x + 1;
                    }
                    return x;
                }
                """);

        List<Declaration> declarations = indexed.declarationsNamed("x");
        assertEquals(2, declarations.size(), "two distinct declarations of x");
        Declaration outer = declarations.get(0);
        Declaration inner = declarations.get(1);
        assertNotSame(outer, inner);

        assertEquals(1, indexed.index.usagesOf(outer).size(), "only the return sees the outer x");
        assertEquals(2, indexed.index.usagesOf(inner).size(), "both uses inside the block see the inner x");
    }

    @Test
    void sameNameInTwoFunctionsResolvesToDifferentDeclarations() {
        Indexed indexed = index(new CppTranslator(FULL), """
                int first() {
                    int v = 1;
                    return v;
                }
                int second() {
                    int v = 2;
                    return v + v;
                }
                """);

        List<Declaration> declarations = indexed.declarationsNamed("v");
        assertEquals(2, declarations.size());
        assertEquals(1, indexed.index.usagesOf(declarations.get(0)).size());
        assertEquals(2, indexed.index.usagesOf(declarations.get(1)).size());
    }

    @Test
    void globalVariableIsSeenFromInsideAFunction() {
        Indexed indexed = index(new CppTranslator(FULL), """
                int total = 0;
                int main() {
                    total = total + 1;
                    return total;
                }
                """);

        assertEquals(3, indexed.usagesOf("total").size());
    }

    @Test
    void classFieldIsResolvedInsideItsMethods() {
        Indexed indexed = index(new JavaTranslator(FULL), """
                public class Main {
                    private int counter = 0;
                    public int bump() {
                        counter = counter + 1;
                        return counter;
                    }
                }
                """);

        assertEquals(3, indexed.usagesOf("counter").size());
    }

    /**
     * В Python область открывает только определение, поэтому имя, присвоенное внутри
     * {@code if}, остаётся тем же именем функции — индекс обязан следовать правилу языка, а не
     * форме дерева.
     */
    @Test
    void pythonBlockDoesNotIntroduceANewScope() {
        Indexed indexed = index(new PythonTranslator(FULL), """
                def main():
                    value = 1
                    if value:
                        value = 2
                    return value
                """);

        assertEquals(1, indexed.declarationsNamed("value").size(),
                "in Python the assignment inside if binds the same name");
    }

    @Test
    void declarationNameItselfIsNotCountedAsUsage() {
        Indexed indexed = index(new CppTranslator(FULL), """
                int main() {
                    int unused = 1;
                    return 0;
                }
                """);

        assertTrue(indexed.usagesOf("unused").isEmpty());
    }

    private record Indexed(MeaningTree tree, VariableUsageIndex index) {
        List<Declaration> declarationsNamed(String name) {
            return index.indexedDeclarations().stream()
                    .filter(declaration -> nameOf(declaration).equals(name))
                    .toList();
        }

        Declaration declaration(String name) {
            List<Declaration> found = declarationsNamed(name);
            assertEquals(1, found.size(), "exactly one declaration of " + name);
            return found.get(0);
        }

        List<NodeInfo> usagesOf(String name) {
            return index.usagesOf(declaration(name));
        }

        private static String nameOf(Declaration declaration) {
            if (declaration instanceof VariableDeclaration variable) {
                return variable.getFirstDeclarator() == null
                        ? "" : variable.getFirstDeclarator().getIdentifier().getName();
            }
            if (declaration instanceof DeclarationArgument argument) {
                return argument.getName().getName();
            }
            return "";
        }
    }

    /**
     * Таблица строится отдельным проходом, а не берётся у разбора: индекс обязан работать на
     * любом дереве, а заодно так проверяется его запасной путь — поиск имени в таблице, когда
     * в кадрах его нет.
     */
    private static Indexed index(LanguageTranslator translator, String source) {
        MeaningTree tree = translator.getMeaningTree(source);
        ScopeTable scope = ScopeTableBuilder.build(tree, translator.getLanguageBehavior());
        VariableUsageIndex index = new VariableUsageIndex(
                tree, scope, translator.getLanguageBehavior().scopePolicy()).build();
        return new Indexed(tree, index);
    }

    @Test
    void resolvingAUseReturnsItsDeclaration() {
        Indexed indexed = index(new CppTranslator(FULL), """
                int main() {
                    int a = 1;
                    return a;
                }
                """);

        Declaration declaration = indexed.declaration("a");
        NodeInfo use = indexed.usagesOf("a").get(0);
        Optional<Declaration> resolved = indexed.index.declarationOf(use);

        assertNotNull(use.node());
        assertTrue(use.node() instanceof SimpleIdentifier);
        assertTrue(resolved.isPresent());
        assertSame(declaration, resolved.get());
    }
}
