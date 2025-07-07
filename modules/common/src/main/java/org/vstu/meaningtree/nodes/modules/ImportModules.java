package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.List;

public class ImportModules extends Import {
    @TreeNode
    private List<Identifier> modulesNames;

    public ImportModules(List<Identifier> modulesNames) {
        this.modulesNames = List.copyOf(modulesNames);
    }

    public ImportModules(Identifier... members) {
        this(List.of(members));
    }

    public List<Identifier> getModulesNames() {
        return modulesNames;
    }
}
