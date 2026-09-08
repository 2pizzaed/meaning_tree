package org.vstu.meaningtree.nodes.expressions.newexpr;

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

public class ObjectNewExpression extends NewExpression implements Callable {
    @TreeNode private List<Expression> constructorArguments;

    public ObjectNewExpression(Type type, Expression... constructorArguments) {
        this(type, List.of(constructorArguments));
    }

    public ObjectNewExpression(Type type, List<Expression> constructorArguments) {
        super(type);
        this.constructorArguments = List.copyOf(constructorArguments);
    }

    public List<Expression> getConstructorArguments() {
        return constructorArguments;
    }

    @Override
    public List<Expression> getArguments() {
        return constructorArguments;
    }

    /** Имя вызываемой сущности у создания объекта — сам создаваемый тип. */
    @Override
    public Expression getCallableName() {
        return getType();
    }

    /**
     * Конструктор, который вызывает создание объекта; см.
     * {@link Callable#getResolvedDeclaration()}. Служебная обратная ссылка без
     * {@code @TreeNode}: конструктор уже присутствует в дереве в своём классе.
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

    // anonymous classes unsupported

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ObjectNewExpression that = (ObjectNewExpression) o;
        return Objects.equals(constructorArguments, that.constructorArguments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), constructorArguments);
    }

    @Override
    public ObjectNewExpression clone() {
        ObjectNewExpression obj = (ObjectNewExpression) super.clone();
        obj.constructorArguments = new ArrayList<>(constructorArguments.stream().map(Expression::clone).toList());
        // Клон — это ещё не разобранный вызов: результат прежнего анализа к нему не относится.
        obj.resolvedDeclaration = null;
        return obj;
    }
}
