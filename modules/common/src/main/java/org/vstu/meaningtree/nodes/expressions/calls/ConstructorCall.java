package org.vstu.meaningtree.nodes.expressions.calls;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeReference;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.interfaces.Callable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConstructorCall extends Expression implements Callable {
    @TreeNode protected List<Expression> arguments;
    @TreeNode protected Type constructorOwner;
    private final boolean isBaseClassCall;

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

    public ConstructorCall(Type constructorOwner, boolean isBaseClassCall, List<Expression> arguments) {
        this.arguments = arguments;
        this.constructorOwner = constructorOwner;
        this.isBaseClassCall = isBaseClassCall;
    }

    public ConstructorCall(Type constructorOwner, List<Expression> arguments) {
        this(constructorOwner, false, arguments);
    }

    public ConstructorCall(Type constructorOwner, Expression ... arguments) {
        this(constructorOwner, List.of(arguments));
    }

    public ConstructorCall(Type constructorOwner, boolean isBaseClassCall, Expression ... arguments) {
        this(constructorOwner, isBaseClassCall, List.of(arguments));
    }

    public List<Expression> getArguments() {
        return List.copyOf(arguments);
    }

    public Type getOwner() {
        return constructorOwner;
    }

    public boolean isBaseClassCall() {
        return isBaseClassCall;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ConstructorCall that = (ConstructorCall) o;
        return isBaseClassCall == that.isBaseClassCall
                && Objects.equals(arguments, that.arguments)
                && Objects.equals(constructorOwner, that.constructorOwner);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), arguments, constructorOwner, isBaseClassCall);
    }

    @Override
    public ConstructorCall clone() {
        ConstructorCall obj = (ConstructorCall) super.clone();
        obj.arguments = new ArrayList<>(arguments.stream().map(Expression::clone).toList());
        obj.constructorOwner = constructorOwner.clone();
        // Клон — это ещё не разобранный вызов: результат прежнего анализа к нему не относится.
        obj.resolvedDeclaration = null;
        return obj;
    }

    @Override
    public Expression getCallableName() {
        return getOwner();
    }
}
