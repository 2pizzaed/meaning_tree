package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.nodes.Node;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class StringBodyConstructor extends BodyConstructor {
    private enum SuppressionKind {
        ANY,
        NODE_SPECIFIC
    }

    private record CallerFrame(String className, String methodName) {}

    private record PendingSuppression(SuppressionKind kind, Node node, CallerFrame callerFrame, int remainingUses) {}

    private static final class ActiveSuppression {
        private final SuppressionKind kind;
        private final Node node;
        private final CallerFrame callerFrame;
        private int remainingUses;

        private ActiveSuppression(PendingSuppression suppression) {
            this.kind = suppression.kind();
            this.node = suppression.node();
            this.callerFrame = suppression.callerFrame();
            this.remainingUses = suppression.remainingUses();
        }

        private boolean matches(Node candidate, CallerFrame currentCaller) {
            if (callerFrame != null && !Objects.equals(callerFrame, currentCaller)) {
                return false;
            }
            return kind == SuppressionKind.ANY || node == candidate;
        }

        private boolean consume() {
            if (remainingUses < 0) {
                return false;
            }
            remainingUses--;
            return remainingUses == 0;
        }
    }

    private List<String> stringBuffer = new ArrayList<>();
    private int indentCount = 0;
    private String indentString = " ";
    private final List<PendingSuppression> pendingSuppressions = new ArrayList<>();
    private final List<ActiveSuppression> activeSuppressions = new ArrayList<>();

    public StringBodyConstructor(TranslatorContext ctx, boolean newScope) {
        super(ctx, newScope);
        if (!(ctx.owner instanceof LanguageViewer)) {
            throw new IllegalStateException("Cannot create StringBodyConstructor when owner is not a LanguageViewer");
        }
    }

    public StringBodyConstructor indent(int count) {
        return indent(count, "    ");
    }

    public String withIndent(String str) {
        if (indentCount <= 0 || str.isEmpty()) {
            return str;
        }

        String indent = indentString.repeat(indentCount);
        String[] lines = str.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].isBlank()) {
                lines[i] = indent + lines[i];
            }
        }
        return String.join(System.lineSeparator(), lines);
    }

    public StringBodyConstructor indent(int count, String indent) {
        this.indentString = indent;
        this.indentCount = count;
        return this;
    }

    private LanguageViewer getViewer() {
        return (LanguageViewer) ctx.owner;
    }

    public List<String> stringBuffer() {
        return List.copyOf(stringBuffer);
    }

    public void appendString(String string) {
        stringBuffer.add(string);
    }

    public void appendStringWithIndent(String string) {
        stringBuffer.add(withIndent(string));
    }

    public String getStringAt(int index) {
        return stringBuffer.get(index);
    }

    public void substituteString(int index, String string) {
        stringBuffer.set(index, string);
    }

    public void substituteStringWithIndent(int index, String string) {
        stringBuffer.set(index, withIndent(string));
    }

    /**
     * Разрешает текущую модификацию конструктора, но подавляет первую вложенную модификацию,
     * которая произойдет во время вызова viewer для получения строки узла
     *
     * Если `onlyInCurrentMethod == true`, подавление применяется только для того метода,
     * из которого этот вызов был сделан.
     */
    public StringBodyConstructor stopModificationPropagation(boolean onlyInCurrentMethod) {
        pendingSuppressions.add(new PendingSuppression(
                SuppressionKind.ANY,
                null,
                onlyInCurrentMethod ? resolveExternalCaller() : null,
                1
        ));
        return this;
    }

    /**
     * Подавляет последующие вложенные модификации, связанные с конкретным узлом,
     * во время последующего `getViewer().toString(...)`.
     *
     * Под "связанными" понимаются операции `add/insert/substitute`, в которые передается
     * тот же объект `node` по ссылке.
     *
     * Если `onlyInCurrentMethod == true`, подавление применяется только для того метода,
     * из которого этот вызов был сделан.
     */
    public Node suppressNextModifications(Node node, boolean onlyInCurrentMethod) {
        pendingSuppressions.add(new PendingSuppression(
                SuppressionKind.NODE_SPECIFIC,
                node,
                onlyInCurrentMethod ? resolveExternalCaller() : null,
                -1
        ));
        return node;
    }

    public StringBodyConstructor removeSuppression(Node node) {
        pendingSuppressions.removeIf(suppression ->
                suppression.kind() == SuppressionKind.NODE_SPECIFIC && suppression.node() == node
        );
        activeSuppressions.removeIf(suppression ->
                suppression.kind == SuppressionKind.NODE_SPECIFIC && suppression.node == node
        );
        return this;
    }

    private void armPendingSuppressions() {
        for (PendingSuppression suppression : pendingSuppressions) {
            activeSuppressions.add(new ActiveSuppression(suppression));
        }
        pendingSuppressions.clear();
    }

    private int normalizeStringBufferIndex(int index) {
        return Math.max(0, Math.min(index, stringBuffer.size()));
    }

    private boolean shouldSuppressModification(Node node) {
        CallerFrame caller = resolveExternalCaller();
        Iterator<ActiveSuppression> iterator = activeSuppressions.iterator();
        while (iterator.hasNext()) {
            ActiveSuppression suppression = iterator.next();
            if (!suppression.matches(node, caller)) {
                continue;
            }
            if (suppression.consume()) {
                iterator.remove();
            }
            return true;
        }
        return false;
    }

    private CallerFrame resolveExternalCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String bodyConstructorClass = BodyConstructor.class.getName();
        String stringBodyConstructorClass = StringBodyConstructor.class.getName();
        String contextClass = TranslatorContext.class.getName();

        for (StackTraceElement frame : stack) {
            String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(bodyConstructorClass)
                    || className.equals(stringBodyConstructorClass)
                    || className.equals(contextClass)
                    || className.startsWith(contextClass + "$")) {
                continue;
            }
            return new CallerFrame(className, resolveMethodName(frame));
        }
        return null;
    }

    private String resolveMethodName(StackTraceElement frame) {
        try {
            Class<?> cls = Class.forName(frame.getClassName());
            for (Method method : cls.getDeclaredMethods()) {
                if (method.getName().equals(frame.getMethodName())) {
                    return method.getName();
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        return frame.getMethodName();
    }

    @Override
    protected void afterInsert(int index) {
        if (iterator != null && index <= iterator.index + 1) {
            iterator.index++;
        }
    }

    @Override
    protected void afterRemove(int index) {
        if (iterator != null && index <= iterator.index) {
            iterator.index--;
        }
    }

    public StringBodyConstructor add(Node node) {
        if (shouldSuppressModification(node)) {
            return this;
        }
        super.add(node);
        armPendingSuppressions();
        stringBuffer.add(withIndent(getViewer().toString(node)));
        return this;
    }

    public StringBodyConstructor insert(int index, Node node) {
        if (shouldSuppressModification(node)) {
            return this;
        }
        super.insert(index, node);
        armPendingSuppressions();
        stringBuffer.add(normalizeStringBufferIndex(index), withIndent(getViewer().toString(node)));
        return this;
    }

    public StringBodyConstructor substitute(int index, Node node) {
        if (shouldSuppressModification(node)) {
            return this;
        }
        super.substitute(index, node);
        armPendingSuppressions();
        stringBuffer.set(index, withIndent(getViewer().toString(node)));
        return this;
    }

    public StringBodyConstructor insertBeforeLast(int index, Node node) {
        if (shouldSuppressModification(node)) {
            return this;
        }
        int insertIndex = currentNodeIndex() + index;
        super.insert(insertIndex, node);
        armPendingSuppressions();
        stringBuffer.add(
                normalizeStringBufferIndex(insertIndex),
                withIndent(getViewer().toString(node))
        );
        return this;
    }

    public static StringBodyConstructor createFrom(TranslatorContext ctx, boolean newScope, List<Node> nodes) {
        var result = new StringBodyConstructor(ctx, newScope);
        result.nodes = new ArrayList<>(nodes);
        return result;
    }

    @Override
    public BodyConstructor remove(int index) {
        if (shouldSuppressModification(nodes.get(index))) {
            return this;
        }
        super.remove(index);
        stringBuffer.remove(index);
        return this;
    }
}
