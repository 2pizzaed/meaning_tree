package org.vstu.meaningtree.nodes.expressions.calls;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.exceptions.IllegalUsageException;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.iterators.utils.NodeReference;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.interfaces.Callable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FunctionCall extends Expression implements Callable {
    @TreeNode protected Expression function;
    @TreeNode protected List<Expression> arguments;

    /**
     * Декларация, к которой относится вызов; заполняется анализом после построения дерева.
     * <p>
     * Служебная обратная ссылка, а не потомок: та же декларация уже присутствует в дереве на
     * своём месте, поэтому {@code @TreeNode} превратил бы дерево в граф — обход посетил бы её
     * дважды, а {@code MeaningTree.makeIndex} упал бы на дубликате id. По той же причине поле
     * не участвует в {@code equals}/{@code hashCode}: тождество вызова определяется его
     * текстом, а не результатом анализа, иначе одинаковые вызовы перестали бы совпадать
     * из-за разных исходов разбора.
     */
    @NodeReference
    @Nullable
    private FunctionDeclaration resolvedDeclaration;

    @Override
    @Nullable
    public FunctionDeclaration getResolvedDeclaration() {
        return resolvedDeclaration;
    }

    @Override
    public void setResolvedDeclaration(@Nullable FunctionDeclaration declaration) {
        this.resolvedDeclaration = declaration;
    }

    public Expression getFunction() {
        return function;
    }

    public List<Expression> getArguments() {
        return List.copyOf(arguments);
    }

    public FunctionCall(Expression function, Expression ... arguments) {
        this(function, List.of(arguments));
    }

    public FunctionCall(Expression function, List<Expression> arguments) {
        if (function instanceof MemberAccess) {
            throw new IllegalUsageException("Use MethodCall instead this node");
        }
        this.function = function;
        this.arguments = arguments;
    }

    public boolean hasFunctionName() {
        return function instanceof SimpleIdentifier || (function instanceof ParenthesizedExpression paren && paren.getExpression() instanceof SimpleIdentifier);
    }

    public SimpleIdentifier getFunctionName() {
        // Проверяется само выражение, а не hasFunctionName: наследник вправе переопределить
        // предикат, а часть из них хранит function == null (команды печати, ввода, работы с
        // памятью), и доверие предикату вернуло бы отсюда null вместо исключения.
        if (function instanceof ParenthesizedExpression paren) {
            if (paren.getExpression() instanceof SimpleIdentifier name) {
                return name;
            }
        } else if (function instanceof SimpleIdentifier name) {
            return name;
        }
        throw new MeaningTreeException("Function does not have identifier of call");
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FunctionCall that = (FunctionCall) o;
        return Objects.equals(function, that.function) && Objects.equals(arguments, that.arguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), function, arguments);
    }

    @Override
    public FunctionCall clone() {
        FunctionCall obj = (FunctionCall) super.clone();
        if (function != null) {
            obj.function = function.clone();
        }
        obj.arguments = new ArrayList<>(arguments.stream().map(Expression::clone).toList());
        // Клон — это ещё не разобранный вызов: результат прежнего анализа к нему не относится.
        obj.resolvedDeclaration = null;
        return obj;
    }

    @Override
    public Expression getCallableName() {
        return function;
    }
}
