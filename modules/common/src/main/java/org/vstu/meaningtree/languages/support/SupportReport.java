package org.vstu.meaningtree.languages.support;

import org.vstu.meaningtree.exceptions.UnsupportedConversionException;

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

    public void throwAll() {
        StringBuilder builder = new StringBuilder("Given meaning tree has incompatible features listed below:\n");
        for (var issue : issues) {
            String prefix = "[%s]:".formatted(issue.feature() == null ? issue.relatedNode().getNodeUniqueName() :
                    issue.feature().getClass().getSimpleName());
            builder.append("%s %s".formatted(prefix, issue.message())).append("\n");
        }
        if (!issues.isEmpty()) {
            throw new UnsupportedConversionException(builder.toString());
        }
    }
}
