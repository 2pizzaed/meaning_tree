package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Definition;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code TypedEntities} представляет набор сущностей текущей области видимости,
 * включая переменные и методы с хранящимся возвращаемым типом.
 * Поддерживает вложенные области через ссылку на родительский {@code TypedEntities}.
 */
public class TypedEntities {
    /**
     * Родительская коллекция сущностей, соответствующая вышестоящей области видимости.
     */
    @Nullable
    private final TypedEntities parent;

    @Nullable
    private Node owner;

    /**
     * Хранилище типов переменных в текущей области (для класса - его полей)
     */
    @NotNull
    private final Map<SimpleIdentifier, Type> variables;

    @NotNull
    private final Map<SimpleIdentifier, VariableDeclaration> variableDeclarations;

    /**
     * Все определения в данной области видимости (переменные не учитываются)
     */
    @NotNull
    private final Map<Declaration, Definition> definitions;

    /**
     * Все объявления типов в данной области (переменные не учитываются)
     */
    @NotNull
    private final Map<Type, Declaration> typeDeclarations;


    @NotNull
    private final Map<SimpleIdentifier, Declaration> availableDeclarations;

    /**
     * Хранилище всех доступных типов в текущей области.
     */
    @NotNull
    private final Map<Identifier, Type> availableTypes;

    /**
     * Создаёт новый набор сущностей с родителем.
     *
     * @param parent родительский набор сущностей или {@code null} для корневого
     * @param owner для какого узла вводится данная область
     */
    public TypedEntities(@Nullable TypedEntities parent, @Nullable Node owner) {
        this.parent = parent;
        this.owner = owner;
        this.variables = new HashMap<>();
        this.availableTypes = new HashMap<>();
        this.definitions = new HashMap<>();
        this.typeDeclarations = new HashMap<>();
        this.variableDeclarations = new HashMap<>();
        this.availableDeclarations = new HashMap<>();
    }

    /**
     * Создаёт новый набор сущностей с родителем.
     *
     * @param parent родительский набор сущностей или {@code null} для корневого
     */
    public TypedEntities(@Nullable TypedEntities parent) {
        this(parent, null);
    }

    /**
     * Добавляет переменную с указанным типом в текущую область.
     *
     */
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

    /**
     * Добавляет метод с указанным возвращаемым типом
     * @param type       тип, который нужно регистрировать {@code null}
     */
    public Identifier registerType(@NotNull Identifier name, @NotNull Type type) {
        if (!availableTypes.containsValue(type)) {
            availableTypes.put(name, type);
            return name;
        } else {
            for (var pair : availableTypes.entrySet()) {
                if (pair.getValue().equals(type)) {
                    return pair.getKey();
                }
            }
        }
        return null;
    }

    public void removeVariable(@NotNull SimpleIdentifier name) {
        if (!variables.containsKey(name) && parent != null) {
            parent.removeVariable(name);
            return;
        }
        variables.remove(name);
    }

    public boolean hasVariable(@NotNull SimpleIdentifier name) {
        return variables.containsKey(name);
    }

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration decl) {
        if (decl instanceof ClassDeclaration cls) {
            typeDeclarations.put(cls.getTypeNode(), decl);
            registerType(name, cls.getTypeNode());
        }
        availableDeclarations.put(name, decl);
        definitions.put(decl, null);
    }

    public void registerDefinition(@NotNull SimpleIdentifier name, @NotNull Definition def) {
        registerDeclaration(name, def.getDeclaration());
        definitions.put(def.getDeclaration(), def);
    }

    /**
     * Ищет тип переменной в текущей и родительских областях.
     *
     * @param name идентификатор переменной, не может быть {@code null}
     * @return найденный тип или {@code null}, если переменная не объявлена
     */
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
        if (variableDeclarations.containsKey(name) && (type == null || variables.containsKey(name))) {
            return Optional.of(variableDeclarations.get(name));
        }
        if (parent != null) {
            return parent.getVariableDeclaration(name, type);
        }
        return Optional.empty();
    }

    /**
     * Изменяет или создаёт переменную в текущей области.
     *
     * @param name                идентификатор переменной, не может быть {@code null}
     * @param type                новый тип переменной, не может быть {@code null}
     * @param createIfNotExists   если {@code true}, создаёт переменную при отсутствии;
     *                            иначе выбрасывает исключение
     * @throws IllegalArgumentException если переменная отсутствует и
     *                                  {@code createIfNotExists} равно {@code false}
     */
    public void changeVariableType(@NotNull SimpleIdentifier name,
                                   @NotNull Type type,
                                   boolean createIfNotExists) {
        if (getVariableType(name) == null && !createIfNotExists) {
            throw new IllegalArgumentException("No such variable: " + name);
        }
        variables.put(name, type);
    }

    /**
     * Изменяет или создаёт переменную в текущей области.
     *
     * @param name идентификатор переменной, не может быть {@code null}
     * @param type новый тип переменной, не может быть {@code null}
     */
    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type) {
        changeVariableType(name, type, true);
    }

    /**
     * Ищет возвращаемый тип метода/функции в текущей и родительских областях.
     *
     * @param name идентификатор метода, не может быть {@code null}
     * @return найденный тип или {@link UnknownType}, если метод не объявлен
     */
    @NotNull
    public Type getFunctionReturnType(@NotNull SimpleIdentifier name) {
        var method = findDeclaration(name, FunctionDeclaration.class);
        if (method.isPresent()) {
            FunctionDeclaration methodDecl = (FunctionDeclaration) method.get();
            return methodDecl.getReturnType();
        }
        return new UnknownType();
    }

    /**
     * Возвращает карту переменных текущей области.
     *
     * @return карта из идентификаторов переменных в типы
     */
    @NotNull
    public Map<SimpleIdentifier, Type> getCurrentVariables() {
        return variables;
    }

    /**
     * Возвращает карту методов текущей области.
     *
     * @return карта из идентификаторов методов в возвращаемые типы
     */
    @NotNull
    public Map<Identifier, Type> getCurrentAvailableTypes() {
        return availableTypes;
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name, Class<? extends Declaration> clazz) {
        for (var declaration : availableDeclarations.entrySet()) {
            if (declaration.getKey().equals(name) && (clazz == null || declaration.getValue().getClass().isAssignableFrom(clazz))) {
                return Optional.of(declaration.getValue());
            }
        }
        if (parent != null) {
            return parent.findDeclaration(name, clazz);
        }
        return Optional.empty();
    }

    public Optional<Definition> findDefinition(@NotNull SimpleIdentifier name, Class<? extends Declaration> declarationClass) {
        var decl = findDeclaration(name, declarationClass);
        if (decl.isPresent() && definitions.containsKey(decl)) {
            return Optional.of(definitions.get(decl));
        }
        if (parent != null) {
            return parent.findDefinition(name, declarationClass);
        }
        return Optional.empty();
    }

    public Optional<Definition> findDefinition(@NotNull Declaration name) {
        if (definitions.containsKey(name) && definitions.get(name) != null) {
            return Optional.of(definitions.get(name));
        }
        if (parent != null) {
            var result = parent.findDefinition(name);
            if (definitions.containsKey(name) && definitions.get(name) == null && result.isPresent()) {
                definitions.put(name, result.get());
            }
            return result;
        }
        return Optional.empty();
    }

    public List<Declaration> findDeclaration(Class<? extends Declaration> clazz) {
        var result = availableDeclarations.values().stream().filter(x -> x.getClass().isAssignableFrom(clazz)).toList();
        if (result.isEmpty() && parent != null) {
            return parent.findDeclaration(clazz);
        }
        return result;
    }

    public List<Definition> findDefinition(Class<? extends Definition> clazz) {
        var result = definitions.values().stream().filter(x -> x.getClass().isAssignableFrom(clazz)).toList();
        if (result.isEmpty() && parent != null) {
            return parent.findDefinition(clazz);
        }
        return result;
    }

    public Optional<Type> findType(@NotNull Identifier name) {
        if (availableTypes.containsValue(name)) {
            return Optional.of(availableTypes.get(name));
        }
        if (parent != null) {
            return parent.findType(name);
        }
        return Optional.empty();
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type) {
       if (!availableTypes.containsValue(type) && parent != null) {
           return parent.findTypeDeclaration(type);
       }
       if (typeDeclarations.containsValue(type)) {
           return Optional.of(typeDeclarations.get(type));
       }
       if (parent != null) {
            return parent.findTypeDeclaration(type);
       }
       return Optional.empty();
    }

    /**
     * Возвращает родительский {@code TypedEntities}.
     *
     * @return родитель или {@code null} для корня
     */
    @Nullable
    public TypedEntities getParent() {
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

    public void setOwner(Node node) {
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
