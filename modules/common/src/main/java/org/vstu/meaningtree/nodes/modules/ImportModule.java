package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.Objects;

public class ImportModule extends Import {
    @TreeNode
    protected Identifier moduleName;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImportModule nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(moduleName, nodeInfos.moduleName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), moduleName);
    }

    public ImportModule(Identifier moduleName) {
        this.moduleName = moduleName;
    }

    public Identifier getModuleName() {
        return this.moduleName;
    }
}
