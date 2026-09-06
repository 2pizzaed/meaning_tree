package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;

/**
 * Параметр без имени: {@code double f(double);}.
 * <p>
 * В прототипе C/C++ имя параметра необязательно — обращаться к нему там негде. В языках, где
 * функция объявляется вместе с телом, такого объявления не бывает вовсе: параметр без имени
 * невыразим, а подставить выдуманное имя значит написать в выводе то, чего в исходнике не было.
 * Поэтому отказ.
 */
public class UnnamedParameterFeature extends SemanticFeature {
    @Override
    public String id() {
        return "feature-unnamed-parameter";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return node instanceof DeclarationArgument argument && !argument.hasName();
    }

    @Override
    public String description(Node node) {
        return "Function parameter without a name has no counterpart in this language";
    }
}
