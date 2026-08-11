package org.vstu.meaningtree.utils.hooks;

import org.treesitter.TSNode;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.tokens.TokenList;

/**
 * Типизированный идентификатор фазы трансляции, в которую можно встроиться.
 * <p>
 * Параметры типа задают контракт фазы и проверяются компилятором при регистрации хука:
 * <ul>
 *     <li>{@code S} — <b>субъект</b>: то, по чему хук фильтруется (обычно узел дерева);</li>
 *     <li>{@code V} — <b>значение</b>: то, что течёт по конвейеру и что перехватчик может
 *     заменить.</li>
 * </ul>
 * Для фаз, где субъект и значение — одно и то же (подготовка узла перед рендерингом,
 * постобработка дерева), оба параметра совпадают, и в {@link HookRegistry#run} передаётся
 * один и тот же объект.
 * <p>
 * Дополнительное окружение фазы (например, исходный {@link TSNode} для разобранного узла)
 * доступно хуку через {@link HookContext#source(Class)} — так параметры типа не
 * размножаются ради данных, нужных меньшинству хуков.
 */
public final class HookPhase<S, V> {
    private final String name;
    private final boolean selfUpdating;

    private HookPhase(String name, boolean selfUpdating) {
        this.name = name;
        this.selfUpdating = selfUpdating;
    }

    /** Фаза, где субъект и значение — разные сущности (узел и его отрисованная строка). */
    private static <S, V> HookPhase<S, V> of(String name) {
        return new HookPhase<>(name, false);
    }

    /**
     * Фаза, где субъект и значение — одно и то же: перехватчик, заменивший значение,
     * заменяет и субъект.
     */
    private static <T> HookPhase<T, T> selfUpdating(String name) {
        return new HookPhase<>(name, true);
    }

    public String getName() {
        return name;
    }

    /**
     * Заменяет ли перехватчик вместе со значением и субъект фильтрации.
     * <p>
     * Существенно для цепочек: если один перехватчик подменил узел на узел другого типа,
     * следующие должны фильтроваться уже по новому типу, а не по исходному. Иначе,
     * например, хук, зарегистрированный на {@code SimpleIdentifier}, сработал бы на узле,
     * который предыдущий перехватчик уже завернул в скобки.
     */
    public boolean isSelfUpdating() {
        return selfUpdating;
    }

    @Override
    public String toString() {
        return name;
    }

    /* ------------------------------ Парсер ------------------------------ */

    /**
     * Узел построен из {@link TSNode} и готов занять место в дереве.
     * <p>
     * Субъект и значение — созданный узел. Исходный узел tree-sitter доступен через
     * {@code context.source(TSNode.class)}.
     */
    public static final HookPhase<Node, Node> AFTER_NODE_PARSE = selfUpdating("AFTER_NODE_PARSE");

    /**
     * Дерево полностью построено, таблица символов парсера ещё жива.
     * <p>
     * Единственное окно, в котором доступен результат разбора вместе с областями
     * видимости: сразу после этой фазы {@code rollbackContext()} уничтожает контекст
     * парсера. Здесь работают анализаторы — {@code SymbolResolver},
     * {@code ExpressionValueEvaluator}, {@code LoopIterationAnalyzer}.
     */
    public static final HookPhase<MeaningTree, MeaningTree> AFTER_TREE_PARSE = selfUpdating("AFTER_TREE_PARSE");

    /* ------------------------------ Viewer ------------------------------ */

    /**
     * Дерево получено на рендеринг, но ещё не проверялось на поддержку целевым языком.
     * Перехватчик может подменить дерево целиком.
     */
    public static final HookPhase<MeaningTree, MeaningTree> BEFORE_TREE_RENDER = selfUpdating("BEFORE_TREE_RENDER");

    /**
     * Узел готов к рендерингу. Перехватчик может вернуть другой узел — именно он и будет
     * отрисован. Здесь работает расстановка скобок по приоритетам операций.
     */
    public static final HookPhase<Node, Node> BEFORE_NODE_RENDER = selfUpdating("BEFORE_NODE_RENDER");

    /**
     * Узел отрисован в строку. Перехватчик может изменить или обернуть результат.
     * Фаза срабатывает на каждом узле рекурсии, включая корневой.
     */
    public static final HookPhase<Node, String> AFTER_NODE_RENDER = of("AFTER_NODE_RENDER");

    /**
     * Всё дерево отрисовано. Место для того, что относится к результату целиком:
     * финальное форматирование, заголовки, нормализация переводов строк.
     */
    public static final HookPhase<MeaningTree, String> AFTER_TREE_RENDER = of("AFTER_TREE_RENDER");

    /* ---------------------------- Токенизатор ---------------------------- */

    /**
     * Токенизатор входит в узел tree-sitter. Значение — накопленный список токенов.
     */
    public static final HookPhase<TSNode, TokenList> BEFORE_TOKEN_COLLECT = of("BEFORE_TOKEN_COLLECT");

    /**
     * Список токенов изменён. Субъект описывает само изменение.
     */
    public static final HookPhase<TokenListChange, TokenList> ON_TOKEN_LIST_CHANGE = of("ON_TOKEN_LIST_CHANGE");

    /**
     * Токенизация завершена. Субъект и значение — итоговый список токенов.
     */
    public static final HookPhase<TokenList, TokenList> AFTER_TOKENIZE = selfUpdating("AFTER_TOKENIZE");
}
