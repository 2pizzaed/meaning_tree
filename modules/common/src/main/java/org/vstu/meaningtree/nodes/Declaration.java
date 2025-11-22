package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.declarations.Annotation;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;

import java.util.ArrayList;
import java.util.List;

public abstract class Declaration extends Node {
    @TreeNode
    protected List<Annotation> annotations;

    protected List<DeclarationModifier> modifiers;

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public boolean hasAnnotations() {
        return !annotations.isEmpty();
    }

    public Declaration clone() {
        return (Declaration) super.clone();
    }

    public List<Annotation> getAnnotations() {
        return annotations == null ? List.of() : List.copyOf(annotations);
    }

    public List<DeclarationModifier> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<DeclarationModifier> modifiers) {
        this.modifiers = new ArrayList<>(modifiers);
    }

    public void addModifiers(DeclarationModifier ... modifiers) {
        if (this.modifiers == null) {
            this.modifiers = new ArrayList<>();
        }
        for (DeclarationModifier mod : modifiers) {
            this.modifiers.add(mod);
        }
    }
}
