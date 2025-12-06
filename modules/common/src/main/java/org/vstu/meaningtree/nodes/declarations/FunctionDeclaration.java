package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FunctionDeclaration extends Declaration {
    @TreeNode private List<DeclarationArgument> arguments;
    @TreeNode private Identifier name;
    @TreeNode private Type returnType;

    public FunctionDeclaration(Identifier name, Type returnType, List<Annotation> annotations, DeclarationArgument... arguments) {
        this(name, returnType, annotations, List.of(arguments));
    }

    public FunctionDeclaration(Identifier name, Type returnType, List<Annotation> annotations, List<DeclarationArgument> arguments) {
        this.name = name;
        this.annotations = new ArrayList<>(annotations);
        this.arguments = List.copyOf(arguments);
        this.returnType = returnType;
    }

    public Identifier getQualifiedName() {
        return name;
    }

    public SimpleIdentifier getName() {
        if (getQualifiedName() instanceof QualifiedIdentifier qualified) {
            return qualified.getMember();
        } else if (getQualifiedName() instanceof ScopedIdentifier scoped) {
            return scoped.getScopeResolution().getLast();
        }
        return (SimpleIdentifier) name;
    }

    public List<DeclarationArgument> getArguments() {
        return arguments;
    }

    public Type getReturnType() {
        return returnType;
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FunctionDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(arguments, nodeInfos.arguments) && Objects.equals(name, nodeInfos.name) && Objects.equals(returnType, nodeInfos.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), arguments, name, returnType);
    }

    public FunctionDeclaration clone() {
        var clone = (FunctionDeclaration) super.clone();
        clone.arguments = new ArrayList<>(arguments.stream().map(DeclarationArgument::clone).toList());
        clone.name = name;
        clone.returnType = returnType;
        return clone;
    }
}
