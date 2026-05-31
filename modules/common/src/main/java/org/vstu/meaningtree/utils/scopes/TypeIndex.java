package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.types.UserType;

import java.io.Serializable;
import java.util.*;

class TypeIndex implements Serializable {
    @NotNull
    private final Map<Type, Declaration> typeDeclarations = new HashMap<>();

    @NotNull
    private final Map<Identifier, Type> declaredTypes = new HashMap<>();

    @NotNull
    private final TypeHierarchy hierarchy = new TypeHierarchy();

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
        if (declaration instanceof ClassDeclaration classDeclaration && type instanceof UserType userType) {
            Set<UserType> parents = new LinkedHashSet<>();
            for (Type parent : classDeclaration.getParents()) {
                if (parent instanceof UserType parentUserType) {
                    parents.add(parentUserType);
                }
            }
            hierarchy.register(userType, parents);
        }
    }

    public Optional<Type> findType(@NotNull Identifier name) {
        return Optional.ofNullable(declaredTypes.get(name));
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type) {
        return Optional.ofNullable(typeDeclarations.get(type));
    }

    public Map<Identifier, Type> allTypes() {
        return Map.copyOf(declaredTypes);
    }

    public Map<Type, Declaration> allTypeDeclarations() {
        return Map.copyOf(typeDeclarations);
    }

    public TypeHierarchy hierarchy() {
        return hierarchy;
    }
}
