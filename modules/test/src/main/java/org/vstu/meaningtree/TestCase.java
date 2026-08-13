package org.vstu.meaningtree;

import com.google.gson.JsonObject;
import org.vstu.meaningtree.exceptions.MeaningTreeException;

import java.util.*;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestCase {
    private static final Pattern CASE_HEADER = Pattern.compile("(?m)^\\h*case(?:\\[([^\\]]*)])?:\\s*(\\S+)\\h*$");
    private static final Pattern IGNORE_DIRECTIVE = Pattern.compile("(?m)^\\h*ignore\\h+(\\S+)\\h*->\\h*(\\S+)\\h*;\\h*$");

    private final String _name;
    private final JsonObject _configuration;
    private final Set<ConversionDirection> _ignoredConversions;
    private List<TestCodeGroup> _codeGroups; // Группы, состоящие из альтернатив кода на одном языке
    private SingleTestCode _mainCode; // Основной язык, если установлен, то все преобразования производятся от него

    public TestCase(String testCase, JsonObject groupConfiguration) {
        _name = parseName(testCase);
        _configuration = TestCaseOptions.mergeConfiguration(groupConfiguration, parseConfiguration(testCase));
        _ignoredConversions = parseIgnoredConversions(testCase);
        parseCodes(IGNORE_DIRECTIVE.matcher(testCase).replaceAll(""));
    }

    private String parseName(String testCase) {
        Matcher nameMatcher = CASE_HEADER.matcher(testCase);

        if (!nameMatcher.find()) {
            throw new IllegalArgumentException("Имя тест-кейса не найдено!");
        }
        return nameMatcher.group(2);
    }

    private JsonObject parseConfiguration(String testCase) {
        Matcher header = CASE_HEADER.matcher(testCase);
        if (!header.find()) {
            throw new IllegalArgumentException("Заголовок тест-кейса не найден!");
        }
        return TestCaseOptions.parseConfiguration(header.group(1), header.group());
    }

    private Set<ConversionDirection> parseIgnoredConversions(String testCase) {
        Matcher directive = IGNORE_DIRECTIVE.matcher(testCase);
        Set<ConversionDirection> ignored = new HashSet<>();
        while (directive.find()) {
            ignored.add(new ConversionDirection(directive.group(1), directive.group(2)));
        }
        return ignored;
    }

    private void parseCodes(String testCase) {
        Pattern langNamePattern = Pattern.compile("^([ \\t\\f\\r]+)((main|alt|isolated)\\s+)?[^\\s]+:\\s*$", Pattern.MULTILINE);
        Matcher matcher = langNamePattern.matcher(testCase);

        // Найти строки с названиями языков
        ArrayList<MatchResult> results = matcher.results().collect(Collectors.toCollection(ArrayList::new));
        // Определить отступ
        String langNameIndent = results.getFirst().group().replace(results.getFirst().group().strip(), "");
        ArrayList<Integer> codesStarts = results.stream()
                .filter(match -> match.group().replace(match.group().strip(), "").equals(langNameIndent))
                .map(MatchResult::start)
                .collect(Collectors.toCollection(ArrayList::new));

        codesStarts.add(testCase.length());

        // Вычленить всё что начинается названием языка включительно
        // и кончается названием другого языка не включительно
        List<SingleTestCode> codes = new ArrayList<>();
        HashMap<String, TestCodeGroup> alternatives = new HashMap<>();
        SingleTestCode mainCode = null;

        for (int i = 0; i < codesStarts.size() - 1; i++) {
            String code = testCase.substring(codesStarts.get(i), codesStarts.get(i + 1));
            SingleTestCode testCode = new SingleTestCode(code);
            if (mainCode != null && testCode.getType().equals(TestCodeType.MAIN)) {
                throw new MeaningTreeException("В тест кейсе несколько главных кодов:\n" + testCase);
            }
            if (testCode.getType().equals(TestCodeType.MAIN)) {
                mainCode = testCode;
            } else if (testCode.getType().equals(TestCodeType.ALTERNATIVE)) {
                if (!alternatives.containsKey(testCode.getLanguage())) {
                    alternatives.put(testCode.getLanguage(), new TestCodeGroup(testCode.getLanguage(), testCode));
                } else {
                    alternatives.get(testCode.getLanguage()).add(testCode);
                }
            } else {
                if (alternatives.containsKey(testCode.getLanguage())) {
                    throw new MeaningTreeException("В тест кейсе язык " + testCode.getLanguage() + " должен состоять только из альтернатив");
                }
                codes.add(testCode);
            }
        }
        _codeGroups = new ArrayList<>();
        _codeGroups.addAll(alternatives.values());
        for (SingleTestCode code : codes) {
            _codeGroups.add(new TestCodeGroup(code.getLanguage(), code));
        }
        _mainCode = mainCode;
    }

    public String getName() { return _name; }

    public JsonObject getConfiguration() {
        return _configuration.deepCopy();
    }

    public boolean ignoresConversion(String sourceLanguage, String targetLanguage) {
        return _ignoredConversions.contains(new ConversionDirection(sourceLanguage, targetLanguage));
    }

    public List<TestCodeGroup> getCodeGroups() {
        return new ArrayList<>(_codeGroups);
    }

    public SingleTestCode getMainCode() {
        return _mainCode;
    }

    public boolean hasMainCode() {
        return _mainCode != null;
    }

    public String[] getLanguages() {
        return _codeGroups.stream().flatMap(Collection::stream).map(SingleTestCode::getLanguage).distinct().toArray(String[]::new);
    }

    private record ConversionDirection(String sourceLanguage, String targetLanguage) {
    }
}
