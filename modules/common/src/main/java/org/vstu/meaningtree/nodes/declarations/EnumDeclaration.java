package org.vstu.meaningtree.nodes.declarations;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
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
    protected Set<Identifier> constants;

    // отдельный словарь, чтобы исключить выражения из итерации по узлам
    protected Map<Identifier, Expression> constantsValues;

    @TreeNode
    protected Enum typeNode;

    /**
     * Ограничена ли область видимости констант именем перечисления. Java и Python всегда
     * требуют квалификации ({@code Color.RED}), в C++ так ведет себя только {@code enum class},
     * а обычный {@code enum} выносит константы в окружающую область видимости.
     */
    protected boolean scoped;

    /**
     * @param constants упорядоченное отображение константы в ее значение; значение может быть
     *                  {@code null}, если в исходном коде оно не задано явно
     */
    public EnumDeclaration(List<DeclarationModifier> modifiers,
                           Identifier name,
                           Map<Identifier, Expression> constants,
                           boolean scoped) {
        this(modifiers, name, constants, scoped, new Enum((Identifier) name.freshClone()));
    }

    public EnumDeclaration(List<DeclarationModifier> modifiers,
                           Identifier name,
                           Map<Identifier, Expression> constants) {
        this(modifiers, name, constants, true);
    }

    protected EnumDeclaration(List<DeclarationModifier> modifiers,
                              Identifier name,
                              Map<Identifier, Expression> constants,
                              boolean scoped,
                              Enum typeNode) {
        this.modifiers = List.copyOf(modifiers);
        this.name = name;
        this.constants = new LinkedHashSet<>(constants.keySet());
        this.constantsValues = new LinkedHashMap<>(constants);
        this.scoped = scoped;
        this.typeNode = typeNode;
    }

    public static EnumDeclaration withTypeNode(List<DeclarationModifier> modifiers,
                                               Identifier name,
                                               Map<Identifier, Expression> constants,
                                               boolean scoped,
                                               Enum typeNode) {
        return new EnumDeclaration(modifiers, name, constants, scoped, typeNode);
    }

    public Set<Identifier> getConstants() {
        return constants;
    }

    public boolean hasConstant(Identifier identifier) {
        return constants.contains(identifier);
    }

    /**
     * @return явное значение константы или {@code null}, если его нет или константа неизвестна
     */
    @Nullable
    public Expression getConstant(Identifier identifier) {
        return constantsValues.getOrDefault(identifier, null);
    }

    /**
     * @return упорядоченное отображение константы в ее значение, значения могут быть {@code null}
     */
    public Map<Identifier, Expression> getConstantsWithValues() {
        return new LinkedHashMap<>(constantsValues);
    }

    public boolean hasConstantValues() {
        return constantsValues.values().stream().anyMatch(Objects::nonNull);
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
                && Objects.equals(constantsValues, nodeInfos.constantsValues)
                && Objects.equals(typeNode, nodeInfos.typeNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, constants, constantsValues, typeNode, scoped);
    }

    public EnumDeclaration clone() {
        var clone = (EnumDeclaration) super.clone();
        clone.name = this.name.clone();
        clone.typeNode = (Enum) this.typeNode.clone();
        // константы и их значения клонируются одним проходом, чтобы ключи словаря остались
        // теми же объектами, что лежат в множестве констант
        clone.constants = new LinkedHashSet<>();
        clone.constantsValues = new LinkedHashMap<>();
        for (Identifier constant : this.constants) {
            Identifier clonedConstant = constant.clone();
            Expression value = this.constantsValues.get(constant);
            clone.constants.add(clonedConstant);
            clone.constantsValues.put(clonedConstant, value == null ? null : value.clone());
        }
        return clone;
    }
}
