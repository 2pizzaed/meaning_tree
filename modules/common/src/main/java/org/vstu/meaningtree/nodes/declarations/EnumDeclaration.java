package org.vstu.meaningtree.nodes.declarations;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.user.Enum;

import java.util.*;

/**
 * Перечисление без расширенных возможностей: только имя и упорядоченный список констант,
 * каждая из которых может иметь явное значение. Конструкторы, поля и методы перечисления
 * (Java enum с телом, Python Enum с методами) не поддерживаются.
 */
public class EnumDeclaration extends Declaration {
    @TreeNode
    protected Identifier name;

    @TreeNode
    protected List<EnumConstantDeclaration> constants;

    @TreeNode
    protected Enum typeNode;

    /**
     * Ограничена ли область видимости констант именем перечисления. Java и Python всегда
     * требуют квалификации ({@code Color.RED}), в C++ так ведет себя только {@code enum class},
     * а обычный {@code enum} выносит константы в окружающую область видимости.
     */
    protected boolean scoped;

    /**
     * @param constants константы в порядке исходного кода
     */
    public EnumDeclaration(List<DeclarationModifier> modifiers,
                           Identifier name,
                           List<EnumConstantDeclaration> constants,
                           boolean scoped) {
        this(modifiers, name, constants, scoped, new Enum((Identifier) name.freshClone()));
    }

    public EnumDeclaration(List<DeclarationModifier> modifiers,
                           Identifier name,
                           List<EnumConstantDeclaration> constants) {
        this(modifiers, name, constants, true);
    }

    protected EnumDeclaration(List<DeclarationModifier> modifiers,
                              Identifier name,
                              List<EnumConstantDeclaration> constants,
                              boolean scoped,
                              Enum typeNode) {
        this.modifiers = List.copyOf(modifiers);
        this.name = name;
        this.constants = new ArrayList<>(constants);
        this.scoped = scoped;
        this.typeNode = typeNode;
    }

    public static EnumDeclaration withTypeNode(List<DeclarationModifier> modifiers,
                                               Identifier name,
                                               List<EnumConstantDeclaration> constants,
                                               boolean scoped,
                                               Enum typeNode) {
        return new EnumDeclaration(modifiers, name, constants, scoped, typeNode);
    }

    public List<EnumConstantDeclaration> getConstants() {
        return List.copyOf(constants);
    }

    public boolean hasConstant(Identifier identifier) {
        return getConstant(identifier) != null;
    }

    /**
     * @return объявление константы или {@code null}, если константа неизвестна
     */
    @Nullable
    public EnumConstantDeclaration getConstant(Identifier identifier) {
        return constants.stream()
                .filter(constant -> constant.getName().equals(identifier))
                .findFirst().orElse(null);
    }

    public boolean hasConstantValues() {
        return constants.stream().anyMatch(EnumConstantDeclaration::hasValue);
    }

    public Identifier getName() {
        return name;
    }

    public Enum getTypeNode() {
        return typeNode;
    }

    public boolean isScoped() {
        return scoped;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EnumDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return scoped == nodeInfos.scoped
                && Objects.equals(name, nodeInfos.name)
                && Objects.equals(constants, nodeInfos.constants)
                && Objects.equals(typeNode, nodeInfos.typeNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, constants, typeNode, scoped);
    }

    public EnumDeclaration clone() {
        var clone = (EnumDeclaration) super.clone();
        clone.name = this.name.clone();
        clone.typeNode = (Enum) this.typeNode.clone();
        clone.constants = new ArrayList<>();
        for (EnumConstantDeclaration constant : constants) {
            clone.constants.add(constant.clone());
        }
        return clone;
    }
}
