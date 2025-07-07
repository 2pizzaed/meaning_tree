package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

public class ImportAllFromModule extends Import {
    @TreeNode
    private Identifier moduleName;

    public ImportAllFromModule(Identifier moduleName) {
        this.moduleName = moduleName;
    }

    public Identifier getModuleName() {
        return this.moduleName;
    }
}
