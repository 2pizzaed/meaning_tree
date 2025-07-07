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

public class FunctionDefinition extends Definition implements HasBodyStatement {
    @TreeNode protected CompoundStatement body;

    public FunctionDefinition(FunctionDeclaration declaration, CompoundStatement body) {
        super(declaration);
        this.body = body;
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

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    public CompoundStatement getBody() {
        return body;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        return body;
    }
}