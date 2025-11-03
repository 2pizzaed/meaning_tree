package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.List;
import java.util.Objects;

public class ImportMembersFromModule extends ImportModule {
    @TreeNode private List<Identifier> members;

    public ImportMembersFromModule(Identifier moduleName, List<Identifier> members) {
        super(moduleName);
        this.members = List.copyOf(members);
    }

    public ImportMembersFromModule(Identifier scope, Identifier... members) {
        this(scope, List.of(members));
    }

    public List<Identifier> getMembers() {
        return members;
    }

    public ImportMembersFromModule clone() {
        return new ImportMembersFromModule(getModuleName().clone(), members.stream().map(Identifier::clone).toList());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ImportMembersFromModule nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(members, nodeInfos.members);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), members);
    }
}
