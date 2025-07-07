package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.List;

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
}
