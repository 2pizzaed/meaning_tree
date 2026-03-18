package org.vstu.meaningtree.languages.support;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Node;

import java.util.Objects;

public final class SupportIssue {
    private final String language;
    private final Node node;
    private final FeatureSupport feature;

    public SupportIssue(String language,
                        Node node,
                        @Nullable FeatureSupport feature) {
        this.language = Objects.requireNonNull(language, "Language must not be null");
        this.node = Objects.requireNonNull(node, "Meaning Tree node must not be null");
        this.feature = feature;
    }

    public String language() {
        return language;
    }

    public Node relatedNode() {
        return node;
    }

    public boolean requiresThrow() {
        return feature.issueLevel() != FeatureIssueLevel.WARNING;
    }

    @Nullable
    public FeatureSupport feature() {
        return feature;
    }

    public boolean isSemanticFeatureIssue() {
        return feature != null;
    }

    public String featureId() {
        return feature == null ? "unsupported-node" : feature.id();
    }

    public String message() {
        if (isSemanticFeatureIssue()) {
            return "Feature %s (in node with id: %d) is unsupported in %s: %s".formatted(feature.id(), node.getId(), language, feature.description(node));
        } else {
            return "Unsupported node %s (id: %d) in %s".formatted(node.getNodeUniqueName(), node.getId(), language);
        }
    }
}
