package org.vstu.meaningtree.nodes.modules;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;

import java.util.Objects;

public class Include extends Import {
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

    public Include clone() {
        return new Include((StringLiteral) filename.clone(), includeType);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Include nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(filename, nodeInfos.filename) && includeType == nodeInfos.includeType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), filename, includeType);
    }
}
