package org.vstu.meaningtree.utils.analysis.symbols;

import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.PythonTranslator;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.declarations.FieldDeclaration;
import org.vstu.meaningtree.nodes.declarations.MethodDeclaration;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.definitions.ObjectConstructorDefinition;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SelfReference;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.IntegerLiteral;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

class SymbolResolverTests {
    @Test
    void selfFieldExtractionRecognizesOnlyPlainSelfAssignments() {
        SymbolResolver resolver = emptyResolver();
        AssignmentStatement selfAssignment = assignment(new SelfReference("self"), "value");
        AssignmentStatement otherAssignment = assignment(new SimpleIdentifier("object"), "value");
        AssignmentStatement augmentedAssignment = new AssignmentStatement(
                new MemberAccess(new SelfReference("self"), new SimpleIdentifier("value")),
                new IntegerLiteral(1),
                AugmentedAssignmentOperator.ADD
        );

        assertEquals(new SimpleIdentifier("value"), resolver.extractSelfFieldName(selfAssignment).orElseThrow());
        assertTrue(resolver.extractSelfFieldName(otherAssignment).isEmpty());
        assertTrue(resolver.extractSelfFieldName(augmentedAssignment).isEmpty());
    }

    @Test
    void declaredFieldCollectionIndexesEveryDeclarator() {
        FieldDeclaration fields = new FieldDeclaration(
                new IntType(),
                new VariableDeclarator(new SimpleIdentifier("left")),
                new VariableDeclarator(new SimpleIdentifier("right"))
        );
        ClassDefinition classDefinition = classDefinition(fields);

        var declaredFields = emptyResolver().collectDeclaredFields(classDefinition);

        assertSame(fields, declaredFields.get(new SimpleIdentifier("left")));
        assertSame(fields, declaredFields.get(new SimpleIdentifier("right")));
    }

    @Test
    void typeSelectionPrefersKnownType() {
        SymbolResolver resolver = emptyResolver();

        assertInstanceOf(IntType.class, resolver.chooseResolvedType(new UnknownType(), new IntType()));
        assertInstanceOf(IntType.class, resolver.chooseResolvedType(new IntType(), new UnknownType()));
    }

    @Test
    void constructorAssignmentsCreateMissingFieldsWithoutDuplicatingDeclaredFields() {
        ClassDeclaration declaration = new ClassDeclaration(new SimpleIdentifier("A"));
        FieldDeclaration declared = new FieldDeclaration(new IntType(), new SimpleIdentifier("out"));
        ObjectConstructorDefinition constructor = constructor(
                declaration,
                assignment(new SelfReference("self"), "out"),
                assignment(new SelfReference("self"), "created")
        );
        ClassDefinition classDefinition = new ClassDefinition(
                declaration,
                new CompoundStatement(List.of(declared, constructor))
        );
        MeaningTree meaningTree = new MeaningTree(new ProgramEntryPoint(List.of(classDefinition)));

        new SymbolResolver(meaningTree, new ScopeTable()).resolveImplicitInstanceFields();

        List<FieldDeclaration> fields = classDefinition.getFields().stream()
                .map(FieldDeclaration.class::cast)
                .toList();
        assertEquals(List.of("out", "created"), fields.stream()
                .map(field -> field.getFirstDeclarator().getIdentifier().getName())
                .toList());
        assertInstanceOf(IntType.class, fields.get(1).getType());
        assertSame(declaration, fields.get(1).getParentDeclaration());
        assertFalse(fields.get(1).getFirstDeclarator().hasInitialization());
        assertSame(constructor, classDefinition.getBody().getNodes()[2]);
    }

    @Test
    void ordinaryInstanceMethodAssignmentCreatesField() {
        ClassDeclaration declaration = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDefinition method = method(
                declaration,
                List.of(DeclarationModifier.PUBLIC),
                assignment(new SelfReference("self"), "created")
        );
        ClassDefinition classDefinition = new ClassDefinition(
                declaration,
                new CompoundStatement(List.of(method))
        );

        new SymbolResolver(
                new MeaningTree(new ProgramEntryPoint(List.of(classDefinition))),
                new ScopeTable()
        ).resolveImplicitInstanceFields();

        assertEquals(List.of("created"), classDefinition.getFields().stream()
                .map(FieldDeclaration.class::cast)
                .map(field -> field.getFirstDeclarator().getIdentifier().getName())
                .toList());
    }

    @Test
    void staticMethodAssignmentDoesNotCreateInstanceField() {
        ClassDeclaration declaration = new ClassDeclaration(new SimpleIdentifier("A"));
        MethodDefinition method = method(
                declaration,
                List.of(DeclarationModifier.PUBLIC, DeclarationModifier.STATIC),
                assignment(new SelfReference("self"), "created")
        );
        ClassDefinition classDefinition = new ClassDefinition(
                declaration,
                new CompoundStatement(List.of(method))
        );

        new SymbolResolver(
                new MeaningTree(new ProgramEntryPoint(List.of(classDefinition))),
                new ScopeTable()
        ).resolveImplicitInstanceFields();

        assertTrue(classDefinition.getFields().isEmpty());
    }

    @Test
    void pythonPostprocessResolvesFieldsAssignedInAnyInstanceMethod() {
        PythonTranslator translator = new PythonTranslator(Map.of(
                "translationUnitMode", "simple",
                "skipErrors", false
        ));

        MeaningTree meaningTree = translator.getMeaningTree("""
                class A:
                    out: int = 567

                    def __init__(self):
                        self.out = 5
                        self.k: int = 5
                        self.m = hello()

                    def update(self):
                        self.n = 7
                """);

        ClassDefinition classDefinition = StreamSupport
                .stream(meaningTree.spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(ClassDefinition.class::isInstance)
                .map(ClassDefinition.class::cast)
                .findFirst()
                .orElseThrow();
        List<FieldDeclaration> fields = classDefinition.getFields().stream()
                .map(FieldDeclaration.class::cast)
                .toList();

        assertEquals(List.of("out", "k", "m", "n"), fields.stream()
                .map(field -> field.getFirstDeclarator().getIdentifier().getName())
                .toList());
        assertInstanceOf(IntType.class, fields.get(1).getType());
        assertInstanceOf(UnknownType.class, fields.get(2).getType());
        assertInstanceOf(IntType.class, fields.get(3).getType());
    }

    @Test
    void assignedValueTypeHeuristicUsesNearestLexicalScope() {
        PythonTranslator translator = new PythonTranslator(Map.of(
                "translationUnitMode", "simple",
                "skipErrors", false
        ));

        MeaningTree meaningTree = translator.getMeaningTree("""
                class A:
                    def __init__(self):
                        local: int = 5
                        self.value = local
                """);

        FieldDeclaration field = StreamSupport.stream(meaningTree.spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(FieldDeclaration.class::isInstance)
                .map(FieldDeclaration.class::cast)
                .filter(candidate -> candidate.getFirstDeclarator().getIdentifier().getName().equals("value"))
                .findFirst()
                .orElseThrow();

        assertInstanceOf(IntType.class, field.getType());
    }

    private static SymbolResolver emptyResolver() {
        return new SymbolResolver(
                new MeaningTree(new ProgramEntryPoint(List.of())),
                new ScopeTable()
        );
    }

    private static AssignmentStatement assignment(org.vstu.meaningtree.nodes.Expression owner, String fieldName) {
        return new AssignmentStatement(
                new MemberAccess(owner, new SimpleIdentifier(fieldName)),
                new IntegerLiteral(5)
        );
    }

    private static ObjectConstructorDefinition constructor(ClassDeclaration owner, Node... statements) {
        return new ObjectConstructorDefinition(
                owner.getTypeNode(),
                new SimpleIdentifier("__init__"),
                List.of(),
                List.of(DeclarationModifier.PUBLIC),
                List.of(),
                new CompoundStatement(statements)
        );
    }

    private static MethodDefinition method(ClassDeclaration owner,
                                           List<DeclarationModifier> modifiers,
                                           Node... statements) {
        return new MethodDefinition(
                new MethodDeclaration(
                        owner.getTypeNode(),
                        new SimpleIdentifier("update"),
                        new UnknownType(),
                        List.of(),
                        modifiers,
                        List.of()
                ),
                new CompoundStatement(statements)
        );
    }

    private static ClassDefinition classDefinition(Node... body) {
        return new ClassDefinition(
                new ClassDeclaration(new SimpleIdentifier("A")),
                new CompoundStatement(body)
        );
    }
}
