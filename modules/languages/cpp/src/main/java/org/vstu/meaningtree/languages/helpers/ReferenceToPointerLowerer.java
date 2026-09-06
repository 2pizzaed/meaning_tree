package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;

import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerMemberAccess;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.types.builtin.PointerType;
import org.vstu.meaningtree.nodes.types.builtin.ReferenceType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Выражает ссылки указателями: {@code int& a} становится {@code int* a}, обращение к ссылке —
 * разыменованием, а передача по ссылке — взятием адреса.
 * <p>
 * Нужен там, где ссылки нет в языке (Си) или где она нежелательна по стилю (C++ с включённым
 * {@code representReferencesAsPointers}). Переписать один только тип нельзя: ссылка и указатель
 * различаются не написанием типа, а тем, как через них обращаются к значению, — поэтому проход
 * трогает и объявление, и каждое использование, и место вызова.
 * <p>
 * Что делается:
 * <ul>
 *   <li>тип {@link ReferenceType} заменяется на {@link PointerType} везде, где встретился;</li>
 *   <li>каждое чтение и запись ссылочной переменной оборачивается в {@link PointerUnpackOp};</li>
 *   <li>обращение к члену через ссылку становится {@link PointerMemberAccess} ({@code p->f}),
 *       а не разыменованием со следующей точкой: так печатается идиоматичнее, а смысл тот же;</li>
 *   <li>инициализатор ссылочной переменной и {@code return} функции со ссылочным типом
 *       возврата оборачиваются в {@link PointerPackOp};</li>
 *   <li>аргумент вызова в позиции ссылочного параметра оборачивается в {@link PointerPackOp}.</li>
 * </ul>
 * Парные обёртки, возникшие на стыке этих правил ({@code &*e} и {@code *&e}), снимаются: без
 * этого передача ссылочной переменной в ссылочный параметр дала бы {@code f(&*a)} вместо
 * {@code f(a)}.
 * <p>
 * Ограничение — разрешение вызовов. Место вызова сопоставляется с объявлением по имени и числу
 * аргументов, а не через {@code OverloadCallResolver}: тому нужны таблица областей видимости и
 * правила преобразований исходного языка, которых у прохода нет. Если имя с таким числом
 * аргументов объявлено в дереве не один раз и объявления расходятся в том, какие параметры
 * ссылочные, вызов не трогается: молча взять адрес не в той позиции хуже, чем оставить место
 * вызова человеку. Вызовы функций, объявленных вне переводимого текста, по той же причине
 * остаются как есть — их сигнатуры проходу не видны.
 * <p>
 * Проход работает на клоне входного дерева и никогда не меняет дерево вызывающего.
 */
public final class ReferenceToPointerLowerer {
    private ReferenceToPointerLowerer() {
    }

    public static MeaningTree lower(MeaningTree source) {
        if (!hasReferences(source.getRootNode())) {
            return source;
        }

        MeaningTree result = new MeaningTree(source.getRootNode().clone());
        Node root = result.getRootNode();

        Map<String, boolean[]> referenceParameters = indexReferenceParameters(root);
        for (NodeInfo info : root.iterate(true)) {
            if (info.node() instanceof FunctionDefinition definition) {
                rewriteFunction(definition);
            }
        }
        rewriteCallSites(root, referenceParameters);
        packReferenceInitializers(root);
        replaceTypes(root);
        collapsePairedOperators(root);

        result.invalidateCache();
        return result;
    }

    private static boolean hasReferences(Node root) {
        for (NodeInfo info : root.iterate(true)) {
            if (info.node() instanceof ReferenceType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ссылочные позиции параметров каждой объявленной функции, ключ — имя и число аргументов.
     * Значение {@code null} помечает имя, объявления которого разошлись: такой вызов
     * переписывать нельзя, см. ограничение в описании класса.
     */
    private static Map<String, boolean[]> indexReferenceParameters(Node root) {
        Map<String, boolean[]> index = new HashMap<>();
        Set<String> conflicting = new HashSet<>();
        for (NodeInfo info : root.iterate(true)) {
            if (!(info.node() instanceof FunctionDeclaration declaration)) {
                continue;
            }
            List<DeclarationArgument> arguments = declaration.getArguments();
            boolean[] positions = new boolean[arguments.size()];
            for (int i = 0; i < arguments.size(); i++) {
                positions[i] = arguments.get(i).getType() instanceof ReferenceType;
            }
            // Учитываются и объявления вовсе без ссылочных параметров: именно они чаще всего и
            // расходятся с одноимённым ссылочным, и пропустить их значило бы не заметить спор
            String key = callKey(declaration.getName().toString(), arguments.size());
            boolean[] known = index.get(key);
            if (known != null && !Arrays.equals(known, positions)) {
                conflicting.add(key);
            }
            index.put(key, positions);
        }
        conflicting.forEach(index::remove);
        index.values().removeIf(positions -> !hasAny(positions));
        return index;
    }

    private static boolean hasAny(boolean[] positions) {
        for (boolean position : positions) {
            if (position) {
                return true;
            }
        }
        return false;
    }

    private static String callKey(String name, int arity) {
        return name + "/" + arity;
    }

    /**
     * Тело функции: использования её ссылочных имён становятся разыменованием, а {@code return}
     * при ссылочном типе возврата — взятием адреса.
     */
    private static void rewriteFunction(FunctionDefinition definition) {
        FunctionDeclaration declaration = definition.getDeclaration();
        Set<String> references = new HashSet<>();
        for (DeclarationArgument argument : declaration.getArguments()) {
            if (argument.getType() instanceof ReferenceType) {
                // Параметр без имени переписывать не в чем: обращений к нему в теле нет
                if (argument.hasName()) {
                    references.add(argument.getName().toString());
                }
            }
        }
        for (NodeInfo info : definition.iterate(true)) {
            if (info.node() instanceof VariableDeclaration variable
                    && variable.getType() instanceof ReferenceType) {
                for (VariableDeclarator declarator : variable.getDeclarators()) {
                    references.add(declarator.getIdentifier().toString());
                }
            }
        }
        Node body = definition.getBody();
        // Ссылочный тип возврата не зависит от того, есть ли у функции ссылочные имена:
        // int& get() { return shared; } не имеет ни одного, но адрес взять обязано
        if (declaration.getReturnType() instanceof ReferenceType) {
            body.replaceAll(
                    info -> info.node() instanceof ReturnStatement statement && statement.getExpression() != null,
                    node -> new ReturnStatement(new PointerPackOp(((ReturnStatement) node).getExpression()).remap(node))
                            .remap(node)
            );
        }
        if (references.isEmpty()) {
            return;
        }

        body.replaceAll(
                info -> isMemberAccessThroughReference(info.node(), references),
                node -> {
                    MemberAccess access = (MemberAccess) node;
                    return new PointerMemberAccess(access.getExpression(), access.getMember()).remap(access);
                }
        );
        body.replaceAll(
                info -> isReferenceUsage(info, references),
                node -> new PointerUnpackOp((Expression) node).remap(node)
        );
    }

    private static boolean isMemberAccessThroughReference(Node node, Set<String> references) {
        return node instanceof MemberAccess access
                && !(node instanceof PointerMemberAccess)
                && access.getExpression() instanceof SimpleIdentifier identifier
                && references.contains(identifier.toString());
    }

    /**
     * Использование ссылочного имени — то есть идентификатор, стоящий как значение, а не как
     * объявляемое или называемое имя. Отсев по полю родителя: {@code identifier} объявителя и
     * {@code name} параметра называют переменную, {@code member} — поле, {@code function} —
     * вызываемое; разыменовывать там нечего.
     */
    private static boolean isReferenceUsage(NodeInfo info, Set<String> references) {
        if (!(info.node() instanceof SimpleIdentifier identifier) || !references.contains(identifier.toString())) {
            return false;
        }
        Node parent = info.parentNode();
        if (parent instanceof VariableDeclarator || parent instanceof DeclarationArgument
                || parent instanceof FunctionDeclaration) {
            return false;
        }
        String field = info.field() == null ? null : info.field().getName();
        // Стрелка уже разыменовывает: обёртка поверх неё дала бы (*p)->f вместо p->f
        if (parent instanceof PointerMemberAccess && "expression".equals(field)) {
            return false;
        }
        return !"member".equals(field) && !"function".equals(field);
    }

    /** Инициализатор ссылочной переменной: {@code int& r = v} становится {@code int* r = &v}. */
    private static void packReferenceInitializers(Node root) {
        for (NodeInfo info : root.iterate(true)) {
            if (!(info.node() instanceof VariableDeclaration variable)
                    || !(variable.getType() instanceof ReferenceType)) {
                continue;
            }
            for (VariableDeclarator declarator : variable.getDeclarators()) {
                Expression value = declarator.getRValue();
                if (value != null) {
                    declarator.setRValue(new PointerPackOp(value).remap(value));
                }
            }
        }
    }

    /** Аргументы в ссылочных позициях: {@code f(x)} становится {@code f(&x)}. */
    private static void rewriteCallSites(Node root, Map<String, boolean[]> referenceParameters) {
        if (referenceParameters.isEmpty()) {
            return;
        }
        // Заменяется сам узел аргумента, а не список: getArguments() отдаёт список только для
        // чтения, и переписать его на месте нельзя
        root.replaceAll(
                info -> isReferenceArgument(info, referenceParameters),
                node -> new PointerPackOp((Expression) node).remap(node)
        );
    }

    private static boolean isReferenceArgument(NodeInfo info, Map<String, boolean[]> referenceParameters) {
        if (!(info.parentNode() instanceof FunctionCall call) || !call.hasFunctionName()
                || info.field() == null || !"arguments".equals(info.field().getName())
                || !info.field().isIndexed()) {
            return false;
        }
        boolean[] positions = referenceParameters.get(
                callKey(call.getFunctionName().toString(), call.getArguments().size()));
        int index = info.field().getIndex();
        return positions != null && index < positions.length && positions[index];
    }

    private static void replaceTypes(Node root) {
        root.replaceAll(
                info -> info.node() instanceof ReferenceType,
                node -> {
                    ReferenceType reference = (ReferenceType) node;
                    PointerType pointer = new PointerType(reference.getTargetType());
                    pointer.setConst(reference.isConst());
                    return pointer.remap(reference);
                }
        );
    }

    /**
     * Снимает парные обёртки {@code &*e} и {@code *&e}, которые возникают, когда ссылочная
     * переменная попадает в ссылочную же позицию: сначала её использование разыменовали, потом
     * позиция потребовала адрес.
     */
    private static void collapsePairedOperators(Node root) {
        boolean changed = true;
        while (changed) {
            changed = !root.replaceAll(
                    info -> isPaired(info.node()),
                    node -> ((UnaryExpression) ((UnaryExpression) node).getArgument()).getArgument()
            ).isEmpty();
        }
    }

    private static boolean isPaired(Node node) {
        return (node instanceof PointerPackOp pack && pack.getArgument() instanceof PointerUnpackOp)
                || (node instanceof PointerUnpackOp unpack && unpack.getArgument() instanceof PointerPackOp);
    }
}
