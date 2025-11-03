package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.Objects;

public class ImportAllFromModule extends Import {
    @TreeNode
    private Identifier moduleName;

    public ImportAllFromModule(Identifier moduleName) {
        this.moduleName = moduleName;
    }

    public Identifier getModuleName() {
        return this.moduleName;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ImportAllFromModule nodeInfos = (ImportAllFromModule) o;
        return Objects.equals(moduleName, nodeInfos.moduleName);
    }

    public ImportAllFromModule clone() {
        return new ImportAllFromModule(moduleName.clone());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), moduleName);
    }
}
