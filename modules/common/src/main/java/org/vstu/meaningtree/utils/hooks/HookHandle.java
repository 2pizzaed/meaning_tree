package org.vstu.meaningtree.utils.hooks;

/**
 * Ссылка на зарегистрированный хук, позволяющая его снять.
 * <p>
 * Реализует {@link AutoCloseable}, поэтому одиночную регистрацию можно ограничить блоком
 * {@code try}-with-resources. Снятие идемпотентно: повторный вызов ничего не делает, что
 * важно, поскольку хуки прогона могут быть сняты раньше — при завершении трансляции.
 */
public interface HookHandle extends AutoCloseable {

    /**
     * Снимает хук. Повторный вызов безопасен.
     */
    void remove();

    /**
     * @return {@code true}, если хук всё ещё зарегистрирован
     */
    boolean isActive();

    @Override
    default void close() {
        remove();
    }
}
