package org.vstu.meaningtree.languages.support.features;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.languages.helpers.GeneratorLowerer;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.definitions.GeneratorDefinition;

/**
 * Генератор, форму которого не удалось перевести в класс-итератор.
 * <p>
 * Правило срабатывает на генераторе, доживший до анализа поддержки: в языке без генераторов
 * понижение проходит до отрисовки и убирает из дерева всё, что смогло перевести, поэтому
 * оставшийся {@link GeneratorDefinition} — это ровно тот случай, который переводу не поддался.
 * Причину называет сам разбор формы, чтобы отказ говорил, что именно помешало, а не просто
 * «генераторы не поддерживаются».
 */
public class UnloweredGeneratorFeature extends SemanticFeature {
    @Override
    public String id() {
        return "unlowered-generator";
    }

    @Override
    public boolean matches(Node node, @Nullable FeatureContext context) {
        return node instanceof GeneratorDefinition;
    }

    @Override
    public String description(Node node) {
        if (!(node instanceof GeneratorDefinition generator)) {
            return "Generator cannot be converted to an iterator class";
        }
        String reason = GeneratorLowerer.unsupportedReason(generator);
        return "Generator `%s` cannot be converted to an iterator class: %s".formatted(
                generator.getName(), reason == null ? "unrecognized form" : reason);
    }
}
