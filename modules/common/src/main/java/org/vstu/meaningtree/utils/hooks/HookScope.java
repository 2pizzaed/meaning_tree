package org.vstu.meaningtree.utils.hooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Область регистрации хуков на один прогон трансляции.
 * <p>
 * Предназначена для внешних потребителей, которым нужно встроиться в трансляцию временно:
 *
 * <pre>{@code
 * try (HookScope scope = viewer.hooks().openScope()) {
 *     scope.intercept(HookPhase.AFTER_NODE_RENDER, Node.class, HookOrder.LATE, watermarker);
 *     code = translator.getCode(tree);
 * }
 * }</pre>
 *
 * Хуки области снимаются при её закрытии либо при завершении трансляции — смотря что
 * случится раньше; снятие идемпотентно, поэтому оба пути безопасны.
 */
public final class HookScope implements AutoCloseable {

    private final HookRegistry registry;
    private final List<HookHandle> handles = new ArrayList<>();

    HookScope(HookRegistry registry) {
        this.registry = registry;
    }

    public <S, V> HookHandle intercept(HookPhase<S, V> phase, HookOrder order, Interceptor<S, V> interceptor) {
        return track(registry.register(registry.interceptorStore(), phase, null, order, interceptor, true));
    }

    public <S, V, T extends S> HookHandle intercept(HookPhase<S, V> phase, Class<T> subjectType, HookOrder order,
                                                    Interceptor<T, V> interceptor) {
        return track(registry.register(registry.interceptorStore(), phase, subjectType, order, interceptor, true));
    }

    public <S, V> HookHandle observe(HookPhase<S, V> phase, HookOrder order, Listener<S, V> listener) {
        return track(registry.register(registry.listenerStore(), phase, null, order, listener, true));
    }

    public <S, V, T extends S> HookHandle observe(HookPhase<S, V> phase, Class<T> subjectType, HookOrder order,
                                                  Listener<T, V> listener) {
        return track(registry.register(registry.listenerStore(), phase, subjectType, order, listener, true));
    }

    private HookHandle track(HookHandle handle) {
        handles.add(handle);
        return handle;
    }

    @Override
    public void close() {
        handles.forEach(HookHandle::remove);
        handles.clear();
    }
}
