package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
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
    @Nullable
    private final ScopeTableElement parent;

    @Nullable
    private Node owner;

    @NotNull
    private final Map<SimpleIdentifier, Type> variables;

    @NotNull
    private final Map<SimpleIdentifier, VariableDeclaration> variableDeclarations;

    @NotNull
    private final Map<SimpleIdentifier, Declaration> localDeclarations;

    @NotNull
    private final Map<Identifier, Type> declaredTypes;

    @NotNull
    private final Map<Type, Declaration> typeDeclarations;

    public ScopeTableElement(@Nullable ScopeTableElement parent, @Nullable Node owner) {
        this.parent = parent;
        this.owner = owner;
        this.variables = new HashMap<>();
        this.variableDeclarations = new HashMap<>();
        this.localDeclarations = new HashMap<>();
        this.declaredTypes = new HashMap<>();
        this.typeDeclarations = new HashMap<>();
    }

    public ScopeTableElement(@Nullable ScopeTableElement parent) {
        this(parent, null);
    }

    public Map<Identifier, Declaration> allDeclarations() {
        return Map.copyOf(new HashMap<Identifier, Declaration>(localDeclarations));
    }

    public void registerVariable(@NotNull VariableDeclaration variableDeclaration) {
        for (VariableDeclarator decl : variableDeclaration.getDeclarators()) {
            variables.put(decl.getIdentifier(), variableDeclaration.getType());
            variableDeclarations.put(decl.getIdentifier(), variableDeclaration);
        }
    }

    public void registerVariable(@NotNull SeparatedVariableDeclaration variableDeclaration) {
        for (var varDecl : variableDeclaration.getDeclarations()) {
            registerVariable(varDecl);
        }
    }

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration decl) {
        localDeclarations.put(name, decl);
        if (decl instanceof ClassDeclaration cls) {
            Type type = cls.getTypeNode();
            typeDeclarations.put(type, decl);
            registerType(name, type);
        }
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

    public void removeVariable(@NotNull SimpleIdentifier name) {
        if (!variables.containsKey(name) && parent != null) {
            parent.removeVariable(name);
            return;
        }
        variables.remove(name);
        variableDeclarations.remove(name);
    }

    public boolean hasVariable(@NotNull SimpleIdentifier name) {
        return variables.containsKey(name);
    }

    @Nullable
    public Type getVariableType(@NotNull SimpleIdentifier name) {
        Type type = variables.get(name);
        if (type != null) {
            return type;
        }
        if (parent != null) {
            return parent.getVariableType(name);
        }
        return null;
    }

    public Optional<VariableDeclaration> getVariableDeclaration(@NotNull SimpleIdentifier name, @Nullable Type type) {
        if (variableDeclarations.containsKey(name)
                && (type == null || Objects.equals(variables.get(name), type))) {
            return Optional.of(variableDeclarations.get(name));
        }
        if (parent != null) {
            return parent.getVariableDeclaration(name, type);
        }
        return Optional.empty();
    }

    public void changeVariableType(@NotNull SimpleIdentifier name,
                                   @NotNull Type type,
                                   boolean createIfNotExists) {
        if (getVariableType(name) == null && !createIfNotExists) {
            throw new IllegalArgumentException("No such variable: " + name);
        }

        if (!variables.containsKey(name) && parent != null && parent.getVariableType(name) != null) {
            parent.changeVariableType(name, type, false);
            return;
        }

        variables.put(name, type);
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
        Declaration declaration = localDeclarations.get(name);
        if (declaration != null && (clazz == null || clazz.isAssignableFrom(declaration.getClass()))) {
            return Optional.of(declaration);
        }
        return Optional.empty();
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz) {
        var result = findCurrentDeclaration(clazz);
        if (result.isEmpty() && parent != null) {
            return parent.findDeclaration(clazz);
        }
        return result;
    }

    public List<Declaration> findCurrentDeclaration(@NotNull Class<? extends Declaration> clazz) {
        return localDeclarations.values().stream()
                .filter(declaration -> clazz.isAssignableFrom(declaration.getClass()))
                .toList();
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
}
