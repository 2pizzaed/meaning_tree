package org.vstu.meaningtree.languages.support.features;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.Loop;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;

public class LabeledLoopFeature extends SemanticFeature {
    @Override
    public String id() {
        return "labeled-loop";
    }

    @Override
    public boolean matches(Node node, @Nullable FeatureContext context) {
        return node instanceof Loop loop && loop.getJumpLabel() != null
                || node instanceof ContinueStatement continueStmt && continueStmt.getJumpLabel() != null
                || node instanceof BreakStatement breakStmt && breakStmt.getJumpLabel() != null
                ;
    }

    @Override
    public String description(Node node) {
        return "Labeled loops (instead gotos) is not supported by this language";
    }
}
