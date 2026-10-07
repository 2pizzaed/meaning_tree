package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Comment;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.enums.CommentStyle;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.hooks.HookOrder;
import org.vstu.meaningtree.utils.hooks.HookPhase;
import org.vstu.meaningtree.utils.hooks.HookScope;

import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Выводит комментарии, прикреплённые к узлам ({@link Node#getTrailingComments()}), так, что
 * генераторы языков о них ничего не знают.
 * <p>
 * Вставить {@code // c} сразу после узла нельзя: {@code for (int i = 0 // c; ...} закомментировал
 * бы остаток строки. Поэтому вывод идёт в два шага, как разметка {@link SourceMapGenerator}:
 * <ol>
 *     <li>после отрисовки узла с комментариями к тексту приписывается невидимая метка
 *     ({@link HookPhase#AFTER_NODE_RENDER});</li>
 *     <li>в готовом коде метка заменяется комментарием: блочный в C/C++/Java остаётся на месте
 *     ({@code i = 0 /* c *}{@code /;}), остальные уходят в конец строки, где стоит метка. В Python
 *     многострочный комментарий печатается строками {@code #} перед этой строкой.</li>
 * </ol>
 * Узел, который генератор не отрисовал сам (заменил другим при выводе), метки не получит.
 * Его комментарии забирает ближайший отрисованный предок, кроме корня: если узел в заголовке
 * предка (не внутри его тела), метка встаёт в конец первой строки предка, иначе — в конец всего
 * предка. Комментарий узла, от которого в выводе не осталось и предка, пропадает вместе с ним.
 * <p>
 * Отдельные узлы {@link Comment} генераторы выводят сами; при {@code dropComments} они
 * отрисовываются меткой, и строка, где кроме неё ничего нет, удаляется.
 */
final class TrailingCommentRenderer {
    private static final String TAG_START = "\u2060CMT_";
    private static final String TAG_END = "\u2060CMT_END";
    private static final String DROPPED = "DROP";
    private static final Pattern TAG = Pattern.compile("\u2060CMT_(\\d+|" + DROPPED + ")\u2060CMT_END");
    /** Любая невидимая метка — эта или {@link SourceMapGenerator}: в видимый текст строки они не входят. */
    private static final Pattern ANY_TAG = Pattern.compile(TAG.pattern() + "|" + SourceMapGenerator.MARKER.pattern());
    private static final String GAP = "  ";

    private final LanguageViewer viewer;
    private final boolean drop;
    private final Map<Long, List<Comment>> commentsByOwner = new HashMap<>();
    /** Владельцы комментариев под каждым предком; {@code true} — владелец в заголовке предка. */
    private final Map<Long, Map<Long, Boolean>> ownersByAncestor = new HashMap<>();
    private final Set<Long> tagged = new HashSet<>();
    private final Set<Long> placed = new HashSet<>();

    private TrailingCommentRenderer(LanguageViewer viewer, boolean drop) {
        this.viewer = viewer;
        this.drop = drop;
    }

    /**
     * @param tree      обход отрисовываемого дерева — откуда берутся владельцы комментариев
     * @param rendering отрисовка этого дерева генератором
     */
    static String render(LanguageViewer viewer, Iterable<NodeInfo> tree, boolean drop, Supplier<String> rendering) {
        TrailingCommentRenderer renderer = new TrailingCommentRenderer(viewer, drop);
        boolean hasStandalone = renderer.index(tree);
        if (renderer.commentsByOwner.isEmpty() && !(drop && hasStandalone)) {
            return rendering.get();
        }
        String tagged;
        try (HookScope scope = viewer.hooks().openScope()) {
            scope.intercept(HookPhase.AFTER_NODE_RENDER, Node.class, HookOrder.NORMAL,
                    (node, rendered, context) -> renderer.tag(node, rendered));
            tagged = rendering.get();
        }
        return renderer.place(tagged);
    }

    /**
     * @return есть ли в дереве отдельные узлы {@link Comment}
     */
    private boolean index(Iterable<NodeInfo> tree) {
        boolean hasStandalone = false;
        for (NodeInfo info : tree) {
            Node node = info.node();
            if (node instanceof Comment && info.parent() != null
                    && info.parentNode().getTrailingComments().stream().noneMatch(comment -> comment == node)) {
                hasStandalone = true;
            }
            if (drop || !node.hasTrailingComments()) {
                continue;
            }
            commentsByOwner.put(node.getId(), node.getTrailingComments());
            boolean inHeader = !(node instanceof CompoundStatement);
            // Корень не забирает комментарии: у него нет своей строки, и комментарий узла, которого
            // в выводе нет вовсе (например, ненужного целевому языку импорта), встал бы на чужую
            for (NodeInfo ancestor = info.parent(); ancestor != null && ancestor.parent() != null;
                 ancestor = ancestor.parent()) {
                ownersByAncestor.computeIfAbsent(ancestor.id(), id -> new LinkedHashMap<>())
                        .put(node.getId(), inHeader);
                inHeader &= !(ancestor.node() instanceof CompoundStatement);
            }
        }
        return hasStandalone;
    }

    private String tag(Node node, String rendered) {
        if (drop) {
            return node instanceof Comment ? tag(DROPPED) : rendered;
        }
        String result = rendered;
        if (commentsByOwner.containsKey(node.getId()) && tagged.add(node.getId())) {
            result = appendBeforeTrailingWhitespace(result, tag(String.valueOf(node.getId())));
        }
        for (var owner : ownersByAncestor.getOrDefault(node.getId(), Map.of()).entrySet()) {
            if (!tagged.add(owner.getKey())) {
                continue;
            }
            String ownerTag = tag(String.valueOf(owner.getKey()));
            int firstLineEnd = owner.getValue() ? firstLineEnd(result) : -1;
            result = firstLineEnd < 0
                    ? appendBeforeTrailingWhitespace(result, ownerTag)
                    : result.substring(0, firstLineEnd) + ownerTag + result.substring(firstLineEnd);
        }
        return result;
    }

    private String place(String code) {
        List<String> out = new ArrayList<>();
        for (String line : code.split("\n", -1)) {
            if (line.indexOf('\u2060') < 0 || !TAG.matcher(line).find()) {
                out.add(line);
            } else {
                placeInLine(line, out);
            }
        }
        return String.join("\n", out);
    }

    private void placeInLine(String line, List<String> out) {
        String carriageReturn = line.endsWith("\r") ? "\r" : "";
        String rest = line.substring(0, line.length() - carriageReturn.length());
        while (rest != null) {
            rest = placeInSegment(rest, out, carriageReturn);
        }
    }

    /**
     * Расставляет комментарии одного отрезка строки. Строчный комментарий элемента списка
     * ({@code for (int i = 0; // c}, {@code f(a, // c}) переносит строку после разделителя: иначе
     * он ушёл бы в конец строки, оторвавшись от своего элемента.
     *
     * @return остаток строки после переноса либо {@code null}, если переноса не было
     */
    private String placeInSegment(String body, List<String> out, String carriageReturn) {
        StringBuilder text = new StringBuilder();
        List<String> before = new ArrayList<>();
        List<Comment> atEnd = new ArrayList<>();
        boolean hadDropped = false;
        String remainder = null;

        Matcher matcher = TAG.matcher(body);
        int last = 0;
        while (matcher.find()) {
            text.append(body, last, matcher.start());
            last = matcher.end();
            if (matcher.group(1).equals(DROPPED)) {
                hadDropped = true;
                continue;
            }
            long owner = Long.parseLong(matcher.group(1));
            if (!placed.add(owner)) {
                continue;
            }
            boolean toLineEnd = false;
            for (Comment comment : commentsByOwner.get(owner)) {
                if (viewer.allowsInlineComments() && comment.getStyle() != CommentStyle.LINE) {
                    if (comment.hasNewline()) {
                        atEnd.add(comment);
                    } else {
                        text.append(' ').append(viewer.toString(comment));
                    }
                } else if (comment.hasNewline()) {
                    before.addAll(List.of(asLineComment(comment).split("\n")));
                } else {
                    atEnd.add(comment);
                    toLineEnd = true;
                }
            }
            int separator = toLineEnd ? listSeparatorAt(body, last, text) : -1;
            if (separator >= 0) {
                text.append(body, last, separator + 1);
                int column = innermostOpenBracket(visible(text)) + 1;
                remainder = " ".repeat(column) + body.substring(separator + 1).stripLeading();
                last = body.length();
                break;
            }
        }
        text.append(body.substring(last));

        String visible = visible(text);
        if (hadDropped && visible.isBlank()) {
            return remainder;
        }
        String indent = visible.substring(0, visible.length() - visible.stripLeading().length());
        before.forEach(comment -> out.add(indent + comment.stripTrailing() + carriageReturn));
        String result = text.toString();
        if (!atEnd.isEmpty()) {
            // Метки владельцев стоят в порядке вывода, а не исходника; id комментариев идут по исходнику
            atEnd.sort(Comparator.comparingLong(Comment::getId));
            List<String> rendered = new ArrayList<>();
            for (Comment comment : atEnd) {
                rendered.add(comment.hasNewline() ? viewer.toString(comment) : asLineComment(comment).stripTrailing());
            }
            result = result.stripTrailing() + GAP + String.join(" ", rendered);
        }
        out.add(result + carriageReturn);
        return remainder;
    }

    /**
     * Разделитель элементов списка сразу после метки: {@code ,} или {@code ;} внутри открытой
     * скобки, за которым на строке есть следующий элемент.
     *
     * @param prefix текст строки до метки
     * @return позиция разделителя в {@code body} либо {@code -1}
     */
    private static int listSeparatorAt(String body, int from, CharSequence prefix) {
        int separator = skipInvisible(body, from);
        if (separator >= body.length() || body.charAt(separator) != ',' && body.charAt(separator) != ';') {
            return -1;
        }
        String next = visible(body.substring(separator + 1)).strip();
        if (next.isEmpty() || ")]}".indexOf(next.charAt(0)) >= 0) {
            return -1;
        }
        return innermostOpenBracket(visible(prefix)) >= 0 ? separator : -1;
    }

    private static int skipInvisible(String body, int from) {
        int position = from;
        Matcher tag = ANY_TAG.matcher(body);
        while (position < body.length()) {
            if (Character.isWhitespace(body.charAt(position))) {
                position++;
            } else if (body.charAt(position) == '\u2060' && tag.find(position) && tag.start() == position) {
                position = tag.end();
            } else {
                break;
            }
        }
        return position;
    }

    /** Позиция самой внутренней незакрытой скобки в строке либо {@code -1}; строковые литералы пропускаются. */
    private static int innermostOpenBracket(String line) {
        Deque<Integer> open = new ArrayDeque<>();
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(' || c == '[' || c == '{') {
                open.push(i);
            } else if ((c == ')' || c == ']' || c == '}') && !open.isEmpty()) {
                open.pop();
            }
        }
        return open.isEmpty() ? -1 : open.peek();
    }

    private static String visible(CharSequence text) {
        return ANY_TAG.matcher(text).replaceAll("");
    }

    /**
     * Комментарий в строчной форме языка: только она допустима в конце строки кода. Строится
     * новым узлом, привязанным к исходному, чтобы source map приписал его исходному комментарию.
     */
    private String asLineComment(Comment comment) {
        if (comment.getStyle() == CommentStyle.LINE) {
            return viewer.toString(comment);
        }
        return viewer.toString(Comment.fromUnescaped(comment.getUnescapedContent(), CommentStyle.LINE).remap(comment));
    }

    /** Конец первой непустой строки текста либо {@code -1}, если строка одна. */
    private static int firstLineEnd(String text) {
        int contentStart = 0;
        while (contentStart < text.length() && Character.isWhitespace(text.charAt(contentStart))) {
            contentStart++;
        }
        int lineEnd = text.indexOf('\n', contentStart);
        if (lineEnd > 0 && text.charAt(lineEnd - 1) == '\r') {
            lineEnd--;
        }
        return lineEnd;
    }

    /**
     * Текст узла может кончаться переводом строки; метка после него оказалась бы на следующей
     * строке, отдельно от узла.
     */
    private static String appendBeforeTrailingWhitespace(String text, String tag) {
        int end = text.length();
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + tag + text.substring(end);
    }

    private static String tag(String value) {
        return TAG_START + value + TAG_END;
    }
}
