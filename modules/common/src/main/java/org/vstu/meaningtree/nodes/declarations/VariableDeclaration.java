package org.vstu.meaningtree.nodes.declarations;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VariableDeclaration extends Declaration implements HasInitialization {
    @TreeNode protected Type type;
    @TreeNode protected List<VariableDeclarator> variableDeclaratorList;
    @TreeNode protected List<Annotation> annotations;

    public VariableDeclaration(Type type, SimpleIdentifier name) {
        this(type, name, null);
    }

    public VariableDeclaration(Type type, SimpleIdentifier name, Expression value) {
        variableDeclaratorList = new ArrayList<>();
        variableDeclaratorList.add(new VariableDeclarator(name, value));
        this.type = type;
    }

    public VariableDeclaration(Type type, VariableDeclarator... variableDeclarators) {
        this(type, List.of(variableDeclarators));
    }

    public VariableDeclaration(Type type, List<VariableDeclarator> variableDeclarators) {
        variableDeclaratorList = List.copyOf(variableDeclarators);
        this.type = type;
    }

    public void setType(Type newType) {
        type = newType;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public List<Annotation> getAnnotations() {
        return List.copyOf(annotations);
    }

    public Type getType() {
        return type;
    }

    public FieldDeclaration makeField(List<DeclarationModifier> modifiers) {
        return new FieldDeclaration(getType(), modifiers, getDeclarators());
    }

    public VariableDeclarator[] getDeclarators() {
        return variableDeclaratorList.toArray(new VariableDeclarator[0]);
    }

    @Override
    public String generateDot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        VariableDeclaration that = (VariableDeclaration) o;
        return Objects.equals(type, that.type) && Objects.equals(variableDeclaratorList, that.variableDeclaratorList);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), type, variableDeclaratorList);
    }
}
