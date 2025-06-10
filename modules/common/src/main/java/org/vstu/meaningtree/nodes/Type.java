package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.nodes.expressions.Identifier;

import java.util.Objects;

public abstract class Type extends Identifier {
    private boolean isConst = false;

    public boolean isConst() {
        return isConst;
    }

    // Чтобы не пришлось модифицировать конструкторы остальных типов
    public void setConst(boolean state) {
        isConst = state;
    }

    @Override
    public boolean equals(Object o) {
        return o.getClass().equals(this.getClass());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getClass().getName().hashCode(), "meaning_tree_type_node");
    }

    @Override
    public Type clone() {
        return (Type) super.clone();
    }

    @Override
    public int contentSize() {
        return 1;
    }

    @Override
    public boolean contains(Identifier o) {
        return false;
    }
}
