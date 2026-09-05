package org.vstu.meaningtree.utils.analysis.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Type;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Что известно про функцию стандартной библиотеки: где она объявлена, что возвращает и какие
 * типы принимает.
 * <p>
 * Одна запись на функцию, а не отдельная таблица под каждый вопрос. Знание про одну и ту же
 * функцию, разложенное по нескольким таблицам с одним ключом, неизбежно оказывается неполным в
 * каждой из них: добавляющий функцию помнит про ту таблицу, ради которой пришёл.
 *
 * <h2>Почему типы — поставщики, а не готовые экземпляры</h2>
 * {@link Type} — это {@code Node} с идентификатором. Таблица общих экземпляров означала бы, что
 * один и тот же узел достижим из двух мест дерева: обход посетил бы его дважды, а
 * {@code MeaningTree.makeIndex} упал бы на {@code Duplicate node id} (см.
 * {@code docs/references/node-types.md}). Поэтому каждый запрос типа создаёт новый узел.
 *
 * @param header     единица подключения, без которой вызов не соберётся: заголовок в C++, модуль
 *                   в Python или Java. {@code null}, если подключать нечего — функция встроена
 * @param returnType возвращаемый тип; {@code null}, если он не выразим — так у шаблонов и
 *                   обобщённых функций, где тип зависит от аргументов
 * @param parameters типы фиксированных параметров. У функции с переменным числом аргументов
 *                   описаны только фиксированные: про остальные ничего не известно, и
 *                   притворяться, что известно, нельзя
 */
public record LibraryFunction(@Nullable String header,
                              @Nullable Supplier<Type> returnType,
                              @NotNull List<Supplier<Type>> parameters) {

    public LibraryFunction {
        Objects.requireNonNull(parameters, "parameters must not be null");
        parameters = List.copyOf(parameters);
    }

    /** @return {@code null}, если возвращаемый тип не описан */
    @Nullable
    public Type resolveReturnType() {
        return returnType == null ? null : returnType.get();
    }

    /**
     * @return тип параметра, либо {@code null}, если он не описан — в том числе для аргумента за
     * пределами фиксированной части
     */
    @Nullable
    public Type resolveParameterType(int index) {
        if (index < 0 || index >= parameters.size()) {
            return null;
        }
        return parameters.get(index).get();
    }

    /** Относится ли параметр к типу {@code type} — вопрос, который чаще всего и задают таблице. */
    public boolean isParameterOfType(int index, @NotNull Class<? extends Type> type) {
        return type.isInstance(resolveParameterType(index));
    }

    public boolean returnsType(@NotNull Class<? extends Type> type) {
        return type.isInstance(resolveReturnType());
    }
}
