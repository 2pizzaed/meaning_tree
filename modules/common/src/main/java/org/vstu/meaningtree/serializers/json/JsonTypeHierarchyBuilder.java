package org.vstu.meaningtree.serializers.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.TransliterationUtils;

import java.util.*;

public class JsonTypeHierarchyBuilder {

    /**
     * Generate JSON hierarchy as JsonObject
     * @return JsonObject with type hierarchy
     */
    public static JsonObject generateHierarchyJsonObject() {
        JsonObject root = new JsonObject();

        // Get all registered types
        Set<String> registeredTypes = getAllRegisteredTypes();

        for (String type : registeredTypes) {
            Class<? extends Node> nodeClass = JsonNodeTypeClassMapper.getClassForType(type);
            if (nodeClass != null) {
                fillHierarchy(root, type, nodeClass);
            }
            var subclasses = nodeClass.getPermittedSubclasses();
            if (subclasses != null) {
                for (Class<?> subclassClass : subclasses) {
                    Class<? extends Node> subclassNode  = (Class<? extends Node>) subclassClass;
                    fillHierarchy(root, TransliterationUtils.camelToSnake(subclassNode.getSimpleName()), subclassNode);
                }
            }
        }

        return root;
    }

    private static void fillHierarchy(JsonObject root, String type, Class<? extends Node> nodeClass) {
        JsonArray parents = getParentTypes(nodeClass);
        root.add(type, parents);
    }

    /**
     * Get parent types for a given node class
     * @param nodeClass Node class
     * @return JsonArray of parent type strings
     */
    private static JsonArray getParentTypes(Class<? extends Node> nodeClass) {
        JsonArray parents = new JsonArray();
        List<String> parentTypes = new ArrayList<>();

        // Walk up the class hierarchy
        Class<?> currentClass = nodeClass.getSuperclass();

        while (currentClass != null && Node.class.isAssignableFrom(currentClass)) {
            // Skip abstract classes and Node itself if needed
            if (currentClass != Node.class) {
                @SuppressWarnings("unchecked")
                Class<? extends Node> parentNodeClass = (Class<? extends Node>) currentClass;

                // Try to get registered type
                String parentType = JsonNodeTypeClassMapper.getTypeForClass(parentNodeClass, false);
                parentTypes.add(parentType);
            }

            currentClass = currentClass.getSuperclass();
        }

        // Add to JsonArray
        for (String parentType : parentTypes) {
            parents.add(parentType);
        }

        return parents;
    }

    /**
     * Get all registered types from JsonTypeClassMapper
     * Uses reflection to access the private TYPE_TO_CLASS map
     * @return Set of all registered type strings
     */
    private static Set<String> getAllRegisteredTypes() {
        Set<String> types = new TreeSet<>(); // TreeSet for sorted output

        try {
            var field = JsonNodeTypeClassMapper.class.getDeclaredField("TYPE_TO_CLASS");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Class<? extends Node>> map = (Map<String, Class<? extends Node>>) field.get(null);
            types.addAll(map.keySet());
        } catch (Exception e) {
            throw new RuntimeException("Failed to access TYPE_TO_CLASS map", e);
        }

        return types;
    }

    /**
     * Get parent types for a specific JSON type
     * @param jsonType JSON type string
     * @return List of parent type strings, empty if type not found
     */
    public static List<String> getParentTypesForType(String jsonType) {
        List<String> result = new ArrayList<>();
        Class<? extends Node> nodeClass = JsonNodeTypeClassMapper.getClassForType(jsonType);

        if (nodeClass != null) {
            JsonArray parents = getParentTypes(nodeClass);
            for (var element : parents) {
                result.add(element.getAsString());
            }
        }

        return result;
    }

    /**
     * Check if one type is a parent of another
     * @param childType JSON type of potential child
     * @param parentType JSON type of potential parent
     * @return true if parentType is in the hierarchy of childType
     */
    public static boolean isParentOf(String childType, String parentType) {
        List<String> parents = getParentTypesForType(childType);
        return parents.contains(parentType);
    }

    /**
     * Get all types that inherit from a given type
     * @param parentType JSON type of parent
     * @return Set of all child types
     */
    public static Set<String> getAllChildrenOf(String parentType) {
        Set<String> children = new HashSet<>();
        Set<String> allTypes = getAllRegisteredTypes();

        for (String type : allTypes) {
            if (isParentOf(type, parentType)) {
                children.add(type);
            }
        }

        return children;
    }
}
