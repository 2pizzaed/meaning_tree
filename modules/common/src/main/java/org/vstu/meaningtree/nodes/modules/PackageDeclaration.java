package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.Objects;

public class PackageDeclaration extends Declaration {
    @TreeNode private Identifier packageName;

    public PackageDeclaration(Identifier packageName) {
        this.packageName = packageName;
    }

    public Identifier getPackageName() {
        return packageName;
    }

    public PackageDeclaration clone() {
        var clone = (PackageDeclaration) super.clone();
        clone.packageName = packageName;
        return clone;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PackageDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(packageName, nodeInfos.packageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), packageName);
    }
}
