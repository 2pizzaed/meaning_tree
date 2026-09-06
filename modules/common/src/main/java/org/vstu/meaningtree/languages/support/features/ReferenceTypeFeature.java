package org.vstu.meaningtree.languages.support.features;

import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.types.builtin.ReferenceType;

import java.util.function.BooleanSupplier;

/**
 * Ссылочный тип в любом объявлении: переменной, аргумента функции или возвращаемого значения.
 * Парный к {@link PointerTypeFeature} запрет для языков, где ссылки нельзя выразить — прежде
 * всего для Си, у которого из двух видов косвенности есть только указатель.
 * <p>
 * В отличие от указателя, ссылка запрещена не всегда, а по настройке языка: тот же C++-вьювер
 * печатает ссылки в режиме C++ и не может в режиме Си. Поэтому условие передаётся замыканием и
 * спрашивается в момент проверки: набор запретов регистрируется конструктором вьювера, а
 * конфигурация доходит до компонента позже — в {@code LanguageTranslator.init()}, — и прочитать
 * её при регистрации нельзя.
 */
public class ReferenceTypeFeature extends SemanticFeature {
    private final BooleanSupplier unsupported;

    public ReferenceTypeFeature() {
        this(() -> true);
    }

    public ReferenceTypeFeature(BooleanSupplier unsupported) {
        this.unsupported = unsupported;
    }

    @Override
    public String id() {
        return "feature-reference-type";
    }

    @Override
    public boolean matches(Node node, FeatureContext featureContext) {
        return node instanceof ReferenceType && unsupported.getAsBoolean();
    }

    @Override
    public String description(Node node) {
        return "Reference type is not supported";
    }
}
