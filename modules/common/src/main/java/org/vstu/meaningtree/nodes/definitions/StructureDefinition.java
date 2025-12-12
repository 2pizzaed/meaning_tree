package org.vstu.meaningtree.nodes.definitions;

import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

public class StructureDefinition extends ClassDefinition {
    public StructureDefinition(ClassDeclaration declaration, CompoundStatement body) {
        super(declaration, body);
    }
}
