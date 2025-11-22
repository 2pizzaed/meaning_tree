package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FieldDeclaration extends VariableDeclaration {

    public FieldDeclaration(Type type, SimpleIdentifier name, List<DeclarationModifier> modifiers) {
        super(type, name);
        this.modifiers = new ArrayList<>(modifiers);
    }

    public FieldDeclaration(Type type, SimpleIdentifier name, Expression value, List<DeclarationModifier> modifiers) {
        super(type, name, value);
        this.modifiers = new ArrayList<>(modifiers);
    }

    public FieldDeclaration(Type type, List<DeclarationModifier> modifiers, VariableDeclarator... declarators) {
        this(type, modifiers, List.of(declarators));
    }

    public FieldDeclaration(Type type, List<DeclarationModifier> modifiers, List<VariableDeclarator> declarators) {
        super(type, declarators);
        this.modifiers = new ArrayList<>(modifiers);
    }

    public FieldDeclaration(Type type, SimpleIdentifier name) {
        this(type, name, List.of());
    }


    public FieldDeclaration(Type type, SimpleIdentifier name, Expression value) {
        this(type, name, value, List.of());
    }

    public FieldDeclaration(Type type, VariableDeclarator... fields) {
        this(type, List.of(), fields);
    }


    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(modifiers, nodeInfos.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), modifiers);
    }

    public FieldDeclaration clone() {
        var clone = (FieldDeclaration) super.clone();
        clone.modifiers = List.copyOf(modifiers);
        return clone;
    }
}
