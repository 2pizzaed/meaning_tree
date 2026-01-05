package org.vstu.meaningtree.nodes.definitions;

import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

public class MethodDefinition extends FunctionDefinition {
    public MethodDefinition(MethodDeclaration declaration, CompoundStatement body) {
        super(declaration, body);
    }

    public MethodDeclaration getDeclaration() {
        return (MethodDeclaration) super.getDeclaration();
    }

    public SimpleIdentifier getName() {
        MethodDeclaration methodDeclaration = getDeclaration();
        return methodDeclaration.getName();
    }
}
