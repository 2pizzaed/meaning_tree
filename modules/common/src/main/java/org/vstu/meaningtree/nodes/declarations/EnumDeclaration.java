package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class EnumDeclaration extends Declaration {
    @TreeNode
    protected Identifier name;

    @TreeNode
    protected Set<Identifier> constants;

    // отдельный словарь, чтобы исключить выражения из итерации по узлам
    protected Map<Identifier, Expression> constantsValues;


    public Set<Identifier> getConstants() {
        return constants;
    }

    public boolean hasConstant(Identifier identifier) {
        return constants.contains(identifier);
    }

    public Expression getConstant(Identifier identifier) {
        return constantsValues.getOrDefault(identifier, null);
    }

    public Identifier getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EnumDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(name, nodeInfos.name) && Objects.equals(constants, nodeInfos.constants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, constants);
    }

    public EnumDeclaration clone() {
        var clone = (EnumDeclaration) super.clone();
        clone.name = this.name.clone();
        clone.constants = this.constants.stream().map(Identifier::clone).collect(Collectors.toSet());
        clone.constantsValues = this.constantsValues.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return clone;
    }
}
