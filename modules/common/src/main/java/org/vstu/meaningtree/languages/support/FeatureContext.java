package org.vstu.meaningtree.languages.support;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.TranslatorComponent;
import org.vstu.meaningtree.nodes.Node;

public record FeatureContext(@NotNull TranslatorComponent component, @Nullable MeaningTree meaningTree,
                             @Nullable NodeInfo nodeInfo, @NotNull Node node, boolean checkNodeIsRegistered) {
}
