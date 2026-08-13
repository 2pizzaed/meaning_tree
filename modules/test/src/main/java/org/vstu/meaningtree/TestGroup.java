package org.vstu.meaningtree;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TestGroup {
    private static final Pattern CASE_HEADER = Pattern.compile("(?m)^\\h*case(?:\\[([^\\]]*)])?:\\s*(\\S+)\\h*$");

    private final String _name;
    private final JsonObject _configuration;
    private final ArrayList<TestCase> _testCases;

    public TestGroup(String testGroup) {
        _name = parseName(testGroup);
        _configuration = parseConfiguration(testGroup);
        _testCases = parseCases(testGroup);
    }

    private String parseName(String testGroup) {
        Matcher nameMatcher = TestsParser.GROUP_HEADER.matcher(testGroup);

        if (!nameMatcher.find()) {
            throw new IllegalArgumentException("Имя группы тестов не найдено!");
        }
        return nameMatcher.group(2);
    }

    private JsonObject parseConfiguration(String testGroup) {
        Matcher header = TestsParser.GROUP_HEADER.matcher(testGroup);
        if (!header.find()) {
            throw new IllegalArgumentException("Заголовок группы тестов не найден!");
        }
        return TestCaseOptions.parseConfiguration(header.group(1), header.group());
    }

    private ArrayList<TestCase> parseCases(String testGroup) {
        Matcher caseMatcher = CASE_HEADER.matcher(testGroup);
        ArrayList<MatchResult> headers = caseMatcher.results().collect(Collectors.toCollection(ArrayList::new));
        ArrayList<TestCase> cases = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            int end = i + 1 < headers.size() ? headers.get(i + 1).start() : testGroup.length();
            cases.add(new TestCase(testGroup.substring(headers.get(i).start(), end), _configuration));
        }
        return cases;
    }


    public String getName() { return _name; }

    public TestCase[] getCases() { return _testCases.toArray(TestCase[]::new); }
}
