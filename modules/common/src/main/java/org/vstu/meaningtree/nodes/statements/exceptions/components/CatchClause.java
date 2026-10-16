package org.vstu.meaningtree.nodes.statements.exceptions.components;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.InternalNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Одна ветвь обработки исключения: <code>catch (E e) { ... }</code> в Java/C++,
 * <code>except (A, B) as e: ...</code> в Python.
 * <p>
 * Несколько типов покрывают Java multi-catch (<code>catch (A | B e)</code>) и
 * python-кортеж типов. Пустой список типов — это <code>except:</code> в Python
 * и <code>catch (...)</code> в C++, то есть перехват любого исключения.
 * Имя отсутствует, если исключение не связывается с переменной.
 */
@InternalNode
public class CatchClause extends Statement implements HasBodyStatement {
    @TreeNode private List<Type> exceptionTypes;

    @TreeNode
    @Nullable
    private SimpleIdentifier name;

    @TreeNode private Statement body;

    public CatchClause(@NotNull List<Type> exceptionTypes, @Nullable SimpleIdentifier name, @NotNull Statement body) {
        this.exceptionTypes = new ArrayList<>(exceptionTypes);
        this.name = name;
        this.body = body;
    }

    public CatchClause(@NotNull Type exceptionType, @Nullable SimpleIdentifier name, @NotNull Statement body) {
        this(List.of(exceptionType), name, body);
    }

    /**
     * Перехват любого исключения без связывания с переменной.
     */
    public CatchClause(@NotNull Statement body) {
        this(List.of(), null, body);
    }

    public List<Type> getExceptionTypes() {
        return exceptionTypes;
    }

    /**
     * @return true, если ветвь перехватывает любое исключение и типы не указаны.
     */
    public boolean catchesAny() {
        return exceptionTypes.isEmpty();
    }

    @Nullable
    public SimpleIdentifier getName() {
        return name;
    }

    public boolean hasName() {
        return name != null;
    }

    @Override
    public Statement getBody() {
        return body;
    }

    @Override
    public CompoundStatement makeCompoundBody() {
        body = CompoundStatement.wrap(body);
        return (CompoundStatement) body;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CatchClause nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(exceptionTypes, nodeInfos.exceptionTypes)
                && Objects.equals(name, nodeInfos.name)
                && Objects.equals(body, nodeInfos.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), exceptionTypes, name, body);
    }

    @Override
    public CatchClause clone() {
        CatchClause clone = (CatchClause) super.clone();
        clone.exceptionTypes = new ArrayList<>(exceptionTypes.stream().map(Type::clone).toList());
        clone.name = name == null ? null : name.clone();
        clone.body = body.clone();
        return clone;
    }
}
