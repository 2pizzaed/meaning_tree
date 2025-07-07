package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

public class ImportModule extends Import {
    @TreeNode
    private Identifier moduleName;

    public ImportModule(Identifier moduleName) {
        this.moduleName = moduleName;
    }

    public Identifier getModuleName() {
        return this.moduleName;
    }
}
