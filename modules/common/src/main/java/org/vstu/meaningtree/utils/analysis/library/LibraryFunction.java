package org.vstu.meaningtree.utils.analysis.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Type;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Сигнатура функции стандартной библиотеки: что она возвращает и какие типы принимает.
 * <p>
 * Только сигнатура: имя и единица подключения живут в {@link LibrarySymbol}, одной записью на
 * имя. Пока заголовок лежал здесь, а состав этого заголовка — в реестре импортов, знание про
 * одну и ту же функцию было разложено по двум таблицам с одним ключом, и каждая из них
 * оказывалась неполной.
 *
 * <h2>Почему типы — поставщики, а не готовые экземпляры</h2>
 * {@link Type} — это {@code Node} с идентификатором. Таблица общих экземпляров означала бы, что
 * один и тот же узел достижим из двух мест дерева: обход посетил бы его дважды, а
 * {@code MeaningTree.makeIndex} упал бы на {@code Duplicate node id} (см.
 * {@code docs/references/node-types.md}). Поэтому каждый запрос типа создаёт новый узел.
 *
 * @param returnType возвращаемый тип; {@code null}, если он не выразим — так у шаблонов и
 *                   обобщённых функций, где тип зависит от аргументов
 * @param parameters типы фиксированных параметров. У функции с переменным числом аргументов
 *                   описаны только фиксированные: про остальные ничего не известно, и
 *                   притворяться, что известно, нельзя
 */
public record LibraryFunction(@Nullable Supplier<Type> returnType,
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
