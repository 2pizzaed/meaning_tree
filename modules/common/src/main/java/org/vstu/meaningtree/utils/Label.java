package org.vstu.meaningtree.utils;

import java.util.Objects;

/**
 * Специальные метки для узла дерева
 */
public class Label {
    /**
     * Метка, которая использует атрибут и позволяет привязать к узлу любое значение для любых целей
     */
    public static final short VALUE = 0;

    /**
     * Указывает Viewer выводить пустую строку вместо этого узла
     */
    public static final short DUMMY = 1;

    /**
     * Пометка для пользовательского кода, указывает, что узел изменился после каких-то манипуляций (мутация).
     * Устанавливается пользовательским кодом по соглашениям, определенным в нем.
     * Может иметь атрибут в виде short кода, принятого пользователем для уточнения мутации
     */
    public static final short MUTATION_FLAG = 2;

    /**
     * Показывает, из какого языка создано дерево изначально. Содержит id из enum SupportedLanguage
     */
    public static final short ORIGIN = 3;

    /**
     * Зарезервированный номер. Применяется в случае, если метка была не распознана
     */
    public static final short UNKNOWN = Short.MAX_VALUE;

    /**
     * Зарезервированный номер. Применяется, если владелец метки имеет ошибку
     */
    public static final short ERROR = Short.MIN_VALUE;


    /**
     * Пользователи библиотеки могут создавать собственные метки. Для этого выделен диапазон отрицательных id
     * Представлено максимально возможное значение пользовательской метки
     */
    public static final short USER_TAG_MAX_ID = -1;
    /**
     * Пользователи библиотеки могут создавать собственные метки. Для этого выделен диапазон отрицательных id
     * Представлено минимально возможное значение пользовательской метки
     */
    public static final short USER_TAG_MIN_ID = -32000;


    private short id;
    private Object attribute = null;

    public Label(short id, Object attribute) {
        this.attribute = attribute;
        this.id = id;
    }

    public Label(short id) {
        this.id = id;
    }

    public short getId() {
        return id;
    }

    public Object getAttribute() {
        return attribute;
    }

    public boolean hasAttribute() {
        return attribute != null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Label)) return false;
        return ((Label)o).id == this.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(attribute, id);
    }
}
