package org.vstu.meaningtree.nodes.declarations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeReference;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.interfaces.NestedDeclaration;
import org.vstu.meaningtree.nodes.types.UserType;

import java.util.List;
import java.util.Objects;

public class MethodDeclaration extends FunctionDeclaration implements NestedDeclaration<ClassDeclaration> {
    @NodeReference
    private UserType owner;
    @NodeReference
    private ClassDeclaration parent;

    /**
     * Методы предков (по классу или интерфейсу), которые этот метод переопределяет или
     * реализует. Заполняется {@code OverrideResolver} после парсинга, только если предок
     * присутствует в том же фрагменте кода; иначе список пуст.
     * <p>
     * Список, а не одна ссылка: метод может одновременно реализовывать одинаковую сигнатуру
     * нескольких интерфейсов, а в C++ несколько прямых баз могут объявлять её на одном
     * расстоянии. Выбирать из них по порядку родителей значило бы отдавать за факт порядок
     * объявления; {@link #isOverrideAmbiguous()} говорит, что выбора не было.
     * <p>
     * Связи не участвуют в {@code equals}/{@code hashCode} и очищаются в {@link #clone()}: как и
     * {@link #parent}, они указывают вверх по иерархии дерева, а не являются его частью, и у
     * копии, ещё не прошедшей анализ, их быть не должно.
     */
    @NodeReference
    private List<MethodDeclaration> overriddenFrom = List.of();

    public MethodDeclaration(UserType owner,
                             Identifier name,
                             Type returnType,
                             List<Annotation> annotations,
                             List<DeclarationModifier> modifiers,
                             DeclarationArgument... arguments
    ) {
        this(owner, name, returnType, annotations, modifiers, List.of(arguments));
    }

    public MethodDeclaration(UserType owner,
                             Identifier name,
                             Type returnType,
                             List<Annotation> annotations,
                             List<DeclarationModifier> modifiers,
                             List<DeclarationArgument> arguments
    ) {
        super(name, returnType, annotations, arguments);
        this.owner = owner;
        this.modifiers = List.copyOf(modifiers);
    }

    public UserType getOwner() {
        return owner;
    }

    public void setOwner(UserType owner) {
        this.owner = owner;
    }

    /** Все найденные методы предков с этой сигнатурой; пусто, если связь не установлена. */
    public List<MethodDeclaration> getOverriddenFrom() {
        return overriddenFrom;
    }

    /**
     * Единственный переопределяемый метод.
     *
     * @return пусто, если связь не установлена или кандидатов оказалось несколько — во втором
     *         случае см. {@link #isOverrideAmbiguous()}
     */
    @Nullable
    public MethodDeclaration getOverriddenFromSingle() {
        return overriddenFrom.size() == 1 ? overriddenFrom.getFirst() : null;
    }

    public void setOverriddenFrom(@Nullable MethodDeclaration overriddenFrom) {
        this.overriddenFrom = overriddenFrom == null ? List.of() : List.of(overriddenFrom);
    }

    public void setOverriddenFrom(@NotNull List<MethodDeclaration> overriddenFrom) {
        this.overriddenFrom = List.copyOf(overriddenFrom);
    }

    public boolean isOverride() {
        return !overriddenFrom.isEmpty();
    }

    /**
     * Нашлось несколько методов предков с этой сигнатурой на одном расстоянии, и выбрать один
     * правилами языка нельзя. Связь при этом сохранена целиком — потребитель видит все варианты
     * и знает, что это не выбор, а неоднозначность.
     */
    public boolean isOverrideAmbiguous() {
        return overriddenFrom.size() > 1;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MethodDeclaration nodeInfos)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(owner, nodeInfos.owner) && Objects.equals(modifiers, nodeInfos.modifiers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), owner, modifiers);
    }

    public MethodDeclaration clone() {
        var clone = (MethodDeclaration) super.clone();
        clone.modifiers = List.copyOf(modifiers);
        clone.owner = owner.clone();
        // Копия — ещё не проанализированный узел: связь с предком проставляет OverrideResolver,
        // а указывать в чужое дерево она не должна.
        clone.overriddenFrom = List.of();
        return clone;
    }

    @Override
    public ClassDeclaration getParentDeclaration() {
        return parent;
    }

    @Override
    public void setParentDeclaration(ClassDeclaration declaration) {
        if (parent == null) parent = declaration;
    }
}
