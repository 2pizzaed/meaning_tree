package org.vstu.meaningtree.utils.frames;

import org.treesitter.TSNode;
import org.vstu.meaningtree.nodes.Node;

import java.util.Optional;

/**
 * Кадр стека обработки: узел, внутри которого ядро находится прямо сейчас.
 * <p>
 * Кадры кладёт и снимает только ядро — {@code LanguageViewer.renderPrepared} и
 * {@code LanguageParser.parseTSNode}; всем остальным стек доступен только на чтение
 * (см. {@link FrameStack}). Прикладные вопросы («внутри какого узла мы находимся») решаются
 * первым ярусом на {@code TranslatorContext}, кадр нужен только там, где требуется сам узел
 * или исходный {@link TSNode}.
 * <p>
 * <b>Кадры viewer'а и парсера различаются по наполнению, и это не дефект реализации:</b>
 * различается то, что вообще существует в момент обработки.
 * <ul>
 *   <li>Viewer: дерево построено целиком, поэтому {@link #node()} есть всегда, а
 *       {@link #nodeType()} — фактический класс узла. {@link #sourceNode()} пуст.</li>
 *   <li>Парсер: {@link #sourceNode()} есть всегда, {@link #nodeType()} — <i>заявленный</i>
 *       при регистрации тип результата ({@code registerTSNodeHandler}), а {@link #node()}
 *       пуст на спуске: {@code parseTSNode} строит дерево снизу вверх, и пока разбирается
 *       ребёнок, родительского узла ещё не существует. Узел появляется в кадре перед
 *       снятием — его видят хуки
 *       {@link org.vstu.meaningtree.utils.hooks.HookPhase#AFTER_NODE_PARSE}, но не дети.</li>
 * </ul>
 * Стек знает только узлы, прошедшие через диспетчеризацию: прямые вызовы приватных хелперов
 * мимо {@code toString} в него не попадают. Контекст отражает <b>дерево узлов</b>, а не
 * последовательность вызовов Java.
 */
public interface Frame {
    /**
     * Тип узла кадра: фактический класс во viewer'е, заявленный при регистрации — в парсере.
     * Есть всегда: на нём построены все запросы обоих ярусов.
     */
    Class<? extends Node> nodeType();

    /**
     * Сам узел. Во viewer'е присутствует всегда; в парсере пуст на спуске и заполняется
     * перед снятием кадра.
     */
    Optional<Node> node();

    /**
     * Исходный узел tree-sitter. Есть только в парсере.
     */
    Optional<TSNode> sourceNode();
}
