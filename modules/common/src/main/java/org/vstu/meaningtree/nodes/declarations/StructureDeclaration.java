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
        super(modifiers, name, List.of(), new Structure((Identifier) name.freshClone()), parents);
    }

    public StructureDeclaration(List<DeclarationModifier> modifiers, Identifier name, List<Type> typeParameters, Type... parents) {
        super(modifiers, name, typeParameters,
                typeParameters.isEmpty()
                        ? new Structure((Identifier) name.freshClone())
                        : new GenericStructure((Identifier) name.freshClone(), cloneTypes(typeParameters)),
                parents);
    }

    protected StructureDeclaration(List<DeclarationModifier> modifiers,
                                   Identifier name,
                                   List<Type> typeParameters,
                                   UserType typeNode,
                                   Type... parents) {
        super(modifiers, name, typeParameters, typeNode, parents);
    }

    public static StructureDeclaration withTypeNode(List<DeclarationModifier> modifiers,
                                                    Identifier name,
                                                    List<Type> typeParameters,
                                                    UserType typeNode,
                                                    Type... parents) {
        return new StructureDeclaration(modifiers, name, typeParameters, typeNode, parents);
    }

    public UserType getTypeNode() {
        return typeNode;
    }
}
