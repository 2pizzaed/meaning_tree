package org.vstu.meaningtree.nodes;

import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.builtin.CharacterType;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.nodes.types.containers.ArrayType;

import java.util.Objects;

public abstract class Type extends Identifier {
    private boolean isConst = false;
    private boolean isSafeReference = false;

    public boolean isSafeReference() {
        return isSafeReference;
    }

    /**
     * Важно для Python. Если тип объявлен как аннотация в виде строки,
     * то это попытка обезопасить ссылку на возможно еще необъявленный тип
     * @param safeReference ссылка на тип в данном месте пока может быть не валидной?
     */
    public void setSafeReference(boolean safeReference) {
        isSafeReference = safeReference;
    }

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

    @Override
    public String internalRepresentation() {
        return getClass().getSimpleName();
    }

    public static boolean isCStyleString(Type t) {
        return (t instanceof PointerType ptr && ptr.getTargetType() instanceof CharacterType) ||
                (t instanceof ArrayType arr && arr.getDimensionsCount() == 1 && arr.getItemType() instanceof CharacterType);
    }
}
