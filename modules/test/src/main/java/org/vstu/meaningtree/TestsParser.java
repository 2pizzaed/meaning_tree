package org.vstu.meaningtree;

import java.util.ArrayList;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestsParser {
    static final Pattern GROUP_HEADER = Pattern.compile("(?m)^group(?:\\[([^\\]]*)])?:\\s*(\\w+)\\s*$");

    public static TestGroup[] parse(String tests) {
        Matcher groupMatcher = GROUP_HEADER.matcher(tests);
        ArrayList<TestGroup> testGroups = new ArrayList<>();

        ArrayList<MatchResult> groups = groupMatcher.results().collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (int i = 0; i < groups.size(); i++) {
            int end = i + 1 < groups.size() ? groups.get(i + 1).start() : tests.length();
            testGroups.add(new TestGroup(tests.substring(groups.get(i).start(), end)));
        }

        return testGroups.toArray(TestGroup[]::new);
    }
}
