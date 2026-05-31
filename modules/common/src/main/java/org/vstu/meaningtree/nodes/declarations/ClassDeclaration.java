package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.user.Class;
import org.vstu.meaningtree.nodes.types.user.GenericClass;

import java.util.List;
import java.util.Objects;


public class ClassDeclaration extends Declaration {
    @TreeNode protected Identifier name;
    @TreeNode protected List<Type> parentTypes;
    @TreeNode protected List<Type> typeParameters; // for generic type
    @TreeNode protected UserType typeNode;

    public ClassDeclaration(List<DeclarationModifier> modifiers, Identifier name, List<Type> typeParameters, Type ... parents) {
        this(modifiers, name, typeParameters,
                typeParameters.isEmpty()
                        ? new Class(name)
                        : new GenericClass(name, typeParameters.toArray(new Type[0])),
                parents);
    }

    protected ClassDeclaration(List<DeclarationModifier> modifiers,
                               Identifier name,
                               List<Type> typeParameters,
                               UserType typeNode,
                               Type ... parents) {
        this.modifiers = List.copyOf(modifiers);
        this.name = name;
        this.typeParameters = List.copyOf(typeParameters);
        this.typeNode = typeNode;
        parentTypes = List.of(parents);
    }

    public static ClassDeclaration withTypeNode(List<DeclarationModifier> modifiers,
                                                Identifier name,
                                                List<Type> typeParameters,
                                                UserType typeNode,
                                                Type ... parents) {
        return new ClassDeclaration(modifiers, name, typeParameters, typeNode, parents);
    }

    public ClassDeclaration(List<DeclarationModifier> modifiers, Identifier name, Type ... parents) {
        this(modifiers, name, List.of(), parents);
    }

    public ClassDeclaration(Identifier name, List<Type> typeParameters) {
        this(List.of(), name, typeParameters);
    }

    public ClassDeclaration(Identifier name) {
        this(List.of(), name);
    }

    public List<Type> getParents() {
        return parentTypes;
    }

    public Identifier getName() {
        return name;
    }

    public List<Type> getTypeParameters() {
        return typeParameters;
    }

    public boolean isGeneric() {
        return !typeParameters.isEmpty();
    }

    public UserType getTypeNode() {
        return typeNode;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ClassDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(modifiers, nodeInfos.modifiers) && Objects.equals(name, nodeInfos.name) && Objects.equals(parentTypes, nodeInfos.parentTypes) && Objects.equals(typeParameters, nodeInfos.typeParameters) && Objects.equals(typeNode, nodeInfos.typeNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), modifiers, name, parentTypes, typeParameters, typeNode);
    }

    public ClassDeclaration clone() {
        var clone = (ClassDeclaration) super.clone();
        clone.modifiers = List.copyOf(modifiers);
        clone.name = name;
        clone.typeParameters = typeParameters.stream().map(Type::clone).toList();
        clone.parentTypes = parentTypes.stream().map(Type::clone).toList();
        clone.typeNode = typeNode.clone();
        return clone;
    }
}
