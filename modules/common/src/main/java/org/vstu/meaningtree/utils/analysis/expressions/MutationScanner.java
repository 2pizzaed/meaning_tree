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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
     * Что поддерево способно сделать с переменными, по именам. Считается за один проход, поэтому
     * на вопросы о многих переменных одной области видимости отвечает без повторных обходов:
     * сводку области строят один раз и спрашивают по имени.
     */
    public static final class Summary {
        private final Set<String> modified = new HashSet<>();
        private final Set<String> aliased = new HashSet<>();
        private final Map<String, Integer> declared = new HashMap<>();

        /** Может ли поддерево изменить переменную: записать, дать псевдоним, передать по ссылке или объявить заново. */
        public boolean mayModify(String name) {
            return modified.contains(name) || declared.containsKey(name);
        }

        /**
         * То же для области видимости, в которой объявлена сама переменная: её собственное объявление
         * изменением не считается, а второе одноимённое (затенение) считается.
         */
        public boolean mayModifyBesidesItsDeclaration(String name) {
            return modified.contains(name) || declared.getOrDefault(name, 0) > 1;
        }

        /** Может ли поддерево дать переменной псевдоним: адрес или ссылку. */
        public boolean mayAlias(String name) {
            return aliased.contains(name);
        }
    }

    public static Summary summarize(Node subtree, Mode mode) {
        Summary summary = new Summary();
        for (NodeInfo info : subtree.iterate(true)) {
            collect(info.node(), mode, summary);
        }
        return summary;
    }

    /** Может ли поддерево изменить переменную {@code identifier}. */
    public static boolean mayModify(Node subtree, SimpleIdentifier identifier, Mode mode) {
        return summarize(subtree, mode).mayModify(identifier.getName());
    }

    /**
     * Может ли поддерево дать переменной псевдоним: адрес или ссылку. Запись через такой псевдоним
     * не содержит имени переменной, поэтому присваивания по имени её не показывают.
     */
    public static boolean mayAlias(Node subtree, SimpleIdentifier identifier) {
        return summarize(subtree, Mode.SCALAR).mayAlias(identifier.getName());
    }

    private static void collect(Node node, Mode mode, Summary summary) {
        if (node instanceof VariableDeclaration declaration) {
            for (VariableDeclarator declarator : declaration.getDeclarators()) {
                summary.declared.merge(declarator.getIdentifier().getName(), 1, Integer::sum);
                if (declaration.getType() instanceof ReferenceType && declarator.hasInitialization()) {
                    alias(declarator.getRValue(), summary);
                }
            }
            return;
        }
        if (node instanceof SeparatedVariableDeclaration || node instanceof MultipleAssignmentStatement) {
            // Их объявления и присваивания обходятся как самостоятельные узлы
            return;
        }
        if (node instanceof AssignmentStatement assignment) {
            modify(assignment.getLValue(), summary);
        } else if (node instanceof AssignmentExpression assignment) {
            modify(assignment.getLValue(), summary);
        } else if (node instanceof PrefixIncrementOp || node instanceof PostfixIncrementOp
                || node instanceof PrefixDecrementOp || node instanceof PostfixDecrementOp) {
            modify(((UnaryExpression) node).getArgument(), summary);
        } else if (node instanceof ChainedAssignmentStatement chained) {
            // a = b = 5: цели — выражения, отдельных узлов присваивания у них нет
            chained.getTargets().forEach(target -> modify(target, summary));
        } else if (node instanceof ListUnpackingAssignmentStatement unpacking) {
            unpacking.getVariableNames().forEach(name -> modify(name, summary));
        } else if (node instanceof HasAssignmentEffect) {
            // Присваивание неизвестного вида: цель неизвестна, значит, любое упоминание подозрительно
            for (NodeInfo info : node.iterate(true)) {
                modify(info.node(), summary);
            }
        } else if (node instanceof RangeForLoop rangeLoop) {
            modify(rangeLoop.getIdentifier(), summary);
        } else if (node instanceof PointerPackOp pack) {
            alias(pack.getArgument(), summary);
        } else if (node instanceof InputCommand input) {
            input.getArguments().forEach(argument -> modify(argument, summary));
        } else if (node instanceof PrintCommand) {
            // Печать значение читает и ничего не меняет
            return;
        } else if (node instanceof FunctionCall call) {
            if (mode == Mode.COLLECTION) {
                if (!isKnownNonMutating(call)) {
                    call.getArguments().forEach(argument -> modify(argument, summary));
                    if (call instanceof MethodCall method) {
                        modify(method.getObject(), summary);
                    }
                }
            } else {
                modifyByMutableReference(call, summary);
            }
        }
    }

    private static void modifyByMutableReference(FunctionCall call, Summary summary) {
        FunctionDeclaration resolved = call.getResolvedDeclaration();
        if (resolved == null) {
            return;
        }
        List<Expression> arguments = call.getArguments();
        List<DeclarationArgument> parameters = resolved.getArguments();
        for (int i = 0; i < arguments.size() && i < parameters.size(); i++) {
            Type parameterType = parameters.get(i).getType();
            if (parameterType instanceof ReferenceType reference && !reference.getTargetType().isConst()) {
                modify(arguments.get(i), summary);
            }
        }
    }

    private static void modify(@Nullable Node target, Summary summary) {
        String name = nameOf(target);
        if (name != null) {
            summary.modified.add(name);
        }
    }

    private static void alias(@Nullable Node target, Summary summary) {
        String name = nameOf(target);
        if (name != null) {
            summary.aliased.add(name);
            summary.modified.add(name);
        }
    }

    private static @Nullable String nameOf(@Nullable Node node) {
        while (node instanceof ParenthesizedExpression parenthesized) {
            node = parenthesized.getExpression();
        }
        return node instanceof SimpleIdentifier simple ? simple.getName() : null;
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
}
