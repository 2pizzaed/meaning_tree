package org.vstu.meaningtree.languages.support.features;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.statements.loops.control.GotoStatement;

public class StatementJumpFeature extends SemanticFeature {
    @Override
    public String id() {
        return "statement-jump";
    }

    @Override
    public boolean matches(Node node, @Nullable FeatureContext context) {
        return node instanceof Statement stmt && stmt.getJumpLabel() != null || node instanceof GotoStatement;
    }

    @Override
    public String description(Node node) {
        return "Jump statement is not supported by this language";
    }
}
