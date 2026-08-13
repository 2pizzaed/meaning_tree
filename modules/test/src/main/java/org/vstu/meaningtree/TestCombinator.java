package org.vstu.meaningtree;

import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.List;

public class TestCombinator {
    public static List<ImmutablePair<TestCodeGroup, TestCodeGroup>> getPairs(TestCase testCase) {
        List<ImmutablePair<TestCodeGroup, TestCodeGroup>> result;

        boolean hasMain = testCase.hasMainCode();

        if (hasMain) {
            // Для каждой группы кодов в тест кейсе сделать пару {язык, код}
            result = new ArrayList<>();
            for (TestCodeGroup code : testCase.getCodeGroups()) {
                result.add(new ImmutablePair<>(new TestCodeGroup(testCase.getMainCode().getLanguage(), testCase.getMainCode()), code));
            }
        } else {
            Combinator<TestCodeGroup> comb = new Combinator<>(testCase.getCodeGroups());
            result = new ArrayList<>(comb.getPermutations());
        }

        // Убираем запрещенные условия тестирования
        result.removeIf(codePair -> {
            TestCodeGroup left = codePair.left;
            TestCodeGroup right = codePair.right;
            // сравнение двух групп альтернатив
            return left.size() > 1 && right.size() > 1
                    // альтернативы не должны преобразовываться в другой язык (бессмысленно)
                    || right.size() > 1 && left.size() == 1 && !hasMain
                    // статический блок кода не должен подвергаться преобразованию в другой язык
                    || right.size() == 1 && right.getFirst().getType().equals(TestCodeType.ISOLATED) && !hasMain
                    || testCase.ignoresConversion(
                            hasMain ? left.getLanguage() : right.getLanguage(),
                            hasMain ? right.getLanguage() : left.getLanguage()
                    );
        });

        return result;
    }
}
