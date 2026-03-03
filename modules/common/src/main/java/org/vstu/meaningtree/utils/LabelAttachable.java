package org.vstu.meaningtree.utils;

import java.util.Set;

public interface LabelAttachable {
    /**
     * Переключает состояние метки
     * @param id - айди метки
     * @return убрана или установлена метка после вызова этой функции
     */
    default boolean toggleLabel(short id) {
        return toggleLabel(id, null);
    }

    /**
     * Переключает состояние метки
     * @param id - айди метки
     * @param val - атрибут
     * @return убрана или установлена метка после вызова этой функции
     */
    default boolean toggleLabel(short id, Object val) {
        if (hasLabel(id)) {
            removeLabel(id);
            return false;
        } else {
            setLabel(new Label(id, val));
            return true;
        }
    }

    LabelAttachable setLabel(Label label);

    default void setLabel(short id) {
        setLabel(new Label(id));
    }

    Label getLabel(short id);

    boolean hasLabel(short id);

    default boolean removeLabel(short id) {
       return removeLabel(getLabel(id));
    }

    boolean removeLabel(Label label);

    Set<Label> getAllLabels();
}
