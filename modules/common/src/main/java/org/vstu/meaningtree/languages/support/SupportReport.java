package org.vstu.meaningtree.languages.support;

import java.util.ArrayList;
import java.util.List;

public final class SupportReport {
    private final List<SupportIssue> issues;

    public SupportReport(List<SupportIssue> issues) {
        this.issues = List.copyOf(issues == null ? new ArrayList<>() : issues);
    }

    public boolean isSupported() {
        return issues.isEmpty();
    }

    public List<SupportIssue> issues() {
        return issues;
    }

    public SupportIssue firstIssueOrNull() {
        return issues.isEmpty() ? null : issues.getFirst();
    }
}
