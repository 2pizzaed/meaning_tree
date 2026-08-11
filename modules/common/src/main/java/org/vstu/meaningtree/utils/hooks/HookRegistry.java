package org.vstu.meaningtree.utils.hooks;

import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Реестр хуков компонента трансляции — единственная реализация логики хуков в проекте.
 * <p>
 * Посредник между компонентом (парсер, viewer, токенизатор) и встроенным в него
 * поведением. Компонент не знает, кто и зачем в него встроился: он лишь объявляет фазы и
 * прогоняет через них значения вызовом {@link #run}.
 *
 * <h2>Два вида хуков</h2>
 * {@link Interceptor} участвует в конвейере и возвращает значение; {@link Listener} только
 * наблюдает. В пределах фазы сначала применяются все перехватчики (в порядке
 * {@link HookOrder}, внутри порядка — по времени регистрации), затем вызываются все
 * наблюдатели, уже с итоговым значением.
 *
 * <h2>Два яруса жизни</h2>
 * <ul>
 *     <li><b>Языковые хуки</b> регистрируются компонентом при создании и живут столько же,
 *     сколько он: расстановка скобок, метки переходов.</li>
 *     <li><b>Хуки прогона</b> регистрируются внешним потребителем на одну трансляцию через
 *     {@link #openScope()} и снимаются при завершении прогона
 *     ({@link #clearRunScoped()} вызывается из {@code rollbackContext()}).</li>
 * </ul>
 * Разделение существует затем, чтобы внешний потребитель не был вынужден хранить состояние
 * своего хука в статике ради переживания границ трансляции.
 */
public final class HookRegistry {

    private final HookHost host;

    /**
     * Окружение для фаз без дополнительного значения — подавляющего большинства вызовов.
     * <p>
     * Переиспользуется, а не создаётся заново, потому что {@link HookContext} не хранит
     * снимок состояния: {@link HookContext#scope()} каждый раз спрашивает владельца о его
     * текущей таблице областей видимости. Поэтому один экземпляр остаётся корректным и
     * после пересоздания контекста трансляции.
     */
    private final HookContext sharedContext;

    private final Map<HookPhase<?, ?>, List<Registration>> interceptors = new HashMap<>();
    private final Map<HookPhase<?, ?>, List<Registration>> listeners = new HashMap<>();

    private long sequence = 0;

    public HookRegistry(HookHost host) {
        this.host = Objects.requireNonNull(host, "host must not be null");
        this.sharedContext = new HookContext(host, null);
    }

    /* --------------------------- Регистрация --------------------------- */

    public <S, V> HookHandle intercept(HookPhase<S, V> phase, Interceptor<S, V> interceptor) {
        return intercept(phase, HookOrder.NORMAL, interceptor);
    }

    public <S, V> HookHandle intercept(HookPhase<S, V> phase, HookOrder order, Interceptor<S, V> interceptor) {
        return register(interceptors, phase, null, order, interceptor, false);
    }

    public <S, V, T extends S> HookHandle intercept(HookPhase<S, V> phase, Class<T> subjectType,
                                                    Interceptor<T, V> interceptor) {
        return intercept(phase, subjectType, HookOrder.NORMAL, interceptor);
    }

    public <S, V, T extends S> HookHandle intercept(HookPhase<S, V> phase, Class<T> subjectType, HookOrder order,
                                                    Interceptor<T, V> interceptor) {
        return register(interceptors, phase, subjectType, order, interceptor, false);
    }

    public <S, V> HookHandle observe(HookPhase<S, V> phase, Listener<S, V> listener) {
        return observe(phase, HookOrder.NORMAL, listener);
    }

    public <S, V> HookHandle observe(HookPhase<S, V> phase, HookOrder order, Listener<S, V> listener) {
        return register(listeners, phase, null, order, listener, false);
    }

    public <S, V, T extends S> HookHandle observe(HookPhase<S, V> phase, Class<T> subjectType,
                                                  Listener<T, V> listener) {
        return observe(phase, subjectType, HookOrder.NORMAL, listener);
    }

    public <S, V, T extends S> HookHandle observe(HookPhase<S, V> phase, Class<T> subjectType, HookOrder order,
                                                  Listener<T, V> listener) {
        return register(listeners, phase, subjectType, order, listener, false);
    }

    /**
     * Открывает область регистрации хуков на один прогон трансляции.
     * <p>
     * Зарегистрированные в ней хуки снимаются либо при закрытии области, либо при
     * завершении трансляции — смотря что случится раньше.
     */
    public HookScope openScope() {
        return new HookScope(this);
    }

    /* ------------------------------ Запуск ------------------------------ */

    public <S, V> V run(HookPhase<S, V> phase, S subject, V value) {
        return run(phase, subject, value, null);
    }

    /**
     * Прогоняет значение через фазу.
     *
     * @param subject субъект фазы, по которому фильтруются хуки
     * @param value   значение, текущее по конвейеру
     * @param source  дополнительное значение фазы, доступное через
     *                {@link HookContext#source(Class)}
     * @return значение после всех перехватчиков
     */
    @SuppressWarnings("unchecked")
    public <S, V> V run(HookPhase<S, V> phase, S subject, V value, @Nullable Object source) {
        List<Registration> phaseInterceptors = interceptors.get(phase);
        List<Registration> phaseListeners = listeners.get(phase);
        if (phaseInterceptors == null && phaseListeners == null) {
            return value;
        }

        // Фазы без дополнительного значения (все, кроме AFTER_NODE_PARSE) обходятся общим
        // экземпляром: он неизменяем и ничего не кэширует, поэтому разделять его безопасно.
        HookContext context = source == null ? sharedContext : new HookContext(host, source);
        V current = value;
        S currentSubject = subject;

        if (phaseInterceptors != null) {
            for (Registration registration : phaseInterceptors) {
                if (!registration.matches(currentSubject)) {
                    continue;
                }
                V produced = ((Interceptor<S, V>) registration.hook).intercept(currentSubject, current, context);
                current = Objects.requireNonNull(
                        produced,
                        "Interceptor for phase %s returned null; a hook must return its input when it has nothing to do"
                                .formatted(phase)
                );
                if (phase.isSelfUpdating()) {
                    // Перехватчик мог подменить узел на узел другого типа — дальше по цепочке
                    // фильтровать надо уже по новому типу.
                    currentSubject = (S) current;
                }
            }
        }

        if (phaseListeners != null) {
            for (Registration registration : phaseListeners) {
                if (!registration.matches(currentSubject)) {
                    continue;
                }
                ((Listener<S, V>) registration.hook).observe(currentSubject, current, context);
            }
        }

        return current;
    }

    /**
     * @return есть ли у фазы хотя бы один хук. Позволяет вызывающему пропустить подготовку
     * данных для фазы, если встраиваться в неё некому.
     */
    public boolean hasHooks(HookPhase<?, ?> phase) {
        return interceptors.containsKey(phase) || listeners.containsKey(phase);
    }

    /* ---------------------------- Жизненный цикл ---------------------------- */

    /**
     * Снимает все хуки прогона, оставляя языковые. Вызывается при завершении трансляции.
     */
    public void clearRunScoped() {
        interceptors.values().forEach(list -> list.removeIf(registration -> registration.runScoped));
        listeners.values().forEach(list -> list.removeIf(registration -> registration.runScoped));
        interceptors.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        listeners.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /* ------------------------------ Внутреннее ------------------------------ */

    <S, V, T extends S> HookHandle register(Map<HookPhase<?, ?>, List<Registration>> target,
                                            HookPhase<S, V> phase,
                                            @Nullable Class<T> subjectType,
                                            HookOrder order,
                                            Object hook,
                                            boolean runScoped) {
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(order, "order must not be null");
        Objects.requireNonNull(hook, "hook must not be null");

        Registration registration = new Registration(target, phase, subjectType, order, hook, runScoped, sequence++);
        List<Registration> list = target.computeIfAbsent(phase, key -> new ArrayList<>());
        list.add(registration);
        list.sort(Comparator
                .comparingInt((Registration item) -> item.order.ordinal())
                .thenComparingLong(item -> item.sequence));
        return registration;
    }

    Map<HookPhase<?, ?>, List<Registration>> interceptorStore() {
        return interceptors;
    }

    Map<HookPhase<?, ?>, List<Registration>> listenerStore() {
        return listeners;
    }

    static final class Registration implements HookHandle {
        private final Map<HookPhase<?, ?>, List<Registration>> owner;
        private final HookPhase<?, ?> phase;
        private final @Nullable Class<?> subjectType;
        private final HookOrder order;
        private final Object hook;
        private final boolean runScoped;
        private final long sequence;

        private Registration(Map<HookPhase<?, ?>, List<Registration>> owner, HookPhase<?, ?> phase,
                             @Nullable Class<?> subjectType, HookOrder order, Object hook,
                             boolean runScoped, long sequence) {
            this.owner = owner;
            this.phase = phase;
            this.subjectType = subjectType;
            this.order = order;
            this.hook = hook;
            this.runScoped = runScoped;
            this.sequence = sequence;
        }

        boolean matches(Object subject) {
            return subjectType == null || subjectType.isInstance(subject);
        }

        @Override
        public void remove() {
            List<Registration> list = owner.get(phase);
            if (list == null) {
                return;
            }
            list.remove(this);
            if (list.isEmpty()) {
                owner.remove(phase);
            }
        }

        @Override
        public boolean isActive() {
            List<Registration> list = owner.get(phase);
            return list != null && list.contains(this);
        }
    }
}
