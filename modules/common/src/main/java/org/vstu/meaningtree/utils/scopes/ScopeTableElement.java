package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;

import java.io.Serializable;
import java.util.*;

/**
 * Lexical scope frame. Program-wide declarations, imports and type registries
 * are owned by {@link ScopeTable}; this class stores only local visibility
 * extensions and links to parent frames.
 */
public class ScopeTableElement implements Serializable {
    private final long id;

    @Nullable
    private final ScopeTableElement parent;

    @Nullable
    private transient Node owner;

    @NotNull
    private final Map<SimpleIdentifier, Type> variables;

    @NotNull
    private final Map<SimpleIdentifier, VariableDeclaration> variableDeclarations;

    @NotNull
    private final DeclarationBucket localDeclarations;

    @NotNull
    private final Map<Identifier, Type> declaredTypes;

    @NotNull
    private final Map<Type, Declaration> typeDeclarations;

    /**
     * Группы перегрузок, объявленные именно в этой области.
     * <p>
     * Хранятся у области, а не общим списком таблицы, потому что видимость имени лексическая:
     * поиск обязан идти от места вызова вверх по родителям и останавливаться на ближайшей
     * области, где имя объявлено. Общий список не даёт затенению работать — вложенная функция
     * и одноимённая глобальная выглядели бы в нём видимыми одновременно.
     */
    @NotNull
    private final List<OverloadGroup> overloadGroups;

    /**
     * Имена, которые эта область объявила связанными не здесь, а в области-предке:
     * python-{@code global} и {@code nonlocal}.
     * <p>
     * Карта смотрит вперёд, от области к цели, а не наоборот: искать имя в этой области —
     * частая операция, и перенаправление обязано быть ответом на неё, а не отдельным
     * обходом всех потомков. Цель — всегда строгий предок, поэтому цепочка привязок конечна
     * и разрешается простым циклом.
     */
    @NotNull
    private final Map<SimpleIdentifier, ScopeTableElement> rebinds;

    public ScopeTableElement(long id, @Nullable ScopeTableElement parent, @Nullable Node owner) {
        this.id = id;
        this.parent = parent;
        this.variables = new HashMap<>();
        this.variableDeclarations = new HashMap<>();
        this.localDeclarations = new DeclarationBucket();
        this.declaredTypes = new HashMap<>();
        this.typeDeclarations = new HashMap<>();
        this.overloadGroups = new ArrayList<>();
        this.rebinds = new HashMap<>();
        setOwner(owner);
    }

    public long getId() {
        return id;
    }

    public Map<Identifier, List<Declaration>> allDeclarations() {
        return Map.copyOf(new HashMap<Identifier, List<Declaration>>(localDeclarations.asMap()));
    }

    public Map<SimpleIdentifier, Type> allVariables() {
        return Map.copyOf(variables);
    }

    public Map<SimpleIdentifier, VariableDeclaration> allVariableDeclarations() {
        return Map.copyOf(variableDeclarations);
    }

    public Map<Identifier, Type> allTypes() {
        return Map.copyOf(declaredTypes);
    }

    public Map<Type, Declaration> allTypeDeclarations() {
        return Map.copyOf(typeDeclarations);
    }

    public void registerVariable(@NotNull VariableDeclaration variableDeclaration) {
        for (VariableDeclarator decl : variableDeclaration.getDeclarators()) {
            variables.put(decl.getIdentifier(), detachType(variableDeclaration.getType()));
            variableDeclarations.put(decl.getIdentifier(), variableDeclaration);
        }
    }

    public void registerVariable(@NotNull SeparatedVariableDeclaration variableDeclaration) {
        for (var varDecl : variableDeclaration.getDeclarations()) {
            registerVariable(varDecl);
        }
    }

    public void restoreVariable(@NotNull SimpleIdentifier name,
                                @NotNull Type type,
                                @Nullable VariableDeclaration declaration) {
        variables.put(name, detachType(type));
        if (declaration != null) {
            variableDeclarations.put(name, declaration);
        }
    }

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration decl) {
        localDeclarations.register(name, decl);
        Type type = ScopeTable.declaredTypeOf(decl);
        if (type != null) {
            typeDeclarations.put(type, decl);
            registerType(name, type);
        }
    }

    /** Убирает конкретное объявление из этой области видимости. */
    public boolean removeDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        Type type = ScopeTable.declaredTypeOf(declaration);
        if (type != null) {
            typeDeclarations.remove(type);
            declaredTypes.remove(name);
        }
        return localDeclarations.remove(name, declaration);
    }

    public Identifier registerType(@NotNull Identifier name, @NotNull Type type) {
        if (!declaredTypes.containsValue(type)) {
            declaredTypes.put(name, type);
            return name;
        }

        for (var pair : declaredTypes.entrySet()) {
            if (pair.getValue().equals(type)) {
                return pair.getKey();
            }
        }
        return name;
    }

    public void registerTypeDeclaration(@NotNull Type type, @NotNull Declaration declaration) {
        typeDeclarations.put(type, declaration);
    }

    /**
     * Объявляет, что имя в этой области связано областью {@code target}, а не здесь.
     *
     * @throws IllegalArgumentException если цель не строгий предок: привязка на себя или вниз
     *                                  замкнула бы цепочку разрешения в цикл
     */
    public void rebindName(@NotNull SimpleIdentifier name, @NotNull ScopeTableElement target) {
        if (!target.isStrictAncestorOf(this)) {
            throw new IllegalArgumentException(
                    "Scope binding target must be a strict ancestor scope, got scope " + target.getId()
                            + " for scope " + getId());
        }
        rebinds.put(name, target);
    }

    /** Цель привязки, объявленной именно этой областью, без прохода по цепочке. */
    public Optional<ScopeTableElement> rebindTarget(@NotNull SimpleIdentifier name) {
        return Optional.ofNullable(rebinds.get(name));
    }

    /** Все привязки, объявленные этой областью, — для сериализации таблицы. */
    @NotNull
    public Map<SimpleIdentifier, ScopeTableElement> allRebinds() {
        return Map.copyOf(rebinds);
    }

    /**
     * Область, которая на самом деле владеет этим именем при взгляде отсюда.
     * <p>
     * Цепочка проходится до конца: {@code global x} внутри функции, которая сама объявила
     * {@code x} через {@code nonlocal}, обязан привести туда же, куда привёл бы напрямую.
     * Цикла быть не может — каждый шаг поднимается к строгому предку.
     *
     * @return область-цель либо {@code this}, если имя не привязано
     */
    @NotNull
    public ScopeTableElement resolveBinding(@NotNull SimpleIdentifier name) {
        ScopeTableElement current = this;
        ScopeTableElement target;
        while ((target = current.rebinds.get(name)) != null) {
            current = target;
        }
        return current;
    }

    private boolean isStrictAncestorOf(@NotNull ScopeTableElement descendant) {
        for (ScopeTableElement current = descendant.parent; current != null; current = current.parent) {
            if (current == this) {
                return true;
            }
        }
        return false;
    }

    public void removeVariable(@NotNull SimpleIdentifier name) {
        ScopeTableElement target = resolveBinding(name);
        if (target != this) {
            target.removeVariable(name);
            return;
        }
        if (!variables.containsKey(name) && parent != null) {
            parent.removeVariable(name);
            return;
        }
        variables.remove(name);
        variableDeclarations.remove(name);
    }

    public boolean hasVariable(@NotNull SimpleIdentifier name) {
        ScopeTableElement target = resolveBinding(name);
        return target != this ? target.hasVariable(name) : variables.containsKey(name);
    }

    @Nullable
    public Type getVariableType(@NotNull SimpleIdentifier name) {
        ScopeTableElement target = resolveBinding(name);
        if (target != this) {
            return target.getVariableType(name);
        }
        Type type = variables.get(name);
        if (type != null) {
            return detachType(type);
        }
        if (parent != null) {
            return parent.getVariableType(name);
        }
        return null;
    }

    public Optional<VariableDeclaration> getVariableDeclaration(@NotNull SimpleIdentifier name, @Nullable Type type) {
        ScopeTableElement target = resolveBinding(name);
        if (target != this) {
            return target.getVariableDeclaration(name, type);
        }
        if (variableDeclarations.containsKey(name)
                && (type == null || Objects.equals(variables.get(name), type))) {
            return Optional.of(variableDeclarations.get(name));
        }
        if (parent != null) {
            return parent.getVariableDeclaration(name, type);
        }
        return Optional.empty();
    }

    /**
     * Тип переменной, которую свяжет присваивание этому имени отсюда.
     * <p>
     * Отличается от {@link #getVariableType} тем, что зависит от {@link AssignmentBinding}:
     * при {@link AssignmentBinding#LOCAL} видимая снаружи одноимённая переменная целью
     * присваивания не является, поэтому и типа у цели ещё нет. Ровно этим отличаются
     * «присвоить известной переменной» и «объявить новую», а по форме узла они неразличимы.
     */
    @Nullable
    public Type getAssignmentTargetType(@NotNull SimpleIdentifier name, @NotNull AssignmentBinding binding) {
        if (binding == AssignmentBinding.ENCLOSING) {
            return getVariableType(name);
        }
        ScopeTableElement target = resolveBinding(name);
        Type type = target.variables.get(name);
        return type == null ? null : detachType(type);
    }

    public void changeVariableType(@NotNull SimpleIdentifier name,
                                   @NotNull Type type,
                                   boolean createIfNotExists,
                                   @NotNull AssignmentBinding binding) {
        ScopeTableElement target = resolveBinding(name);
        if (target != this) {
            target.changeVariableType(name, type, createIfNotExists, binding);
            return;
        }

        if (binding == AssignmentBinding.LOCAL) {
            if (!variables.containsKey(name) && !createIfNotExists) {
                throw new IllegalArgumentException("No such variable: " + name);
            }
            variables.put(name, detachType(type));
            return;
        }

        if (getVariableType(name) == null && !createIfNotExists) {
            throw new IllegalArgumentException("No such variable: " + name);
        }

        if (!variables.containsKey(name) && parent != null && parent.getVariableType(name) != null) {
            parent.changeVariableType(name, type, false, binding);
            return;
        }

        variables.put(name, detachType(type));
    }

    public void changeVariableType(@NotNull SimpleIdentifier name,
                                   @NotNull Type type,
                                   boolean createIfNotExists) {
        changeVariableType(name, type, createIfNotExists, AssignmentBinding.ENCLOSING);
    }

    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type) {
        changeVariableType(name, type, true);
    }

    @NotNull
    public Type getFunctionReturnType(@NotNull SimpleIdentifier name) {
        var method = findDeclaration(name, FunctionDeclaration.class);
        if (method.isPresent()) {
            FunctionDeclaration methodDecl = (FunctionDeclaration) method.get();
            return methodDecl.getReturnType();
        }
        return new UnknownType();
    }

    @NotNull
    public Map<SimpleIdentifier, Type> getCurrentVariables() {
        return Map.copyOf(variables);
    }

    @NotNull
    public Map<Identifier, Type> getCurrentAvailableTypes() {
        return Map.copyOf(declaredTypes);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name,
                                                 @Nullable Class<? extends Declaration> clazz) {
        var localDeclaration = findCurrentDeclaration(name, clazz);
        if (localDeclaration.isPresent()) {
            return localDeclaration;
        }
        if (parent != null) {
            return parent.findDeclaration(name, clazz);
        }
        return Optional.empty();
    }

    public Optional<Declaration> findCurrentDeclaration(@NotNull SimpleIdentifier name,
                                                        @Nullable Class<? extends Declaration> clazz) {
        return localDeclarations.findLast(name, clazz);
    }

    /**
     * Все одноимённые декларации ближайшей области видимости, в которой имя объявлено.
     * <p>
     * Именно ближайшей, а не объединение всех: внутренняя область целиком затеняет имя,
     * объявленное снаружи, поэтому перегрузки из разных областей в одну группу не сливаются.
     */
    public List<Declaration> findDeclarations(@NotNull SimpleIdentifier name,
                                              @Nullable Class<? extends Declaration> clazz) {
        var local = findCurrentDeclarations(name, clazz);
        if (!local.isEmpty()) {
            return local;
        }
        if (parent != null) {
            return parent.findDeclarations(name, clazz);
        }
        return List.of();
    }

    public List<Declaration> findCurrentDeclarations(@NotNull SimpleIdentifier name,
                                                     @Nullable Class<? extends Declaration> clazz) {
        return localDeclarations.findAll(name, clazz);
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz) {
        var result = findCurrentDeclaration(clazz);
        if (result.isEmpty() && parent != null) {
            return parent.findDeclaration(clazz);
        }
        return result;
    }

    public List<Declaration> findCurrentDeclaration(@NotNull Class<? extends Declaration> clazz) {
        return localDeclarations.findAll(clazz);
    }

    public Optional<Type> findType(@NotNull Identifier name) {
        var type = findCurrentType(name);
        if (type.isPresent()) {
            return type;
        }
        if (parent != null) {
            return parent.findType(name);
        }
        return Optional.empty();
    }

    public Optional<Type> findCurrentType(@NotNull Identifier name) {
        return Optional.ofNullable(declaredTypes.get(name));
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type) {
        var declaration = findCurrentTypeDeclaration(type);
        if (declaration.isPresent()) {
            return declaration;
        }
        if (parent != null) {
            return parent.findTypeDeclaration(type);
        }
        return Optional.empty();
    }

    public Optional<Declaration> findCurrentTypeDeclaration(@NotNull Type type) {
        return Optional.ofNullable(typeDeclarations.get(type));
    }

    @Nullable
    public ScopeTableElement getParent() {
        return parent;
    }

    public Optional<CompoundStatement> belongsToBody() {
        if (owner instanceof CompoundStatement) {
            return Optional.of((CompoundStatement) owner);
        }
        return Optional.empty();
    }

    public Optional<ClassDefinition> belongsToClass() {
        if (owner instanceof ClassDefinition) {
            return Optional.of((ClassDefinition) owner);
        }
        return Optional.empty();
    }

    public Node getOwner() {
        return owner;
    }

    public void setOwner(@Nullable Node node) {
        this.owner = node;
        if (node instanceof CompoundStatement compoundStatement) {
            compoundStatement.bindScope(this);
        }
    }

    private static Type detachType(@NotNull Type type) {
        return (Type) type.freshClone();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        variables.forEach((id, t) -> sb.append(id).append(" = ").append(t).append(", "));
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    void registerOverloadGroup(@NotNull OverloadGroup group) {
        overloadGroups.add(group);
    }

    void clearOverloadGroups() {
        overloadGroups.clear();
    }

    /** Группы, объявленные непосредственно в этой области. */
    @NotNull
    public List<OverloadGroup> overloadGroups() {
        return List.copyOf(overloadGroups);
    }

    /**
     * Ближайшая видимая отсюда группа свободных функций с этим именем.
     * <p>
     * Поиск останавливается на первой области, где имя объявлено: внутренняя декларация затеняет
     * внешнюю целиком, а не дополняет её. Перегрузками считаются только одноимённые декларации
     * одной области — именно так устроена видимость в Java и C++.
     */
    @NotNull
    public Optional<OverloadGroup> findVisibleFunctionGroup(@NotNull SimpleIdentifier name) {
        for (ScopeTableElement current = this; current != null; current = current.parent) {
            for (OverloadGroup group : current.overloadGroups) {
                if (group.kind() == OverloadKind.FUNCTION && group.name().equals(name)) {
                    return Optional.of(group);
                }
            }
        }
        return Optional.empty();
    }
}
