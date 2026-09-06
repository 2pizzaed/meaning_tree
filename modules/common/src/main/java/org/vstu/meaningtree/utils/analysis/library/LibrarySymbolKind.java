package org.vstu.meaningtree.utils.analysis.library;

/**
 * Род имени из стандартной библиотеки.
 * <p>
 * Заголовок или модуль нужен коду не только ради вызовов: {@code FILE}, {@code EOF} и
 * {@code stdout} не вызывает никто, а без своей единицы подключения они не соберутся. Род
 * отвечает на вопрос «что этим именем можно сделать» — сигнатура бывает только у
 * {@link #FUNCTION}.
 */
public enum LibrarySymbolKind {
    /** Функция или функциональный макрос: вызывается. */
    FUNCTION,
    /** Тип или псевдоним типа: {@code FILE}, {@code size_t}. */
    TYPE,
    /** Именованная константа, в C/C++ обычно макрос: {@code EOF}, {@code SEEK_SET}. */
    CONSTANT,
    /** Объект библиотеки: {@code stdin}, {@code stdout}, {@code errno}. */
    OBJECT
}
