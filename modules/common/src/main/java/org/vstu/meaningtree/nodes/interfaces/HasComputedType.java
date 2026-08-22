package org.vstu.meaningtree.nodes.interfaces;

import org.vstu.meaningtree.nodes.Type;

/**
 * Отмечает узел, для которого type inferrer вычисляет и хранит realType — фактический
 * тип присваиваемого/инициализирующего значения, в отличие от декларированного типа
 * переменной. Это результат анализа, а не часть синтаксиса: полезно для статического
 * анализа, например при отслеживании реального типа переменной через полиморфизм.
 * <p>
 * По умолчанию realType — {@code UnknownType}, пока type inferrer не дойдёт до узла.
 */
public interface HasComputedType {
    Type getRealType();

    void setRealType(Type type);
}
