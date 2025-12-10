package org.vstu.meaningtree.nodes.types;

import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.expressions.Literal;

import java.util.Objects;

/**
 * Литеральный тип. Часто используется в динамических языках, по типу TS, Python.
 * Указывает на фиксированное значение заданного, который может принимать типизированный элемент программы
 * Не все типы поддерживаются как литералы
 */
public class LiteralType extends Type {
    @TreeNode private Literal literal;

    public LiteralType(Literal expr) {
        this.literal = expr;
    }

    public Literal getLiteral() {
        return literal;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        LiteralType that = (LiteralType) o;
        return Objects.equals(literal, that.literal);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), literal);
    }

    @Override
    public LiteralType clone() {
        LiteralType obj = (LiteralType) super.clone();
        obj.literal = literal.clone();
        return obj;
    }
}
