package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.List;

public class StaticImportMembersFromModule extends ImportMembersFromModule {

    public StaticImportMembersFromModule(Identifier scope, List<Identifier> members) {
        super(scope, members);
    }

    public StaticImportMembersFromModule(Identifier scope, Identifier... members) {
        super(scope, members);
    }
}
