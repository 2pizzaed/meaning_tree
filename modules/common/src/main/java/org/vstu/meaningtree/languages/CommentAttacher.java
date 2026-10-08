package org.vstu.meaningtree.languages;

import org.treesitter.TSNode;
import org.treesitter.TSTreeCursor;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.FieldDescriptor;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Comment;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Predicate;

/**
 * Расставляет комментарии исходного кода по дереву после разбора.
 * <p>
 * tree-sitter вставляет комментарий extra-узлом в любое место дерева, а парсеры языков строят
 * {@link Comment} только там, где он законный элемент, — в телах. Здесь решается, куда
 * комментарий попадёт на самом деле:
 * <ul>
 *     <li>на одной строке с кодом, после него — хвостом узла ({@link Node#getTrailingComments()}),
 *     который кончается на этой строке ближе всего перед комментарием: {@code int s = 0; // acc} —
 *     объявления, {@code for (int x : arr) { // c} — {@code arr}, {@code } // end} — оператора.
 *     Из тела, куда его положил парсер языка, такой комментарий вынимается;</li>
 *     <li>в теле без такого узла — остаётся отдельным узлом, где его положил парсер языка:
 *     {@code } else { // c} — первым в теле {@code else};</li>
 *     <li>там, где его не разобрал никто (в заголовке, в выражении), — первым узлом ближайшего
 *     следующего тела ({@code else:  # c} в Python), иначе хвостом предыдущего узла, иначе
 *     первым узлом тела предка ({@code int f(int a, // c}) или хвостом предка. Такой
 *     комментарий не теряется.</li>
 * </ul>
 * Владельцем считается только узел, который остался в дереве: парсер языка может построить
 * узел и выбросить его, как Java превращает {@code for (int i = 0; i < n; i++)} в
 * {@code RangeForLoop} без исходных init и cond.
 * <p>
 * {@link #afterParse} запоминает, какой узел построен из какого места исходника; всё остальное
 * делает {@link #finish} над готовым деревом — только тогда известно, какие узлы в него попали.
 */
final class CommentAttacher {
    /** Построенный узел и его место в исходнике. */
    private record Built(Node node, int start, int end, int endRow) {}

    /**
     * @param inBody разобран ли комментарий парсером языка, то есть лежит ли уже в дереве
     */
    private record Pending(TSNode source, Comment comment, boolean inBody) {}

    private final LanguageParser parser;

    /** Построенные узлы по байтовому диапазону; при совпадении диапазона — самый внешний. */
    private final Map<Long, Built> built = new HashMap<>();
    /** Начала комментариев, которые разобрал парсер языка. */
    private final Set<Integer> parsedComments = new HashSet<>();
    private final List<Pending> pending = new ArrayList<>();
    private TSNode root;
    /** Идёт ли разбор неразобранных комментариев: они не из тел, решать за них не нужно. */
    private boolean collecting;

    CommentAttacher(LanguageParser parser) {
        this.parser = parser;
    }

    void reset() {
        built.clear();
        parsedComments.clear();
        pending.clear();
        root = null;
        collecting = false;
    }

    /**
     * Срабатывает на каждый построенный узел ({@link org.vstu.meaningtree.utils.hooks.HookPhase#AFTER_NODE_PARSE}).
     */
    Node afterParse(TSNode source, Node node) {
        if (root == null) {
            root = topmost(source);
        }
        built.put(rangeKey(source), new Built(node, source.getStartByte(), source.getEndByte(), endRow(source)));
        if (!collecting && node instanceof Comment comment && source.isExtra()) {
            parsedComments.add(source.getStartByte());
            if (hasCodeBefore(source)) {
                pending.add(new Pending(source, comment, true));
            }
        }
        return node;
    }

    /**
     * Срабатывает на построенное дерево ({@link org.vstu.meaningtree.utils.hooks.HookPhase#AFTER_TREE_PARSE}).
     */
    void finish(MeaningTree tree) {
        try {
            if (root == null) {
                return;
            }
            if (!parseComments()) {
                detachAll(tree, node -> node instanceof Comment);
                return;
            }
            collectUnparsed(root);
            if (pending.isEmpty()) {
                return;
            }
            Set<Node> inTree = Collections.newSetFromMap(new IdentityHashMap<>());
            // Итератор, а не iterate(): тот построил бы индекс дерева, который устарел бы после вставки
            for (NodeInfo info : tree) {
                inTree.add(info.node());
            }
            List<Built> candidates = built.values().stream()
                    .filter(entry -> inTree.contains(entry.node()) && !(entry.node() instanceof Comment))
                    .toList();
            pending.sort(Comparator.comparingInt(item -> item.source().getStartByte()));
            Set<Node> moved = Collections.newSetFromMap(new IdentityHashMap<>());
            Map<CompoundStatement, Integer> blockStarts = new IdentityHashMap<>();
            for (Pending item : pending) {
                if (item.inBody()) {
                    Built owner = sameLineOwner(item.source(), candidates);
                    if (owner != null && !(owner.node() instanceof CompoundStatement)) {
                        moved.add(item.comment());
                        owner.node().addTrailingComment(freshInSourceOrder(item.comment()));
                    }
                } else {
                    place(item, freshInSourceOrder(item.comment()), candidates, inTree, blockStarts);
                }
            }
            detachAll(tree, moved::contains);
        } finally {
            reset();
        }
    }

    /**
     * Свежий id в порядке исходника: по нему генератор упорядочит комментарии одной строки, а
     * комментарии из заголовков разобраны позже комментариев тел.
     */
    private static Comment freshInSourceOrder(Comment comment) {
        return (Comment) comment.freshClone();
    }

    private void place(Pending item, Comment comment, List<Built> candidates, Set<Node> inTree,
                       Map<CompoundStatement, Integer> blockStarts) {
        TSNode source = item.source();
        Built owner = sameLineOwner(source, candidates);
        if (owner != null && !(owner.node() instanceof CompoundStatement)) {
            owner.node().addTrailingComment(comment);
            return;
        }
        Built ancestor = ancestorInTree(source, inTree);
        if (ancestor == null) {
            // Вне всякой разобранной конструкции (например, в пропущенной директиве)
            return;
        }
        CompoundStatement body = followingBody(source, ancestor, inTree);
        if (body != null) {
            insertAtStart(body, comment, blockStarts);
            return;
        }
        Built previous = previousInside(source, ancestor, candidates);
        CompoundStatement ownBody = ancestor.node() instanceof CompoundStatement block ? block : ownBody(ancestor.node());
        if (previous != null) {
            previous.node().addTrailingComment(comment);
        } else if (ownBody != null) {
            // Перед комментарием в конструкции ничего нет, он в её заголовке: хвостом конструкции
            // он ушёл бы за её конец
            insertAtStart(ownBody, comment, blockStarts);
        } else {
            ancestor.node().addTrailingComment(comment);
        }
    }

    /** Тело конструкции: её непосредственный ребёнок {@link CompoundStatement}. */
    private static CompoundStatement ownBody(Node node) {
        for (NodeInfo info : node) {
            if (info.parentNode() == node && info.node() instanceof CompoundStatement body) {
                return body;
            }
        }
        return null;
    }

    private static void insertAtStart(CompoundStatement body, Comment comment,
                                      Map<CompoundStatement, Integer> blockStarts) {
        body.insert(blockStarts.merge(body, 1, Integer::sum) - 1, comment);
    }

    /**
     * Узел дерева, который кончается на строке комментария ближе всего перед ним; при равном
     * конце — самый внешний.
     */
    private static Built sameLineOwner(TSNode comment, List<Built> candidates) {
        int row = comment.getStartPoint().getRow();
        int start = comment.getStartByte();
        Built best = null;
        for (Built candidate : candidates) {
            if (candidate.endRow() == row && candidate.end() <= start && isCloserBefore(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Ближайшее тело после комментария в пределах предка: следующий сосед комментария или
     * одного из его родителей, построенный как {@link CompoundStatement}.
     */
    private CompoundStatement followingBody(TSNode comment, Built ancestor, Set<Node> inTree) {
        for (TSNode level = comment; !level.isNull() && level.getStartByte() >= ancestor.start()
                && !isRange(level, ancestor); level = level.getParent()) {
            for (TSNode next = level.getNextSibling(); !next.isNull(); next = next.getNextSibling()) {
                Built entry = built.get(rangeKey(next));
                if (entry != null && entry.node() instanceof CompoundStatement body && inTree.contains(body)) {
                    return body;
                }
            }
        }
        return null;
    }

    /** Узел дерева внутри предка, который кончается ближе всего перед комментарием. */
    private static Built previousInside(TSNode comment, Built ancestor, List<Built> candidates) {
        int start = comment.getStartByte();
        Built best = null;
        for (Built candidate : candidates) {
            if (candidate != ancestor && candidate.start() >= ancestor.start() && candidate.end() <= start
                    && isCloserBefore(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isCloserBefore(Built candidate, Built best) {
        return best == null || candidate.end() > best.end()
                || candidate.end() == best.end() && candidate.start() < best.start();
    }

    /**
     * Ближайший построенный предок, оставшийся в дереве. Корень не подходит: комментарий вне
     * всякой разобранной конструкции оказался бы в конце программы.
     */
    private Built ancestorInTree(TSNode node, Set<Node> inTree) {
        for (TSNode current = node.getParent(); !current.isNull() && !current.equals(root);
             current = current.getParent()) {
            Built entry = built.get(rangeKey(current));
            if (entry != null && inTree.contains(entry.node())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Комментарии, которых не коснулся ни один обработчик: внутри заголовков, выражений,
     * списков аргументов. Разбираются тем же обработчиком языка, что и комментарии в телах.
     */
    private void collectUnparsed(TSNode from) {
        collecting = true;
        // Курсор, а не getChild(i): обход идёт по каждому разбору, а getChild линеен по индексу
        try (TSTreeCursor cursor = new TSTreeCursor(from)) {
            while (true) {
                TSNode current = cursor.currentNode();
                if (current.isExtra()) {
                    if (!parsedComments.contains(current.getStartByte()) && producesComment(current)
                            && parser.parseTSNode(current) instanceof Comment comment) {
                        pending.add(new Pending(current, comment, false));
                    }
                } else if (cursor.gotoFirstChild()) {
                    continue;
                }
                while (!cursor.gotoNextSibling()) {
                    if (!cursor.gotoParent()) {
                        return;
                    }
                }
            }
        } finally {
            collecting = false;
        }
    }

    private boolean producesComment(TSNode node) {
        return parser.resolveTsNodeHandler(node.getType())
                .map(handler -> Comment.class.isAssignableFrom(handler.produces()))
                .orElse(false)
                && parser.keepsUnparsedComment(node);
    }

    /**
     * Вынимает подходящие узлы из списков и массивов, где они лежат в дереве. Нужен потому,
     * что парсер языка кладёт комментарий в тело раньше, чем выясняется, что у него есть
     * владелец или что комментарии отбрасываются.
     */
    private static void detachAll(MeaningTree tree, Predicate<Node> matches) {
        List<NodeInfo> occurrences = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (matches.test(info.node()) && info.field() != null && info.isInCollection()) {
                occurrences.add(info);
            }
        }
        for (NodeInfo occurrence : occurrences) {
            detach(occurrence.field(), occurrence.node());
        }
    }

    private static void detach(FieldDescriptor field, Node node) {
        try {
            Field raw = field.getRawField();
            raw.setAccessible(true);
            Object value = raw.get(field.getOwner());
            if (value instanceof List<?> list) {
                try {
                    list.removeIf(element -> element == node);
                } catch (UnsupportedOperationException immutable) {
                    List<Object> copy = new ArrayList<>(list);
                    copy.removeIf(element -> element == node);
                    raw.set(field.getOwner(), copy);
                }
            } else if (value instanceof Node[] array) {
                Node[] kept = Arrays.stream(array).filter(element -> element != node)
                        .toArray(length -> (Node[]) Array.newInstance(array.getClass().getComponentType(), length));
                raw.set(field.getOwner(), kept);
            }
        } catch (IllegalAccessException ignored) {
            // Поле без доступа: комментарий останется на месте отдельным узлом
        }
    }

    /**
     * Есть ли на строке комментария перед ним именованный узел. Безымянные токены на этой же
     * строке ({@code {}, {@code (}, {@code ,}, {@code :}) пропускаются, а у первого ребёнка поиск
     * продолжается у родителя: так {@code for (...) { // c} находит заголовок, а {@code {} на
     * отдельной строке — нет.
     */
    private static boolean hasCodeBefore(TSNode comment) {
        int row = comment.getStartPoint().getRow();
        TSNode current = comment;
        while (true) {
            TSNode previous = current.getPrevSibling();
            if (previous.isNull()) {
                current = current.getParent();
                if (current.isNull()) {
                    return false;
                }
                continue;
            }
            if (endRow(previous) != row) {
                return false;
            }
            if (previous.isNamed() && !previous.isExtra()) {
                return true;
            }
            current = previous;
        }
    }

    /**
     * Строка, на которой кончается текст узла. Узел, захвативший завершающий перевод строки
     * (директива препроцессора), формально кончается в нулевом столбце следующей строки.
     */
    private static int endRow(TSNode node) {
        int row = node.getEndPoint().getRow();
        return node.getEndPoint().getColumn() == 0 && row > node.getStartPoint().getRow() ? row - 1 : row;
    }

    private boolean parseComments() {
        return parser.getConfigParameter("parseComments").asBoolean();
    }

    private static boolean isRange(TSNode node, Built entry) {
        return node.getStartByte() == entry.start() && node.getEndByte() == entry.end();
    }

    private static TSNode topmost(TSNode node) {
        TSNode current = node;
        while (!current.getParent().isNull()) {
            current = current.getParent();
        }
        return current;
    }

    private static long rangeKey(TSNode node) {
        return ((long) node.getStartByte() << 32) | (node.getEndByte() & 0xFFFFFFFFL);
    }
}
