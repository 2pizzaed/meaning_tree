package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;

public class Include extends Node {
    @TreeNode private StringLiteral filename;

    public enum IncludeType {
        // #include "defs.h"
        QUOTED_FORM,
        // #include <stdio.h>
        POINTY_BRACKETS_FORM,
    }

    private final IncludeType includeType;

    public Include(StringLiteral filename, IncludeType includeType) {
        this.filename = filename;
        this.includeType = includeType;
    }

    public StringLiteral getFileName() {
        return filename;
    }

    public IncludeType getIncludeType() {
        return includeType;
    }
}
