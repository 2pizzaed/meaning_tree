package org.vstu.meaningtree.languages.support;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.TranslatorComponent;
import org.vstu.meaningtree.nodes.Node;

import java.util.Objects;

public record FeatureContext(TranslatorComponent component, @Nullable MeaningTree meaningTree,
                             @Nullable NodeInfo nodeInfo, Node node) {
    public FeatureContext(TranslatorComponent component, @Nullable MeaningTree meaningTree, @Nullable NodeInfo nodeInfo, Node node) {
        this.meaningTree = meaningTree;
        this.nodeInfo = nodeInfo;
        this.node = Objects.requireNonNull(node, "node cannot be null");
        this.component = Objects.requireNonNull(component, "translator component cannot be null");
    }
}
