package org.vstu.meaningtree.nodes.types;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.interfaces.Generic;

import java.util.Arrays;
import java.util.Objects;

public class GenericUserType extends UserType implements Generic {
    @TreeNode private Type[] templateParameters;

    public GenericUserType(Identifier name, Type ... templateParameters) {
        super(name);
        this.templateParameters = templateParameters;
    }

    @Override
    public Type[] getTypeParameters() {
        return templateParameters;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        GenericUserType that = (GenericUserType) o;
        return Objects.deepEquals(templateParameters, that.templateParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), Arrays.hashCode(templateParameters));
    }

    @Override
    public GenericUserType clone() {
        GenericUserType obj = (GenericUserType) super.clone();
        Type[] newTemplateParameters = new Type[templateParameters.length];
        for (int i = 0; i < templateParameters.length; i++) {
            newTemplateParameters[i] = templateParameters[i].clone();
        }
        obj.templateParameters = newTemplateParameters;
        return obj;
    }
}
