package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.declarations.Annotation;

import java.util.List;

public abstract class Declaration extends Node {
    @TreeNode
    protected List<Annotation> annotations;

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

}
