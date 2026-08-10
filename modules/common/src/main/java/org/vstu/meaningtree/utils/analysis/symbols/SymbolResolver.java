package org.vstu.meaningtree.utils.analysis.symbols;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.FieldDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SelfReference;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.utils.analysis.types.SimpleTypeInferrer;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.*;

/**
 * Выполняет консервативное разрешение символов после парсинга, для которого
 * необходимы полностью построенные {@link MeaningTree} и лексическая
 * {@link ScopeTable}.
 *
 * <p>Класс представляет общий этап постобработки семантических фактов, которые
 * языковой парсер не может надёжно установить во время построения дерева.
 * Каждое правило разрешения оформлено как явно названная эвристика: она может
 * дополнить дерево, если исходный код содержит достаточно информации, но не
 * заменяет связывание имён или полную проверку типов. Если информации
 * недостаточно, исходное дерево сохраняется либо тип остаётся
 * {@link UnknownType}.</p>
 *
 * <p>Текущая эвристика распознаёт обычные присваивания вида
 * {@code self/this.<name>} в любом нестатическом методе. Она уточняет неизвестный
 * тип существующего поля либо добавляет объявление ранее не объявленного поля
 * экземпляра, оставляя само присваивание без изменений.</p>
 */
public final class SymbolResolver {
    private final MeaningTree meaningTree;
    private final ScopeTable scopeTable;

    public SymbolResolver(@NotNull MeaningTree meaningTree, @NotNull ScopeTable scopeTable) {
        this.meaningTree = Objects.requireNonNull(meaningTree);
        this.scopeTable = Objects.requireNonNull(scopeTable);
    }

    // Эвристики разрешения. Методы этого раздела могут изменять AST или области видимости.

    /** Запускает все эвристики разрешения символов после парсинга. */
    public void resolve() {
        resolveImplicitInstanceFields();
    }

    /**
     * Разрешает поля экземпляра, вводимые присваиваниями вида
     * {@code self.value = expression} в нестатических методах.
     */
    public void resolveImplicitInstanceFields() {
        boolean structureChanged = false;
        for (ClassDefinition classDefinition : findClassDefinitions()) {
            structureChanged |= resolveImplicitInstanceFields(classDefinition);
        }
        if (structureChanged) {
            meaningTree.invalidateCache();
        }
    }

    boolean resolveImplicitInstanceFields(ClassDefinition classDefinition) {
        Map<SimpleIdentifier, FieldDeclaration> declaredFields = collectDeclaredFields(classDefinition);
        Map<SimpleIdentifier, Type> implicitFields = new LinkedHashMap<>();

        for (MethodDefinition method : findInstanceMethods(classDefinition)) {
            resolveImplicitFieldsInMethod(method, declaredFields, implicitFields);
        }

        attachImplicitFields(classDefinition, implicitFields);
        return !implicitFields.isEmpty();
    }

    void resolveImplicitFieldsInMethod(MethodDefinition method,
                                       Map<SimpleIdentifier, FieldDeclaration> declaredFields,
                                       Map<SimpleIdentifier, Type> implicitFields) {
        for (NodeInfo nodeInfo : findAssignments(method)) {
            AssignmentStatement assignment = (AssignmentStatement) nodeInfo.node();

            Optional<SimpleIdentifier> fieldName = extractSelfFieldName(assignment);
            if (fieldName.isEmpty()) {
                continue;
            }

            Type assignedType = inferAssignedValueType(assignment, nodeInfo);
            FieldDeclaration declaredField = declaredFields.get(fieldName.get());
            if (declaredField != null) {
                refineDeclaredFieldType(declaredField, assignedType);
                continue;
            }

            implicitFields.merge(fieldName.get(), assignedType, this::chooseResolvedType);
        }
    }

    void refineDeclaredFieldType(FieldDeclaration field, Type assignedType) {
        field.setType(chooseResolvedType(field.getType(), assignedType));
    }

    void attachImplicitFields(ClassDefinition classDefinition, Map<SimpleIdentifier, Type> implicitFields) {
        int insertionIndex = findFieldInsertionIndex(classDefinition);
        for (Map.Entry<SimpleIdentifier, Type> entry : implicitFields.entrySet()) {
            FieldDeclaration field = createImplicitField(entry.getKey(), entry.getValue(), classDefinition);
            classDefinition.getBody().insert(insertionIndex++, field);
            classDefinition.getBody().getScope().ifPresent(scope -> scope.registerVariable(field));
        }
    }

    // Вспомогательные методы. Они не изменяют существующие AST и области видимости.

    List<ClassDefinition> findClassDefinitions() {
        List<ClassDefinition> classes = new ArrayList<>();
        for (NodeInfo nodeInfo : meaningTree) {
            if (nodeInfo.node() instanceof ClassDefinition classDefinition) {
                classes.add(classDefinition);
            }
        }
        return classes;
    }

    Map<SimpleIdentifier, FieldDeclaration> collectDeclaredFields(ClassDefinition classDefinition) {
        Map<SimpleIdentifier, FieldDeclaration> result = new LinkedHashMap<>();
        for (Node node : classDefinition.getBody().getNodes()) {
            if (!(node instanceof FieldDeclaration field)) {
                continue;
            }
            for (VariableDeclarator declarator : field.getDeclarators()) {
                result.put(declarator.getIdentifier(), field);
            }
        }
        return result;
    }

    List<MethodDefinition> findInstanceMethods(ClassDefinition classDefinition) {
        List<MethodDefinition> methods = new ArrayList<>();
        for (Node node : classDefinition.getBody().getNodes()) {
            if (node instanceof MethodDefinition method
                    && !method.getDeclaration().getModifiers().contains(DeclarationModifier.STATIC)) {
                methods.add(method);
            }
        }
        return methods;
    }

    List<NodeInfo> findAssignments(MethodDefinition method) {
        List<NodeInfo> assignments = new ArrayList<>();
        for (NodeInfo nodeInfo : method.getBody()) {
            if (nodeInfo.node() instanceof AssignmentStatement) {
                assignments.add(nodeInfo);
            }
        }
        assignments.sort(Comparator.comparingLong(NodeInfo::id));
        return assignments;
    }

    Optional<SimpleIdentifier> extractSelfFieldName(AssignmentStatement assignment) {
        if (assignment.getAugmentedOperator() != AugmentedAssignmentOperator.NONE
                || !(assignment.getLValue() instanceof MemberAccess memberAccess)
                || !(memberAccess.getExpression() instanceof SelfReference)) {
            return Optional.empty();
        }
        return Optional.of(memberAccess.getMember());
    }

    Type inferAssignedValueType(AssignmentStatement assignment, NodeInfo nodeInfo) {
        if (assignment.getRValue() == null) {
            return new UnknownType();
        }
        Optional<Long> scopeId = findNearestScopeId(nodeInfo);
        long previousScopeId = scopeTable.currentScopeId();
        try {
            scopeId.ifPresent(scopeTable::setCurrentScope);
            return SimpleTypeInferrer.inference(assignment.getRValue(), scopeTable);
        } finally {
            scopeTable.setCurrentScope(previousScopeId);
        }
    }

    Optional<Long> findNearestScopeId(NodeInfo nodeInfo) {
        NodeInfo current = nodeInfo;
        while (current != null) {
            if (current.node() instanceof CompoundStatement body && body.getScopeId().isPresent()) {
                return Optional.of(body.getScopeId().getAsLong());
            }
            current = current.parent();
        }
        return Optional.empty();
    }

    Type chooseResolvedType(Type currentType, Type candidateType) {
        if (currentType instanceof UnknownType) {
            return candidateType;
        }
        if (candidateType instanceof UnknownType) {
            return currentType;
        }
        return SimpleTypeInferrer.chooseGeneralType(currentType, candidateType);
    }

    int findFieldInsertionIndex(ClassDefinition classDefinition) {
        Node[] nodes = classDefinition.getBody().getNodes();
        int result = 0;
        while (result < nodes.length && nodes[result] instanceof FieldDeclaration) {
            result++;
        }
        return result;
    }

    FieldDeclaration createImplicitField(SimpleIdentifier name, Type type, ClassDefinition owner) {
        FieldDeclaration field = new FieldDeclaration(
                type,
                List.of(DeclarationModifier.PUBLIC),
                new VariableDeclarator((SimpleIdentifier) name.freshClone())
        );
        field.setParentDeclaration(owner.getDeclaration());
        return field;
    }
}
