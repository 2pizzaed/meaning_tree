package org.vstu.meaningtree.languages.support.features;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.SemanticFeature;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.ScopeDeclarationStatement;

/**
 * Объявление имени связанным в объемлющей функции: python-{@code nonlocal}. В Java и C++
 * такой формы нет — вложенная функция там либо не имеет доступа к именам объемлющей вовсе,
 * либо захватывает их замыканием, где выбор «читать или писать» делается при захвате, а не
 * отдельным объявлением.
 * <p>
 * В отличие от {@code global}, снять эту конструкцию нечем: {@code global} в C-семействе
 * ничего не меняет, потому что присваивание и так пишет во внешнее имя, а {@code nonlocal}
 * указывает на промежуточную область, которой соответствовать нечему.
 */
public class NonlocalBindingFeature extends SemanticFeature {
    @Override
    public String id() {
        return "nonlocal-binding";
    }

    @Override
    public boolean matches(Node node, @Nullable FeatureContext context) {
        return node instanceof ScopeDeclarationStatement stmt
                && stmt.getKind() == ScopeDeclarationStatement.Kind.NONLOCAL;
    }

    @Override
    public String description(Node node) {
        return "Declaring a name bound in an enclosing function (nonlocal) is not supported by this language";
    }
}
