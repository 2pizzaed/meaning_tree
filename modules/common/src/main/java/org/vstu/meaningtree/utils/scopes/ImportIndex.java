package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.modules.Import;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

class ImportIndex implements Serializable {
    @NotNull
    private final Set<Import> imports = new HashSet<>();

    public void registerImport(@NotNull Import importDeclaration) {
        imports.add(importDeclaration);
    }

    public Set<Import> allImports() {
        return Set.copyOf(imports);
    }
}
