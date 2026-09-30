package org.vstu.meaningtree.nodes.declarations.components;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.enums.AccessorKind;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.utils.InternalNode;

import java.util.Objects;

/** The role and logical property name of a method, independent of its implementation. */
@InternalNode
public class PropertyAccessor extends Node {
    private final AccessorKind kind;
    @TreeNode
    private SimpleIdentifier propertyName;
    private final boolean propertyDefinition;
    private final int annotationIndex;

    public PropertyAccessor(AccessorKind kind, SimpleIdentifier propertyName) {
        this(kind, propertyName, kind == AccessorKind.GETTER, 0);
    }

    /**
     * @param propertyDefinition whether the getter creates a property ({@code @property})
     *                           rather than replaces its getter ({@code @name.getter})
     * @param annotationIndex number of ordinary decorators preceding the accessor decorator
     */
    public PropertyAccessor(AccessorKind kind, SimpleIdentifier propertyName,
                            boolean propertyDefinition, int annotationIndex) {
        this.kind = Objects.requireNonNull(kind);
        this.propertyName = Objects.requireNonNull(propertyName);
        if (propertyDefinition && kind != AccessorKind.GETTER) {
            throw new IllegalArgumentException("Only a getter can define a property");
        }
        if (annotationIndex < 0) {
            throw new IllegalArgumentException("Annotation index must be non-negative");
        }
        this.propertyDefinition = propertyDefinition;
        this.annotationIndex = annotationIndex;
    }

    public AccessorKind getKind() {
        return kind;
    }

    public SimpleIdentifier getPropertyName() {
        return propertyName;
    }

    public boolean isPropertyDefinition() {
        return propertyDefinition;
    }

    public int getAnnotationIndex() {
        return annotationIndex;
    }

    @Override
    public PropertyAccessor clone() {
        PropertyAccessor clone = (PropertyAccessor) super.clone();
        clone.propertyName = propertyName.clone();
        return clone;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PropertyAccessor accessor)) return false;
        return super.equals(other) && kind == accessor.kind
                && propertyName.equals(accessor.propertyName)
                && propertyDefinition == accessor.propertyDefinition
                && annotationIndex == accessor.annotationIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), kind, propertyName, propertyDefinition, annotationIndex);
    }
}
