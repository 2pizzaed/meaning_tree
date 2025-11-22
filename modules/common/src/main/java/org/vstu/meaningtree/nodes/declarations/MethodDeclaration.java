package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.UserType;

import java.util.List;
import java.util.Objects;

public class MethodDeclaration extends FunctionDeclaration {
    @TreeNode private UserType owner;

    public MethodDeclaration(UserType owner,
                             Identifier name,
                             Type returnType,
                             List<Annotation> annotations,
                             List<DeclarationModifier> modifiers,
                             DeclarationArgument... arguments
    ) {
        this(owner, name, returnType, annotations, modifiers, List.of(arguments));
    }

    public MethodDeclaration(UserType owner,
                             Identifier name,
                             Type returnType,
                             List<Annotation> annotations,
                             List<DeclarationModifier> modifiers,
                             List<DeclarationArgument> arguments
    ) {
        super(name, returnType, annotations, arguments);
        this.owner = owner;
        this.modifiers = List.copyOf(modifiers);
    }

    public UserType getOwner() {
        return owner;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(owner, nodeInfos.owner) && Objects.equals(modifiers, nodeInfos.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), owner, modifiers);
    }

    public MethodDeclaration clone() {
        var clone = (MethodDeclaration) super.clone();
        clone.modifiers = List.copyOf(modifiers);
        clone.owner = owner.clone();
        return clone;
    }
}
