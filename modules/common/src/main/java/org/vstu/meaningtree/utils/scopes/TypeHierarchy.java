package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.types.UserType;

import java.io.Serializable;
import java.util.*;

public class TypeHierarchy implements Serializable {
    private final Map<UserType, Set<UserType>> parentsByType = new LinkedHashMap<>();

    public void register(@NotNull UserType type, @NotNull Set<UserType> parents) {
        parentsByType.put(type, new LinkedHashSet<>(parents));
        for (UserType parent : parents) {
            parentsByType.putIfAbsent(parent, new LinkedHashSet<>());
        }
    }

    public Set<UserType> userTypes() {
        return Collections.unmodifiableSet(parentsByType.keySet());
    }

    public Set<UserType> directParents(@NotNull UserType type) {
        return Collections.unmodifiableSet(parentsByType.getOrDefault(type, Set.of()));
    }

    public Set<UserType> ancestors(@NotNull UserType type) {
        Set<UserType> result = new LinkedHashSet<>();
        ArrayDeque<UserType> queue = new ArrayDeque<>(directParents(type));
        while (!queue.isEmpty()) {
            UserType current = queue.removeFirst();
            if (result.add(current)) {
                queue.addAll(directParents(current));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public Set<UserType> descendants(@NotNull UserType type) {
        Set<UserType> result = new LinkedHashSet<>();
        for (UserType candidate : parentsByType.keySet()) {
            if (!candidate.equals(type) && ancestors(candidate).contains(type)) {
                result.add(candidate);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    public boolean isSubtypeOf(@NotNull UserType possibleSubtype, @NotNull UserType possibleParent) {
        return possibleSubtype.equals(possibleParent) || ancestors(possibleSubtype).contains(possibleParent);
    }

    public Map<UserType, Set<UserType>> asMap() {
        Map<UserType, Set<UserType>> result = new LinkedHashMap<>();
        for (var entry : parentsByType.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }
}
