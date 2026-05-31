package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.types.UserType;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code TypeScope} управляет стеком областей видимости типов,
 * поддерживая операции входа и выхода из областей {@link ScopeTableElement}.
 */
public class ScopeTable implements Serializable {
    /**
     * Program-wide declarations and definitions.
     */
    @NotNull
    private final SymbolIndex symbols;

    /**
     * Program-wide type registry and user type hierarchy.
     */
    @NotNull
    private final TypeIndex types;

    /**
     * Program-wide imports.
     */
    @NotNull
    private final ImportIndex imports;

    /**
     * Текущая область сущностей.
     */
    @NotNull
    private ScopeTableElement current;

    /**
     * Создаёт менеджер областей видимости с корневой областью.
     */
    public ScopeTable() {
        this.symbols = new SymbolIndex();
        this.types = new TypeIndex();
        this.imports = new ImportIndex();
        this.current = new ScopeTableElement(null);
    }

    /**
     * Входит в новую область видимости.
     */
    public void enter() {
        enter(null);
    }

    /**
     * Входит в новую область видимости, связанную с AST-узлом.
     */
    public void enter(@Nullable Node owner) {
        current = new ScopeTableElement(current);
        current.setOwner(owner);
    }

    /**
     * Входит в родительскую область.
     * Если родительской области нет, ничего не происходит.
     *
     * @throws IllegalStateException если текущая область корневая
     */
    public void leave() {
        leave(false);
    }

    /**
     * Входит в родительскую область.
     *
     * @param rootScopeMustExist Когда {@code true}, выбрасывает исключение
     *                           {@code IllegalStateException}, если родительской
     *                           области видимости не существует
     */
    public void leave(boolean rootScopeMustExist) {
        ScopeTableElement parent = current.getParent();
        if (parent != null) {
            current = parent;
            return;
        }

        if (rootScopeMustExist) {
            throw new IllegalStateException("Cannot leave root scope");
        }
    }

    public ScopeTableElement scope() {
        return current;
    }

    public void setCurrentScopeOwner(@Nullable Node owner) {
        current.setOwner(owner);
    }

    public void registerVariable(@NotNull VariableDeclaration variableDeclaration) {
        current.registerVariable(variableDeclaration);
    }

    public void registerVariable(@NotNull SeparatedVariableDeclaration variableDeclaration) {
        current.registerVariable(variableDeclaration);
    }

    public void removeVariable(@NotNull SimpleIdentifier name) {
        current.removeVariable(name);
    }

    public boolean hasVariable(@NotNull SimpleIdentifier name) {
        return current.getVariableType(name) != null;
    }

    @Nullable
    public Type getVariableType(@NotNull SimpleIdentifier name) {
        return current.getVariableType(name);
    }

    public Optional<VariableDeclaration> getVariableDeclaration(@NotNull SimpleIdentifier name, @Nullable Type type) {
        return current.getVariableDeclaration(name, type);
    }

    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type, boolean createIfNotExists) {
        current.changeVariableType(name, type, createIfNotExists);
    }

    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type) {
        changeVariableType(name, type, true);
    }

    public void registerImport(@NotNull Import importDeclaration) {
        imports.registerImport(importDeclaration);
    }

    public void registerTypeDeclaration(@NotNull Type type, @NotNull Declaration declaration) {
        types.registerTypeDeclaration(type, declaration);
    }

    public Identifier registerType(@NotNull Identifier name, @NotNull Type type) {
        if (current.getParent() != null) {
            return current.registerType(name, type);
        }
        return types.registerType(name, type);
    }

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        if (current.getParent() == null) {
            symbols.registerDeclaration(name, declaration);
            if (declaration instanceof ClassDeclaration cls) {
                Type type = cls.getTypeNode();
                registerTypeDeclaration(type, declaration);
                registerType(name, type);
            }
        } else {
            current.registerDeclaration(name, declaration);
        }
    }

    public void registerDefinition(@NotNull SimpleIdentifier name, @NotNull Definition definition) {
        registerDeclaration(name, definition.getDeclaration());
        symbols.registerDefinition(definition.getDeclaration(), definition);
    }

    public void registerDefinition(@NotNull Declaration declaration, @NotNull Definition definition) {
        symbols.registerDefinition(declaration, definition);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name, @Nullable Class<? extends Declaration> clazz) {
        return findDeclaration(name, clazz, ScopeLookupMode.VISIBLE);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name,
                                                 @Nullable Class<? extends Declaration> clazz,
                                                 @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclaration(name, clazz);
            case GLOBAL -> symbols.findDeclaration(name, clazz);
            case VISIBLE -> current.findDeclaration(name, clazz)
                    .or(() -> symbols.findDeclaration(name, clazz));
        };
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz) {
        return findDeclaration(clazz, ScopeLookupMode.VISIBLE);
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz,
                                             @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclaration(clazz);
            case GLOBAL -> symbols.findDeclaration(clazz);
            case VISIBLE -> {
                var localDeclarations = current.findDeclaration(clazz);
                yield localDeclarations.isEmpty() ? symbols.findDeclaration(clazz) : localDeclarations;
            }
        };
    }

    public Optional<Definition> findDefinition(@NotNull SimpleIdentifier name, @Nullable Class<? extends Declaration> declarationClass) {
        return findDefinition(name, declarationClass, ScopeLookupMode.VISIBLE);
    }

    public Optional<Definition> findDefinition(@NotNull SimpleIdentifier name,
                                               @Nullable Class<? extends Declaration> declarationClass,
                                               @NotNull ScopeLookupMode mode) {
        return findDeclaration(name, declarationClass, mode).flatMap(this::findDefinition);
    }

    public Optional<Definition> findDefinition(@NotNull Declaration declaration) {
        return symbols.findDefinition(declaration);
    }

    public List<Definition> findDefinition(@NotNull Class<? extends Definition> clazz) {
        return symbols.findDefinition(clazz);
    }

    public Optional<Type> findType(@NotNull Identifier name) {
        return findType(name, ScopeLookupMode.VISIBLE);
    }

    public Optional<Type> findType(@NotNull Identifier name, @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentType(name);
            case GLOBAL -> types.findType(name);
            case VISIBLE -> current.findType(name).or(() -> types.findType(name));
        };
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type) {
        return findTypeDeclaration(type, ScopeLookupMode.VISIBLE);
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type, @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentTypeDeclaration(type);
            case GLOBAL -> types.findTypeDeclaration(type);
            case VISIBLE -> current.findTypeDeclaration(type).or(() -> types.findTypeDeclaration(type));
        };
    }

    public TypeHierarchy typeHierarchy() {
        return types.hierarchy();
    }

    public Map<UserType, Set<UserType>> userTypeHierarchy() {
        return typeHierarchy().asMap();
    }

    public Set<UserType> directParents(@NotNull UserType type) {
        return typeHierarchy().directParents(type);
    }

    public Set<UserType> ancestors(@NotNull UserType type) {
        return typeHierarchy().ancestors(type);
    }

    public Set<UserType> descendants(@NotNull UserType type) {
        return typeHierarchy().descendants(type);
    }

    public boolean isSubtypeOf(@NotNull UserType possibleSubtype, @NotNull UserType possibleParent) {
        return typeHierarchy().isSubtypeOf(possibleSubtype, possibleParent);
    }

    public Set<UserType> userTypes() {
        return typeHierarchy().userTypes();
    }

    public Map<Identifier, Declaration> allDeclarations() {
        return symbols.allDeclarations();
    }

    public Map<Declaration, Definition> allDefinitions() {
        return symbols.allDefinitions();
    }

    public Map<Identifier, Type> allTypes() {
        return types.allTypes();
    }

    public Map<Type, Declaration> allTypeDeclarations() {
        return types.allTypeDeclarations();
    }

    public Set<Import> allImports() {
        return imports.allImports();
    }

    @Override
    public String toString() {
        return current.toString();
    }
}
