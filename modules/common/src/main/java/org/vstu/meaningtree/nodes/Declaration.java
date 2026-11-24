package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.declarations.Annotation;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public abstract class Declaration extends Node {
    @TreeNode
    protected List<Annotation> annotations;

    protected List<DeclarationModifier> modifiers = new ArrayList<>();

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    public boolean hasAnnotations() {
        return !annotations.isEmpty();
    }

    public Declaration clone() {
        Declaration clone = (Declaration) super.clone();
        clone.annotations = annotations == null
                ? null
                : new ArrayList<>(annotations.stream().map(Annotation::clone).toList());
        clone.modifiers = new ArrayList<>(modifiers);
        return clone;
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

    /** Hashes the ordered modifier list by name to avoid enum identity hashes. */
    protected int modifiersHashCode() {
        int hash = 1;
        for (DeclarationModifier modifier : modifiers) {
            hash = 31 * hash + Objects.hashCode(enumName(modifier));
        }
        return hash;
    }

    public void addModifiers(DeclarationModifier ... modifiers) {
        this.modifiers = new ArrayList<>(this.modifiers);
        this.modifiers.addAll(List.of(modifiers));
    }
}
