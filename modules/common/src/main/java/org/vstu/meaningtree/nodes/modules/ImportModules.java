package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.List;
import java.util.Objects;

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

    public ImportModules clone() {
        return new ImportModules(modulesNames.stream().map(Identifier::clone).toList());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImportModules nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(modulesNames, nodeInfos.modulesNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), modulesNames);
    }
}
