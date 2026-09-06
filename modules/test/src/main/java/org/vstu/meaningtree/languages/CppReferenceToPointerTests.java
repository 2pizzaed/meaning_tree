package org.vstu.meaningtree.languages;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.exceptions.UnsupportedConversionException;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Настройка {@code representReferencesAsPointers}: ссылка выражается указателем целиком —
 * объявление, каждое использование и место вызова.
 */
class CppReferenceToPointerTests {
    @Test
    void parameterBecomesPointerAndItsUsesAreDereferenced() {
        String generated = translate("void inc(int& a) { a = a + 1; }", cpp(true));

        assertTrue(generated.contains("void inc(int * a)"), generated);
        assertTrue(generated.contains("*a = *a + 1;"), generated);
        assertFalse(generated.contains("&"), generated);
    }

    @Test
    void callSitePassesAddressForReferenceParameter() {
        String generated = translate("""
                void inc(int& a) { a = a + 1; }
                int main() {
                    int v = 1;
                    inc(v);
                    return v;
                }
                """, cpp(true));

        assertTrue(generated.contains("inc(&v);"), generated);
    }

    /**
     * Ссылочная переменная, переданная в ссылочный параметр: разыменование использования и
     * взятие адреса в позиции аргумента гасят друг друга, и остаётся сам указатель.
     */
    @Test
    void referenceArgumentInReferencePositionIsPassedAsIs() {
        String generated = translate("""
                void inc(int& a) { a = a + 1; }
                void twice(int& b) { inc(b); inc(b); }
                """, cpp(true));

        assertTrue(generated.contains("inc(b);"), generated);
        assertFalse(generated.contains("inc(&*b)"), generated);
    }

    @Test
    void localReferenceBecomesPointerBoundToAddress() {
        String generated = translate("""
                int main() {
                    int v = 1;
                    int& r = v;
                    r = 5;
                    return r;
                }
                """, cpp(true));

        assertTrue(generated.contains("int * r = &v;"), generated);
        assertTrue(generated.contains("*r = 5;"), generated);
        assertTrue(generated.contains("return *r;"), generated);
    }

    @Test
    void constReferenceKeepsConstness() {
        String generated = translate("""
                int main() {
                    int v = 1;
                    const int& cr = v;
                    return cr;
                }
                """, cpp(true));

        assertTrue(generated.contains("const int * cr = &v;"), generated);
        assertTrue(generated.contains("return *cr;"), generated);
    }

    @Test
    void memberAccessThroughReferenceUsesArrow() {
        String generated = translate("""
                struct P { int f; };
                void use(P& p) { p.f = 2; }
                """, cpp(true));

        assertTrue(generated.contains("p->f = 2;"), generated);
        assertFalse(generated.contains("(*p)"), generated);
    }

    @Test
    void referenceReturnTypeBecomesPointer() {
        String generated = translate("int& pick(int& x, int& y) { return x; }", cpp(true));

        assertTrue(generated.contains("int * pick(int * x, int * y)"), generated);
        assertTrue(generated.contains("return x;"), generated);
    }

    @Test
    void returnOfNonReferenceValueTakesItsAddress() {
        String generated = translate("int& first(int values[]) { return values[0]; }", cpp(true));

        assertTrue(generated.contains("int * first("), generated);
        assertTrue(generated.contains("return &values[0];"), generated);
    }

    /**
     * Расхождение одноимённых объявлений о ссылочных позициях: какое из них вызывается, проход
     * не знает, поэтому место вызова остаётся человеку, а не переписывается наугад.
     */
    @Test
    void ambiguousOverloadLeavesCallSiteAlone() {
        String generated = translate("""
                void put(int& a) { a = 1; }
                void put(double b) { b = 1; }
                int main() {
                    int v = 0;
                    put(v);
                    return v;
                }
                """, cpp(true));

        assertTrue(generated.contains("put(v);"), generated);
    }

    @Test
    void callToFunctionDeclaredElsewhereIsLeftAlone() {
        String generated = translate("""
                int main() {
                    int v = 0;
                    external(v);
                    return v;
                }
                """, cpp(true));

        assertTrue(generated.contains("external(v);"), generated);
    }

    @Test
    void referencesSurviveInCppWhenSettingIsOff() {
        String generated = translate("void inc(int& a) { a = a + 1; }", cpp(false));

        assertTrue(generated.contains("void inc(int & a)"), generated);
        assertTrue(generated.contains("a = a + 1;"), generated);
    }

    @Test
    void settingWorksInCModeAsWell() {
        String generated = translate("""
                void inc(int& a) { a = a + 1; }
                int main() {
                    int v = 1;
                    inc(v);
                    return v;
                }
                """, c(true));

        assertTrue(generated.contains("void inc(int * a)"), generated);
        assertTrue(generated.contains("*a = *a + 1;"), generated);
        assertTrue(generated.contains("inc(&v);"), generated);
    }

    @Test
    void cModeRejectsReferencesWhenSettingIsOff() {
        assertThrows(UnsupportedConversionException.class,
                () -> translate("void inc(int& a) { a = a + 1; }", c(false)));
    }

    @Test
    void codeWithoutReferencesIsUnaffectedBySetting() {
        String source = """
                int twice(int a) { return a * 2; }
                int main() {
                    int v = 1;
                    return twice(v);
                }
                """;

        assertTrue(assertDoesNotThrow(() -> translate(source, cpp(true))).contains("return twice(v);"));
        assertTrue(assertDoesNotThrow(() -> translate(source, c(true))).contains("return twice(v);"));
    }

    private static Map<String, Object> cpp(boolean referencesAsPointers) {
        return config(false, referencesAsPointers);
    }

    private static Map<String, Object> c(boolean referencesAsPointers) {
        return config(true, referencesAsPointers);
    }

    private static Map<String, Object> config(boolean cMode, boolean referencesAsPointers) {
        Map<String, Object> config = new HashMap<>();
        config.put("translationUnitMode", "full");
        config.put("skipErrors", false);
        config.put("preferC", cMode);
        config.put("representReferencesAsPointers", referencesAsPointers);
        return config;
    }

    private static String translate(String source, Map<String, Object> config) {
        CppTranslator translator = new CppTranslator(config);
        return translator.getCode(translator.getMeaningTree(source));
    }
}
