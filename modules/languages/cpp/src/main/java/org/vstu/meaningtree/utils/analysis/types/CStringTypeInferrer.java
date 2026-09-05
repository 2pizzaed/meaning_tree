package org.vstu.meaningtree.utils.analysis.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.CharacterLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.PlainCollectionLiteral;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;
import org.vstu.meaningtree.nodes.interfaces.HasAssignmentEffect;
import org.vstu.meaningtree.nodes.io.PointerInputCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;
import org.vstu.meaningtree.nodes.types.builtin.CharacterType;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;
import org.vstu.meaningtree.nodes.types.containers.ArrayType;
import org.vstu.meaningtree.languages.LanguageBehavior;
import org.vstu.meaningtree.utils.analysis.library.LibraryFunction;
import org.vstu.meaningtree.utils.analysis.library.StandardLibrary;
import org.vstu.meaningtree.utils.analysis.symbols.VariableUsageIndex;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Распознаёт переменные, которые в C/C++ объявлены как {@code char *} или {@code char[]}, но по
 * употреблению достоверно являются строками, и заменяет их тип на {@link StringType}.
 * <p>
 * Разбор сам по себе такое решение принять не может: {@code char *} — это ровно указатель на
 * символ, и строкой он становится только из-за того, что с ним делают дальше по коду. Поэтому
 * эвристика живёт отдельным проходом по готовому дереву, а не в {@code CppParser}: на момент
 * разбора объявления вызова {@code strcpy}, который всё решает, ещё не существует.
 * <p>
 * Обратное направление у вьюера было всегда — {@code CppViewer} печатает {@link StringType} как
 * {@code char *} в C-режиме. Без этого прохода соответствие оставалось односторонним: строка,
 * написанная на Си, приезжала в Python и Java указателем на символ.
 *
 * <h2>Признаки</h2>
 * Достаточно одного:
 * <ol>
 *   <li>инициализатор или правая часть присваивания — строковый литерал;</li>
 *   <li>инициализатор — литерал массива символов с {@code '\0'} в конце;</li>
 *   <li>переменная попадает в параметр библиотечной функции, объявленный как {@code char *}
 *       (см. {@link StandardLibrary}), либо ей присваивается результат
 *       такой функции, возвращающей {@code char *};</li>
 *   <li>переменная — аргумент строкового ввода-вывода, который {@code CppParser} уже свернул в
 *       узел: {@link PrintValues} (из {@code puts}), {@link PointerInputCommand} (из
 *       {@code gets}).</li>
 * </ol>
 * Признак «строка передана в {@code %s}» ({@code printf}/{@code scanf}) не проверяется: он
 * требует разбора форматной строки, а не формы дерева.
 *
 * <h2>Что не трогается</h2>
 * <ul>
 *   <li>{@code char **argv} и многомерные массивы: два уровня косвенности — это уже не одна
 *       строка;</li>
 *   <li>{@code char * const p}: константность самого указателя в {@link StringType} невыразима,
 *       а терять её нельзя;</li>
 *   <li>объявление без единого признака: {@code char *p = malloc(n)} остаётся буфером, потому
 *       что буфером он и может быть.</li>
 * </ul>
 * Ёмкость массива не теряется — она переезжает в {@link StringType#getMaxLength()}, поэтому
 * {@code char buf[64]} печатается обратно ровно как {@code char buf[64]}.
 */
public final class CStringTypeInferrer {
    private final MeaningTree tree;
    private final ScopeTable scope;
    private final LanguageBehavior behavior;

    /** Кандидаты по идентификатору узла-объявления: {@code equals} у узлов значимостный. */
    private final Map<Long, Candidate> candidates = new LinkedHashMap<>();
    private final Map<Long, Boolean> isString = new HashMap<>();

    private VariableUsageIndex usages;

    /**
     * Правила языка принимаются целиком, а не по одной черте: границы областей видимости и
     * описание стандартной библиотеки обязаны описывать один и тот же язык, а два независимых
     * аргумента позволяли бы взять их от разных.
     */
    public CStringTypeInferrer(@NotNull MeaningTree tree, @NotNull ScopeTable scope,
                               @NotNull LanguageBehavior behavior) {
        this.tree = Objects.requireNonNull(tree, "tree must not be null");
        this.scope = Objects.requireNonNull(scope, "scope must not be null");
        this.behavior = Objects.requireNonNull(behavior, "behavior must not be null");
    }

    /**
     * Объявление-кандидат вместе с тем, что понадобится для замены типа.
     *
     * @param charType  символьный тип под указателем или массивом — из него берётся ширина
     *                  символа и константность ({@code const char *} — const на цели)
     * @param maxLength размерность массива, если она указана явно
     */
    private record Candidate(Declaration declaration, Type declaredType, CharacterType charType,
                             @Nullable Expression maxLength) {
    }

    public void infer() {
        usages = new VariableUsageIndex(tree, scope, behavior.scopePolicy()).build();
        collectCandidates();
        if (candidates.isEmpty()) {
            return;
        }
        markDirectEvidence();
        propagate();
        applyInferredTypes();
    }

    private void collectCandidates() {
        for (Declaration declaration : usages.indexedDeclarations()) {
            candidateOf(declaration).ifPresent(candidate ->
                    candidates.put(declaration.getId(), candidate));
        }
    }

    private Optional<Candidate> candidateOf(Declaration declaration) {
        Type declared = declaredType(declaration);
        if (declared == null) {
            return Optional.empty();
        }
        if (declared instanceof PointerType pointer) {
            // const у самого указателя (char * const p) при схлопывании в строку выразить нечем
            if (pointer.isConst() || !(pointer.getTargetType() instanceof CharacterType charType)) {
                return Optional.empty();
            }
            return Optional.of(new Candidate(declaration, declared, charType, null));
        }
        if (declared instanceof ArrayType array
                && array.getDimensionsCount() == 1
                && array.getItemType() instanceof CharacterType charType) {
            List<Expression> dimensions = array.getShape().getDimensions();
            Expression size = dimensions.isEmpty() ? null : dimensions.get(0);
            return Optional.of(new Candidate(declaration, declared, charType, size));
        }
        return Optional.empty();
    }

    @Nullable
    private static Type declaredType(Declaration declaration) {
        if (declaration instanceof VariableDeclaration variable) {
            return variable.getType();
        }
        if (declaration instanceof DeclarationArgument argument) {
            return argument.getType();
        }
        return null;
    }

    /** Признаки, которые видны без оглядки на другие объявления. */
    private void markDirectEvidence() {
        for (Candidate candidate : candidates.values()) {
            if (initializersProveString(candidate.declaration()) || usagesProveString(candidate)) {
                isString.put(candidate.declaration().getId(), true);
            }
        }
    }

    private boolean initializersProveString(Declaration declaration) {
        for (Expression value : initializers(declaration)) {
            if (provesString(value)) {
                return true;
            }
        }
        return false;
    }

    private static List<Expression> initializers(Declaration declaration) {
        List<Expression> values = new ArrayList<>();
        if (declaration instanceof VariableDeclaration variable) {
            for (VariableDeclarator declarator : variable.getDeclarators()) {
                if (declarator.getRValue() != null) {
                    values.add(declarator.getRValue());
                }
            }
        } else if (declaration instanceof DeclarationArgument argument
                && argument.hasInitialExpression()) {
            values.add(argument.getInitialExpression());
        }
        return values;
    }

    /**
     * Значение, которое само по себе доказывает строку: строковый литерал, литерал массива
     * символов с завершающим нулём или результат функции, возвращающей {@code char *}.
     */
    private boolean provesString(Expression value) {
        if (value instanceof StringLiteral) {
            return true;
        }
        if (value instanceof PlainCollectionLiteral collection) {
            return endsWithNullCharacter(collection);
        }
        return value instanceof FunctionCall call
                && describe(call).map(function -> function.returnsType(StringType.class))
                .orElse(false);
    }

    private static boolean endsWithNullCharacter(PlainCollectionLiteral collection) {
        List<Expression> items = collection.getList();
        if (items.isEmpty()) {
            return false;
        }
        return items.stream().allMatch(CharacterLiteral.class::isInstance)
                && ((CharacterLiteral) items.get(items.size() - 1)).getValue() == 0;
    }

    private boolean usagesProveString(Candidate candidate) {
        for (NodeInfo use : usages.usagesOf(candidate.declaration())) {
            if (useProvesString(use)) {
                return true;
            }
        }
        return false;
    }

    private boolean useProvesString(NodeInfo use) {
        Node parent = use.parentNode();
        String field = use.field() == null ? null : use.field().getName();

        if (parent instanceof PointerInputCommand input && input.getTargetString() == use.node()) {
            return true;
        }
        if (parent instanceof PrintValues && "arguments".equals(field)) {
            return true;
        }
        if (parent instanceof FunctionCall call && "arguments".equals(field)) {
            return describe(call)
                    .map(function -> function.isParameterOfType(use.field().getIndex(), StringType.class))
                    .orElse(false);
        }
        // strcpy(dst, src) присваивает через параметр; обычное присваивание — отдельный признак
        return parent instanceof HasAssignmentEffect assignment
                && assignedTarget(assignment) == use.node()
                && provesString(assignedValue(assignment));
    }

    private Optional<LibraryFunction> describe(FunctionCall call) {
        if (!call.hasFunctionName()) {
            return Optional.empty();
        }
        return behavior.standardLibrary().function(call.getFunctionName().getName());
    }

    /**
     * Распространение по присваиваниям: {@code char *p = buf;} делает {@code p} строкой, если
     * строкой уже признан {@code buf}. Повторяется до неподвижной точки — цепочка присваиваний
     * может быть длиннее одного шага, а порядок объявлений в файле ничего не гарантирует.
     */
    private void propagate() {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Candidate candidate : candidates.values()) {
                if (isString.containsKey(candidate.declaration().getId())) {
                    continue;
                }
                if (inheritsFromKnownString(candidate)) {
                    isString.put(candidate.declaration().getId(), true);
                    changed = true;
                }
            }
        }
    }

    private boolean inheritsFromKnownString(Candidate candidate) {
        for (Expression value : initializers(candidate.declaration())) {
            if (isKnownString(value)) {
                return true;
            }
        }
        for (NodeInfo use : usages.usagesOf(candidate.declaration())) {
            if (use.parentNode() instanceof HasAssignmentEffect assignment
                    && assignedTarget(assignment) == use.node()
                    && isKnownString(assignedValue(assignment))) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownString(@Nullable Expression value) {
        if (!(value instanceof SimpleIdentifier)) {
            return false;
        }
        NodeInfo info = tree.getNodeById(value.getId());
        return info != null && usages.declarationOf(info)
                .map(declaration -> isString.containsKey(declaration.getId()))
                .orElse(false);
    }

    @Nullable
    private static Node assignedTarget(HasAssignmentEffect assignment) {
        return switch (assignment) {
            case org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement statement ->
                    statement.getLValue();
            case org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression expression ->
                    expression.getLValue();
            default -> null;
        };
    }

    @Nullable
    private static Expression assignedValue(HasAssignmentEffect assignment) {
        return switch (assignment) {
            case org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement statement ->
                    statement.getRValue();
            case org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression expression ->
                    expression.getRValue();
            default -> null;
        };
    }

    private void applyInferredTypes() {
        for (Candidate candidate : candidates.values()) {
            if (!isString.containsKey(candidate.declaration().getId())) {
                continue;
            }
            StringType inferred = new StringType(
                    candidate.charType().getBitsize(), true, false, candidate.maxLength());
            inferred.setConst(candidate.charType().isConst() || candidate.declaredType().isConst());
            // Узел, подменённый вне разбора, обязан унести id оригинала: иначе его исходный
            // диапазон выпадает из source map, а в неё попадает id, который никто не разрешит
            inferred.remap(candidate.declaredType());
            assignType(candidate.declaration(), inferred);
        }
        if (!isString.isEmpty()) {
            tree.invalidateCache();
        }
    }

    private void assignType(Declaration declaration, StringType inferred) {
        if (declaration instanceof VariableDeclaration variable) {
            variable.setType(inferred);
            for (VariableDeclarator declarator : variable.getDeclarators()) {
                rewriteCharacterArrayInitializer(declarator);
            }
            syncScopeTable(variable, inferred);
        } else if (declaration instanceof DeclarationArgument argument) {
            // Параметров в ScopeTable нет вовсе (см. VariableUsageIndex), синхронизировать нечего
            argument.setType(inferred);
        }
    }

    /**
     * Переписывает {@code {'h', 'i', '\0'}} в {@code "hi"}.
     * <p>
     * Не украшательство, а необходимость: тип объявления уже стал строкой, и печатать его вместе
     * со списком символов нельзя — {@code char * t = {'h', 'i', '\0'};} не собирается ни в Си,
     * ни в C++. Литерал символов и строковый литерал здесь описывают одно и то же значение,
     * поэтому замена ничего не теряет.
     */
    private void rewriteCharacterArrayInitializer(VariableDeclarator declarator) {
        if (!(declarator.getRValue() instanceof PlainCollectionLiteral collection)
                || !endsWithNullCharacter(collection)) {
            return;
        }
        StringBuilder text = new StringBuilder();
        // Завершающий ноль в тексте строки не участвует: его несёт само представление строки
        List<Expression> items = collection.getList();
        for (Expression item : items.subList(0, items.size() - 1)) {
            text.appendCodePoint(((CharacterLiteral) item).getValue());
        }
        StringLiteral literal = StringLiteral.fromUnescaped(text.toString(), StringLiteral.Type.NONE);
        literal.remap(collection);
        declarator.setRValue(literal);
    }

    /**
     * Дотягивает новый тип до {@link ScopeTable}, если переменная там есть.
     * <p>
     * Проверка «есть ли» обязательна и не является перестраховкой: таблица наполняется попутно
     * при разборе, и C++ строит единицу трансляции и тела классов в обход {@code BodyConstructor},
     * поэтому часть объявлений в неё не попадает. Требовать их наличия — падать на совершенно
     * корректной программе.
     */
    private void syncScopeTable(VariableDeclaration variable, StringType inferred) {
        NodeInfo info = tree.getNodeById(variable.getId());
        scope.runInScope(ScopeTable.nearestScopeId(info), () -> {
            for (VariableDeclarator declarator : variable.getDeclarators()) {
                if (scope.hasVariable(declarator.getIdentifier())) {
                    scope.changeVariableType(declarator.getIdentifier(), inferred, false);
                }
            }
        });
    }
}
