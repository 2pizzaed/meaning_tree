package org.vstu.meaningtree.utils.frames;

import org.treesitter.TSNode;
import org.vstu.meaningtree.nodes.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Стек кадров обработки — второй, низкоуровневый ярус контекста разбора.
 * <p>
 * Основной способ спросить «внутри какого узла мы находимся» — первый ярус на
 * {@code TranslatorContext} ({@code isInNode}, {@code parentType}, {@code nodeTypeHierarchy},
 * {@code getEnclosingNode}). Сюда идут за тем, чего на первом ярусе нет: за самим кадром, за
 * {@link TSNode} предка, за реентерабельностью.
 * <p>
 * <b>Двигать стек может только ядро.</b> Сам {@code FrameStack} — read-only: изменяющие
 * операции живут на {@link Control}, который выдаётся один раз, при создании стека
 * ({@link #createControlled()}), и остаётся приватным полем контекста трансляции. Кому
 * достался сам стек — тот его только читает, независимо от пакета. Ядро обязано двигать стек
 * в {@code try/finally}, иначе контекст протечёт при исключении.
 * <p>
 * Пакет намеренно нейтральный (как {@code utils.scopes}): стек кадров нужен и
 * {@code languages}, и {@code utils.hooks}, а зависимость между этими двумя пакетами
 * односторонняя и должна такой остаться.
 * <p>
 * Запросы — <b>только по типам MT-узлов</b>. Отдельного API «внутри какого узла tree-sitter
 * мы находимся» нет намеренно: {@link TSNode} достаётся из кадра, найденного по типу MT-узла.
 * <p>
 * Уровни считаются от нуля: {@code 0} — текущий обрабатываемый узел, {@code 1} — на уровень
 * выше, и так далее.
 */
public final class FrameStack {

    /** Кадр viewer'а: узел построен, тип фактический. */
    private record ViewerFrame(Node viewedNode) implements Frame {
        @Override
        public Class<? extends Node> nodeType() {
            return viewedNode.getClass();
        }

        @Override
        public Optional<Node> node() {
            return Optional.of(viewedNode);
        }

        @Override
        public Optional<TSNode> sourceNode() {
            return Optional.empty();
        }
    }

    /**
     * Кадр парсера: {@link TSNode} на руках всегда, MT-узла на спуске не существует.
     * Заполняется через {@link Control#complete(Node)} перед снятием кадра.
     */
    private static final class ParserFrame implements Frame {
        private final TSNode source;
        private final Class<? extends Node> declaredType;
        private Node produced;

        private ParserFrame(TSNode source, Class<? extends Node> declaredType) {
            this.source = source;
            this.declaredType = declaredType;
        }

        @Override
        public Class<? extends Node> nodeType() {
            return declaredType;
        }

        @Override
        public Optional<Node> node() {
            return Optional.ofNullable(produced);
        }

        @Override
        public Optional<TSNode> sourceNode() {
            return Optional.of(source);
        }
    }

    /**
     * Пульт управления стеком. Выдаётся один раз вместе со стеком и никуда больше не
     * передаётся — это и есть гарантия «двигает только ядро». Package-private модификаторы
     * такой гарантии не дали бы: стек лежит в общем пакете, доступном обоим потребителям.
     */
    public static final class Control {
        private final FrameStack stack;

        private Control(FrameStack stack) {
            this.stack = stack;
        }

        /** Стек, которым управляет этот пульт. Отдавать наружу можно: он только читается. */
        public FrameStack stack() {
            return stack;
        }

        /**
         * Кадр viewer'а. Кладётся узел <b>после</b> подготовок
         * {@link org.vstu.meaningtree.utils.hooks.HookPhase#BEFORE_NODE_RENDER}: иначе после
         * замены узла подготовкой идентичность разъедется и
         * {@link FrameStack#depthOf(Node)} начнёт врать.
         */
        public void enterNode(Node node) {
            Objects.requireNonNull(node, "node must not be null");
            stack.frames.add(new ViewerFrame(node));
        }

        /** Кадр парсера: исходный узел и заявленный при регистрации тип результата. */
        public void enterSource(TSNode source, Class<? extends Node> declaredType) {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(declaredType, "declaredType must not be null");
            stack.frames.add(new ParserFrame(source, declaredType));
        }

        /**
         * Доложить в текущий кадр парсера построенный узел — после этого его видно через
         * {@link Frame#node()}.
         */
        public void complete(Node produced) {
            Frame frame = stack.top();
            if (!(frame instanceof ParserFrame parserFrame)) {
                throw new IllegalStateException("Only parser frames are completed after creation");
            }
            parserFrame.produced = produced;
        }

        public void leave() {
            stack.top();
            stack.frames.remove(stack.frames.size() - 1);
        }
    }

    /** Вершина стека — последний элемент списка. */
    private final List<Frame> frames = new ArrayList<>();

    private FrameStack() {
    }

    /**
     * Создать стек вместе с пультом управления. Единственный способ получить изменяемый
     * доступ: пульт достаётся тому, кто создал стек, и дальше не передаётся.
     */
    public static Control createControlled() {
        return new Control(new FrameStack());
    }

    private Frame top() {
        if (frames.isEmpty()) {
            throw new IllegalStateException("Frame stack is empty");
        }
        return frames.get(frames.size() - 1);
    }

    /**
     * Ближайший объемлющий кадр заданного типа, считая текущий узел: вершина стека — это то,
     * что обрабатывается сейчас. Строго «предок» выражается через {@code callFrame(1)} и далее.
     * <p>
     * Сравнение — через {@link Class#isAssignableFrom}, поэтому кадр, заявленный в парсере как
     * {@code Expression}, но строящий {@code BinaryExpression}, найдётся по {@code Expression} и
     * не найдётся по {@code BinaryExpression}. Это огрубление, а не ошибка: оно видно в точке
     * регистрации handler'а.
     */
    public Optional<Frame> nearestFrame(Class<? extends Node> type) {
        Objects.requireNonNull(type, "type must not be null");
        for (int i = frames.size() - 1; i >= 0; i--) {
            Frame frame = frames.get(i);
            if (type.isAssignableFrom(frame.nodeType())) {
                return Optional.of(frame);
            }
        }
        return Optional.empty();
    }

    /**
     * Кадр на заданном уровне: {@code 0} — текущий, {@code 1} — на уровень выше.
     * Пусто, если уровень выходит за пределы стека.
     */
    public Optional<Frame> at(int level) {
        if (level < 0) {
            throw new IllegalArgumentException("Frame level must not be negative, got " + level);
        }
        int index = frames.size() - 1 - level;
        return index < 0 ? Optional.empty() : Optional.of(frames.get(index));
    }

    /** Типы узлов от текущего наружу. */
    public List<Class<? extends Node>> nodeTypeHierarchy() {
        List<Class<? extends Node>> hierarchy = new ArrayList<>(frames.size());
        for (int i = frames.size() - 1; i >= 0; i--) {
            hierarchy.add(frames.get(i).nodeType());
        }
        return hierarchy;
    }

    /**
     * Глубина вложенности в узлы заданного типа: сколько кадров этого типа сейчас на стеке,
     * считая текущий. Ноль — мы не внутри такого узла.
     */
    public int depthOf(Class<? extends Node> type) {
        Objects.requireNonNull(type, "type must not be null");
        int depth = 0;
        for (Frame frame : frames) {
            if (type.isAssignableFrom(frame.nodeType())) {
                depth++;
            }
        }
        return depth;
    }

    /**
     * Глубина вложенности в конкретный узел (сравнение по ссылке): сколько раз ядро вошло
     * в этот же объект, не выйдя из него. Это и есть признак повторного входа — то, ради чего
     * в {@code StringBodyConstructor} держалась подсистема подавления модификаций.
     * <p>
     * В парсере на спуске узла в кадре нет, поэтому осмысленный ответ даёт только viewer.
     */
    public int depthOf(Node node) {
        Objects.requireNonNull(node, "node must not be null");
        int depth = 0;
        for (Frame frame : frames) {
            if (frame.node().orElse(null) == node) {
                depth++;
            }
        }
        return depth;
    }

    /** Число кадров на стеке. */
    public int size() {
        return frames.size();
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }
}
