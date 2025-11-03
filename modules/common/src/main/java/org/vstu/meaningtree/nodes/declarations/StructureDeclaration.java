package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.user.GenericStructure;
import org.vstu.meaningtree.nodes.types.user.Structure;

import java.util.List;

public class StructureDeclaration extends ClassDeclaration {
    public StructureDeclaration(List<DeclarationModifier> modifiers, Identifier name, Type... parents) {
        super(modifiers, name, parents);
    }

    public UserType getTypeNode() {
        if (isGeneric()) {
            return new GenericStructure(name, getTypeParameters().toArray(new Type[0]));
        }
        return new Structure(name);
    }
}
