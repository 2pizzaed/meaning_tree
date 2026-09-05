package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.utils.analysis.library.StandardLibrary;
import org.vstu.meaningtree.utils.analysis.types.conversion.TypeConversionSemantics;
import org.vstu.meaningtree.utils.scopes.AssignmentBinding;
import org.vstu.meaningtree.utils.scopes.OverloadSemantics;
import org.vstu.meaningtree.utils.scopes.ScopePolicy;

import java.util.Objects;

/**
 * Правила языка, которыми параметризуются разбор и анализ: где проходят границы областей
 * видимости, какую переменную связывает присваивание, что считать перегрузкой, как язык
 * уточняет общие правила преобразования типов и что известно про его стандартную библиотеку.
 * <p>
 * Одно значение, а не набор независимых методов, потому что он описывает один язык целиком
 * и по частям смысла не имеет: таблица областей, собранная с питоновскими границами и
 * джавовским правилом связывания, описывает программу, которой нет. Пока черты передавались
 * поодиночке, такое рассогласование было выразимо — {@code ScopeTableBuilder} принимал границы
 * и правило связывания двумя независимыми аргументами.
 * <p>
 * Внутрь входит только то, что <b>описывает правило языка, само работы не выполняя</b>.
 * Поэтому здесь нет {@code ImportResolver} — он ходит по файловой системе, у него своё время
 * жизни и свой контекст проекта; нет идентичности языка ({@code getLanguageId} /
 * {@code getLanguageName}) — это свойство транслятора; нет таблиц токенизатора. И тем более
 * здесь нет пользовательской конфигурации ({@code Config}): её задаёт вызывающий, а эти
 * правила — сам язык, подменить их извне нельзя.
 * <p>
 * <b>Контракт реализации.</b> Значение читается из конструктора {@link TranslatorComponent},
 * то есть во время {@code super()} языкового парсера — раньше, чем подкласс выполнил свои
 * инициализаторы полей. Поэтому реализация обязана быть чистой функцией от языка и не имеет
 * права читать состояние экземпляра. Держите её в {@code private static final} поле: статическая
 * инициализация проходит до любого конструктора, а обращение к {@code this} из неё не
 * компилируется — ограничение проверяет компилятор, а не только этот абзац.
 * <p>
 * Все компоненты неизменяемы и не имеют состояния (перечисления, non-capturing лямбды и
 * классы без полей экземпляра), поэтому один экземпляр {@code LanguageBehavior} безопасно
 * разделять между всеми трансляциями языка.
 */
public record LanguageBehavior(
        @NotNull ScopePolicy scopePolicy,
        @NotNull AssignmentBinding assignmentBinding,
        @NotNull OverloadSemantics overloadSemantics,
        @NotNull TypeConversionSemantics typeConversionSemantics,
        @NotNull StandardLibrary standardLibrary) {

    public LanguageBehavior {
        Objects.requireNonNull(scopePolicy, "scopePolicy must not be null");
        Objects.requireNonNull(assignmentBinding, "assignmentBinding must not be null");
        Objects.requireNonNull(overloadSemantics, "overloadSemantics must not be null");
        Objects.requireNonNull(typeConversionSemantics, "typeConversionSemantics must not be null");
        Objects.requireNonNull(standardLibrary, "standardLibrary must not be null");
    }

    /**
     * Правила языка, устроенного как большинство: блочная видимость, присваивание связывает
     * ближайшую видимую переменную, перегрузка по сигнатуре, преобразования типов — только
     * общие.
     * <p>
     * Обоснование каждого умолчания по отдельности:
     * <ul>
     *   <li><b>Границы областей блочные</b> — так устроено большинство языков, а тот, где
     *       область открывает только определение, умолчание заменяет.</li>
     *   <li><b>Присваивание связывает ближайшую видимую</b> — так устроены языки с явным
     *       объявлением переменных, где присвоить неизвестному имени нельзя вовсе. Язык с
     *       неявным объявлением умолчание заменяет.</li>
     *   <li><b>Перегрузка по сигнатуре</b> — так устроено большинство языков, а тот, где
     *       одноимённые определения затеняют друг друга, умолчание заменяет.</li>
     *   <li><b>Преобразования типов только общие</b> — язык со своими правилами уточняет их
     *       собственной реализацией {@link TypeConversionSemantics}.</li>
     *   <li><b>Стандартная библиотека не описана</b> — таблица функций есть только у языка,
     *       которому она понадобилась; пустая означает «неизвестно», а не «функций нет».</li>
     * </ul>
     * Это и точка входа для языков: заменять следует только то, в чём язык от умолчания
     * отходит, через {@code with*}. Перечислять весь набор конструктором не надо — тогда
     * изменение умолчания молча не дойдёт до языка, который его продублировал.
     */
    public static LanguageBehavior defaults() {
        return DEFAULTS;
    }

    private static final LanguageBehavior DEFAULTS = new LanguageBehavior(
            ScopePolicy.blockScoped(),
            AssignmentBinding.ENCLOSING,
            OverloadSemantics.bySignature(),
            TypeConversionSemantics.common(),
            StandardLibrary.unknown()
    );

    public LanguageBehavior withScopePolicy(@NotNull ScopePolicy scopePolicy) {
        return new LanguageBehavior(scopePolicy, assignmentBinding, overloadSemantics, typeConversionSemantics, standardLibrary);
    }

    public LanguageBehavior withAssignmentBinding(@NotNull AssignmentBinding assignmentBinding) {
        return new LanguageBehavior(scopePolicy, assignmentBinding, overloadSemantics, typeConversionSemantics, standardLibrary);
    }

    public LanguageBehavior withOverloadSemantics(@NotNull OverloadSemantics overloadSemantics) {
        return new LanguageBehavior(scopePolicy, assignmentBinding, overloadSemantics, typeConversionSemantics, standardLibrary);
    }

    public LanguageBehavior withTypeConversionSemantics(@NotNull TypeConversionSemantics typeConversionSemantics) {
        return new LanguageBehavior(scopePolicy, assignmentBinding, overloadSemantics, typeConversionSemantics, standardLibrary);
    }

    public LanguageBehavior withStandardLibrary(@NotNull StandardLibrary standardLibrary) {
        return new LanguageBehavior(scopePolicy, assignmentBinding, overloadSemantics, typeConversionSemantics, standardLibrary);
    }
}
