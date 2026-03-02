package org.vstu.meaningtree.languages.support;

import org.vstu.meaningtree.languages.TranslatorComponent;
import org.vstu.meaningtree.nodes.Node;

import java.lang.reflect.InvocationTargetException;

public abstract class SemanticFeature implements FeatureSupport {
    public SemanticFeature() {}

    public static boolean test(TranslatorComponent component, Class<? extends SemanticFeature> feature, Node node) {
        try {
            SemanticFeature instance = feature.getDeclaredConstructor().newInstance();
            return instance.matches(node);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            return false;
        }
    }
}
