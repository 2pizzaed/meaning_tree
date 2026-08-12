package org.vstu.meaningtree.utils.hooks;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.utils.frames.FrameStack;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.Optional;

/**
 * Окружение, доступное хуку во время срабатывания.
 * <p>
 * Неизменяем и не хранит снимков: {@link #scope()} и {@link #frames()} каждый раз
 * спрашивают владельца о его текущем состоянии. Благодаря этому экземпляр для фаз без {@code source} переиспользуется
 * реестром, а не создаётся на каждый узел; сохранять его в поле хука тем не менее не
 * следует — привязка к владельцу не гарантируется на будущее.
 *
 * @param owner  компонент, в котором сработала фаза
 * @param source дополнительное значение фазы либо {@code null}. Например, для
 *               {@link HookPhase#AFTER_NODE_PARSE} здесь лежит исходный
 *               {@code TSNode}.
 */
public record HookContext(HookHost owner, @Nullable Object source) {

    /**
     * Таблица областей видимости текущей трансляции.
     */
    public ScopeTable scope() {
        return owner.hookScope();
    }

    /**
     * Где владелец находится в дереве в момент срабатывания хука: стек кадров обработки,
     * от текущего узла наружу.
     * <p>
     * Узел хук получает и так, первым аргументом; отсюда он узнаёт <b>окружение</b> этого
     * узла — например, что {@code ReturnStatement} разбирается внутри конструктора, а не
     * обычного метода. Раньше такой вопрос требовал собственного состояния в хуке.
     * <p>
     * Стек живой, а не снимок: как и {@link #scope()}, метод каждый раз спрашивает владельца.
     * Читать его имеет смысл только во время срабатывания — снаружи фазы он расскажет про
     * чужой момент. Изменять стек нельзя: {@code FrameStack} отдаётся на чтение.
     * <p>
     * Что видно в кадре, зависит от компонента (см.
     * {@link org.vstu.meaningtree.utils.frames.Frame}): во viewer'е узел есть всегда, в
     * парсере на спуске его ещё не существует, а тип там — заявленный при регистрации
     * handler'а, а не фактический. Для {@link HookPhase#AFTER_NODE_PARSE} узел на вершине
     * стека уже проставлен.
     */
    public FrameStack frames() {
        return owner.hookFrames();
    }

    /**
     * Дополнительное значение фазы, если оно есть и имеет ожидаемый тип.
     */
    public <T> Optional<T> source(Class<T> type) {
        return type.isInstance(source) ? Optional.of(type.cast(source)) : Optional.empty();
    }
}
