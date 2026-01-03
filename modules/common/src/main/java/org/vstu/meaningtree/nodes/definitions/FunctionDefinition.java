package org.vstu.meaningtree.nodes.definitions;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.UserType;

import java.util.List;
import java.util.Objects;

public class FunctionDefinition extends Definition implements HasBodyStatement {
    @TreeNode protected CompoundStatement body;

    public FunctionDefinition(FunctionDeclaration declaration, CompoundStatement body) {
        super(declaration);
        this.body = body;
    }

    public FunctionDeclaration getDeclaration() {
        return (FunctionDeclaration) super.getDeclaration();
    }

    public Identifier getName() {
        return ((FunctionDeclaration) getDeclaration()).getName();
    }

    public MethodDefinition makeMethod(UserType owner, List<DeclarationModifier> modifiers) {
        FunctionDeclaration decl = (FunctionDeclaration) getDeclaration();
        return new MethodDefinition(
                new MethodDeclaration(
                        owner, decl.getName(), decl.getReturnType(),
                        decl.getAnnotations(), modifiers,
                        decl.getArguments().toArray(new DeclarationArgument[0])),
                getBody());
    }

    public CompoundStatement getBody() {
        return body;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        return body;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FunctionDefinition nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), body);
    }

    public FunctionDefinition clone() {
        FunctionDefinition clone = (FunctionDefinition) super.clone();
        clone.body = body.clone();
        return clone;
    }
}