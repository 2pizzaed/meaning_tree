package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class SymbolIndex implements Serializable {
    @NotNull
    private final Map<SimpleIdentifier, Declaration> declarations = new HashMap<>();

    @NotNull
    private final Map<Declaration, Definition> definitions = new HashMap<>();

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        declarations.put(name, declaration);
    }

    public void registerDefinition(@NotNull Declaration declaration, @NotNull Definition definition) {
        definitions.put(declaration, definition);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name,
                                                 @Nullable java.lang.Class<? extends Declaration> clazz) {
        Declaration declaration = declarations.get(name);
        if (declaration != null && matchesDeclarationClass(declaration, clazz)) {
            return Optional.of(declaration);
        }
        return Optional.empty();
    }

    public List<Declaration> findDeclaration(@NotNull java.lang.Class<? extends Declaration> clazz) {
        return declarations.values().stream()
                .filter(declaration -> matchesDeclarationClass(declaration, clazz))
                .toList();
    }

    public Optional<Definition> findDefinition(@NotNull Declaration declaration) {
        return Optional.ofNullable(definitions.get(declaration));
    }

    public List<Definition> findDefinition(@NotNull java.lang.Class<? extends Definition> clazz) {
        return definitions.values().stream()
                .filter(definition -> clazz.isAssignableFrom(definition.getClass()))
                .toList();
    }

    public Map<Identifier, Declaration> allDeclarations() {
        return Map.copyOf(new HashMap<Identifier, Declaration>(declarations));
    }

    public Map<Declaration, Definition> allDefinitions() {
        return Map.copyOf(definitions);
    }

    private static boolean matchesDeclarationClass(@NotNull Declaration declaration,
                                                   @Nullable java.lang.Class<? extends Declaration> clazz) {
        return clazz == null || clazz.isAssignableFrom(declaration.getClass());
    }
}
