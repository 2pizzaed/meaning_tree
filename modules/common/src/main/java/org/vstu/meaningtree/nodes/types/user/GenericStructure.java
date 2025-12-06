package org.vstu.meaningtree.nodes.types.user;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.GenericUserType;

public class GenericStructure extends GenericUserType {
    public GenericStructure(Identifier name, Type... templateParameters) {
        super(name, templateParameters);
    }
}
