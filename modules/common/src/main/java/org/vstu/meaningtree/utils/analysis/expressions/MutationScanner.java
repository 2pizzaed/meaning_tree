package org.vstu.meaningtree.utils.analysis.expressions;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.AssignmentExpression;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.unary.PostfixDecrementOp;
import org.vstu.meaningtree.nodes.expressions.unary.PostfixIncrementOp;
import org.vstu.meaningtree.nodes.expressions.unary.PrefixDecrementOp;
import org.vstu.meaningtree.nodes.expressions.unary.PrefixIncrementOp;
import org.vstu.meaningtree.nodes.interfaces.HasAssignmentEffect;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintCommand;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.ChainedAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.ListUnpackingAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.loops.RangeForLoop;
import org.vstu.meaningtree.nodes.types.builtin.ReferenceType;

import java.util.List;
import java.util.Set;

/**
 * Отвечает на один вопрос: может ли код поддерева изменить переменную. Нужен анализам, которые
 * считают значение переменной постоянным (границы цикла, размер коллекции, счётчик):
 * ответ «может» всегда безопасен, ответ «не может» должен быть доказан.
 * <p>
 * Сравнение идёт по имени, а не по объявлению: одноимённая переменная в другой области видимости
 * даст ложное «может», но не ложное «не может». Поэтому вызывающий обязан сузить поддерево до
 * области видимости самого объявления.
 * <p>
 * Изменением считается:
 * <ul>
 *   <li>присваивание (в том числе составное), инкремент и декремент;</li>
 *   <li>повторное объявление имени (одноимённая переменная, переменная цикла);</li>
 *   <li>взятие адреса ({@code &x}) и привязка к ссылке ({@code int &r = x}): дальше изменение
 *       возможно через псевдоним, имени уже не видно;</li>
 *   <li>ввод ({@code cin >> x}, {@code input(x)});</li>
 *   <li>передача в вызов, если разобранное объявление функции принимает параметр по неконстантной
 *       ссылке. Вызов без разобранного объявления для скаляра считается передачей по значению:
 *       это единственное допущение, которое не доказано деревом.</li>
 * </ul>
 * Для коллекции ({@link Mode#COLLECTION}) добавляется всё, что способно изменить содержимое без
 * присваивания: любой вызов метода на ней и передача в любой вызов. В Java и Python коллекция
 * передаётся по ссылке, поэтому разбирать объявление вызываемой функции незачем.
 */
public final class MutationScanner {
    public enum Mode {
        /** Число, символ, булево: изменить можно только присваиванием или через псевдоним. */
        SCALAR,
        /** Список, словарь, строка: содержимое меняется вызовами методов. */
        COLLECTION
    }

    /**
     * Встроенные функции, которые коллекцию-аргумент не меняют: возвращают значение или новую
     * коллекцию. Только для вызовов без разобранного объявления: пользовательская функция с таким
     * же именем встроенной не является.
     */
    private static final Set<String> NON_MUTATING_FUNCTIONS = Set.of(
            "len", "sorted", "sum", "min", "max", "any", "all", "list", "tuple", "set", "frozenset",
            "dict", "reversed", "enumerate", "zip", "str", "repr", "bool", "abs", "isinstance",
            "size", "empty");

    /**
     * Методы коллекций и строк, которые состав и размер получателя и аргументов не меняют.
     * Сюда не входит ничего, что способно добавить или убрать элемент ({@code append}, {@code add},
     * {@code push_back}, {@code pop}, {@code remove}, {@code clear}, {@code sort}).
     */
    private static final Set<String> NON_MUTATING_METHODS = Set.of(
            "count", "index", "copy", "get", "keys", "values", "items", "startswith", "endswith",
            "upper", "lower", "strip", "split", "join", "find",
            "size", "length", "isEmpty", "contains", "containsKey", "containsValue", "containsAll",
            "indexOf", "lastIndexOf", "getOrDefault", "charAt", "equals", "hashCode", "toString",
            "empty", "at", "front", "back", "begin", "end", "cbegin", "cend", "capacity", "max_size", "data");

    private MutationScanner() {
    }

    /**
     * Может ли поддерево изменить переменную {@code identifier}.
     *
     * @param ignoredDeclaration объявление самой переменной, которое не считается изменением
     *                           (при проверке всей области видимости оно в ней лежит), или {@code null}
     */
    public static boolean mayModify(Node subtree,
                                    SimpleIdentifier identifier,
                                    Mode mode,
                                    @Nullable Node ignoredDeclaration) {
        for (NodeInfo info : subtree.iterate(true)) {
            if (modifies(info.node(), identifier, mode, ignoredDeclaration)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Может ли поддерево дать переменной псевдоним: адрес или ссылку. Запись через такой псевдоним
     * не содержит имени переменной, поэтому присваивания по имени её не показывают.
     */
    public static boolean mayAlias(Node subtree, SimpleIdentifier identifier) {
        for (NodeInfo info : subtree.iterate(true)) {
            if (aliases(info.node(), identifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean modifies(Node node,
                                    SimpleIdentifier identifier,
                                    Mode mode,
                                    @Nullable Node ignoredDeclaration) {
        if (node instanceof VariableDeclaration declaration) {
            if (ignoredDeclaration != null && declaration.getId() == ignoredDeclaration.getId()) {
                return false;
            }
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                if (identifier.equals(declarator.getIdentifier())) {
                    return true;
                }
            }
            return aliases(node, identifier);
        }
        if (node instanceof SeparatedVariableDeclaration) {
            // Его объявления обходятся как самостоятельные узлы
            return false;
        }
        if (node instanceof AssignmentStatement assignment) {
            return isIdentifier(assignment.getLValue(), identifier);
        }
        if (node instanceof AssignmentExpression assignment) {
            return isIdentifier(assignment.getLValue(), identifier);
        }
        if (node instanceof PrefixIncrementOp || node instanceof PostfixIncrementOp
                || node instanceof PrefixDecrementOp || node instanceof PostfixDecrementOp) {
            return isIdentifier(((UnaryExpression) node).getArgument(), identifier);
        }
        if (node instanceof ChainedAssignmentStatement chained) {
            // a = b = 5: цели — выражения, отдельных узлов присваивания у них нет
            return chained.getTargets().stream().anyMatch(target -> isIdentifier(target, identifier));
        }
        if (node instanceof ListUnpackingAssignmentStatement unpacking) {
            return unpacking.getVariableNames().stream().anyMatch(identifier::equals);
        }
        if (node instanceof MultipleAssignmentStatement) {
            // Его присваивания обходятся как самостоятельные узлы
            return false;
        }
        if (node instanceof HasAssignmentEffect) {
            // Присваивание неизвестного вида: цель неизвестна, значит, любое упоминание подозрительно
            return mentions(node, identifier);
        }
        if (node instanceof RangeForLoop rangeLoop) {
            return identifier.equals(rangeLoop.getIdentifier());
        }
        if (aliases(node, identifier)) {
            return true;
        }
        if (node instanceof InputCommand input) {
            return hasBareArgument(input.getArguments(), identifier);
        }
        if (node instanceof PrintCommand) {
            // Печать значение читает и ничего не меняет
            return false;
        }
        if (node instanceof FunctionCall call) {
            if (mode == Mode.COLLECTION) {
                return !isKnownNonMutating(call)
                        && (hasBareArgument(call.getArguments(), identifier)
                        || (call instanceof MethodCall method && isIdentifier(method.getObject(), identifier)));
            }
            return passesByMutableReference(call, identifier);
        }
        return false;
    }

    private static boolean isKnownNonMutating(FunctionCall call) {
        if (call.getResolvedDeclaration() != null || !call.hasFunctionName()) {
            return false;
        }
        String name = call.getFunctionName().getName();
        return call instanceof MethodCall
                ? NON_MUTATING_METHODS.contains(name)
                : NON_MUTATING_FUNCTIONS.contains(name);
    }

    /** Упоминается ли переменная где-либо в поддереве, в том числе только для чтения. */
    private static boolean mentions(Node subtree, SimpleIdentifier identifier) {
        for (NodeInfo info : subtree.iterate(true)) {
            if (info.node() instanceof SimpleIdentifier simple && simple.equals(identifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aliases(Node node, SimpleIdentifier identifier) {
        if (node instanceof PointerPackOp pack) {
            return isIdentifier(pack.getArgument(), identifier);
        }
        if (node instanceof VariableDeclaration declaration && declaration.getType() instanceof ReferenceType) {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                if (declarator.hasInitialization() && isIdentifier(declarator.getRValue(), identifier)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean passesByMutableReference(FunctionCall call, SimpleIdentifier identifier) {
        FunctionDeclaration resolved = call.getResolvedDeclaration();
        if (resolved == null) {
            return false;
        }
        List<Expression> arguments = call.getArguments();
        List<DeclarationArgument> parameters = resolved.getArguments();
        for (int i = 0; i < arguments.size() && i < parameters.size(); i++) {
            if (!isIdentifier(arguments.get(i), identifier)) {
                continue;
            }
            Type parameterType = parameters.get(i).getType();
            if (parameterType instanceof ReferenceType reference && !reference.getTargetType().isConst()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBareArgument(List<Expression> arguments, SimpleIdentifier identifier) {
        for (Expression argument : arguments) {
            if (isIdentifier(argument, identifier)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdentifier(@Nullable Node node, SimpleIdentifier identifier) {
        while (node instanceof ParenthesizedExpression parenthesized) {
            node = parenthesized.getExpression();
        }
        return node instanceof SimpleIdentifier simple && simple.equals(identifier);
    }
}
