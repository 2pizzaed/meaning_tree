package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.Objects;

public class Alias extends Identifier {
    @TreeNode private Identifier realName;
    @TreeNode private SimpleIdentifier alias;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Alias nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(realName, nodeInfos.realName) && Objects.equals(alias, nodeInfos.alias);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), realName, alias);
    }

    public Alias clone() {
        return new Alias(realName.clone(), alias.clone());
    }

    public Alias(Identifier realName, SimpleIdentifier alias) {
        this.realName = realName;
        this.alias = alias;
    }

    public Identifier getRealName() {
        return realName;
    }

    public SimpleIdentifier getAlias() {
        return alias;
    }

    @Override
    public boolean contains(Identifier other) {
        return alias.equals(other) || realName.contains(other);
    }

    @Override
    public int contentSize() {
        return realName.contentSize() + 1;
    }

    @Override
    public String internalRepresentation() {
        return alias.internalRepresentation();
    }
}
