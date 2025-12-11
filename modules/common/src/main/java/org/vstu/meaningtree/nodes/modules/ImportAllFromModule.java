package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.nodes.expressions.Identifier;

public class ImportAllFromModule extends ImportModule {

    public ImportAllFromModule(Identifier moduleName) {
        super(moduleName);
    }

    public Identifier getModuleName() {
        return this.moduleName;
    }

    public ImportAllFromModule clone() {
        return new ImportAllFromModule(moduleName.clone());
    }
}
