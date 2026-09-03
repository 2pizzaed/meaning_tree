package org.vstu.meaningtree.nodes.statements;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Statement;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Объявление о том, что перечисленные имена в этой области видимости связаны не здесь, а
 * снаружи: python-<code>global x, y</code> и <code>nonlocal a</code>.
 * <p>
 * Узел нужен потому, что в языках с неявным объявлением переменной присваивание само решает,
 * где имя живёт: без такого объявления <code>x = 1</code> внутри функции создаёт локальное имя,
 * с ним — пишет во внешнее. Само перенаправление хранится не в дереве, а в
 * {@link org.vstu.meaningtree.utils.scopes.ScopeTableElement} как привязка имени к области-цели;
 * узел — запись этого объявления в исходном тексте.
 */
public class ScopeDeclarationStatement extends Statement {
    /**
     * Куда объявление отправляет имя: <code>global</code> — в корневую область программы,
     * <code>nonlocal</code> — в ближайшую объемлющую функцию.
     */
    public enum Kind {
        GLOBAL,
        NONLOCAL
    }

    @NotNull private final Kind kind;

    @TreeNode private List<SimpleIdentifier> names;

    public ScopeDeclarationStatement(@NotNull Kind kind, @NotNull List<SimpleIdentifier> names) {
        this.kind = kind;
        this.names = validateNames(names);
    }

    public ScopeDeclarationStatement(@NotNull Kind kind, @NotNull SimpleIdentifier name) {
        this(kind, List.of(name));
    }

    @NotNull
    public Kind getKind() {
        return kind;
    }

    public List<SimpleIdentifier> getNames() {
        return List.copyOf(names);
    }

    public void setNames(@NotNull List<SimpleIdentifier> names) {
        this.names = validateNames(names);
    }

    /**
     * Пустой список недопустим: ни один язык не записывает такое объявление без имён, а в
     * модели оно означало бы оператор, не меняющий ничего, — то есть узел без смысла.
     */
    private static List<SimpleIdentifier> validateNames(@NotNull List<SimpleIdentifier> names) {
        if (names.isEmpty()) {
            throw new IllegalArgumentException("Scope declaration must name at least one identifier");
        }
        return new ArrayList<>(names);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ScopeDeclarationStatement other)) return false;
        if (!super.equals(o)) return false;
        return kind == other.kind && Objects.equals(names, other.names);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), kind, names);
    }

    @Override
    public ScopeDeclarationStatement clone() {
        ScopeDeclarationStatement clone = (ScopeDeclarationStatement) super.clone();
        clone.names = new ArrayList<>(names.stream().map(SimpleIdentifier::clone).toList());
        return clone;
    }
}
