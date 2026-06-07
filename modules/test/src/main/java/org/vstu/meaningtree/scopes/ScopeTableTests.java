package org.vstu.meaningtree.scopes;

import com.google.gson.JsonObject;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.declarations.FunctionDeclaration;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.serializers.json.JsonDeserializer;
import org.vstu.meaningtree.serializers.json.JsonSerializer;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.scopes.ScopeLookupMode;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

public class ScopeTableTests {
    @Test
    void classDeclarationOwnsStableTypeNode() {
        ClassDeclaration declaration = new ClassDeclaration(new SimpleIdentifier("Box"));

        assertSame(declaration.getTypeNode(), declaration.getTypeNode());
    }

    @Test
    void lookupModeSeparatesCurrentVisibleAndGlobalScopes() {
        ScopeTable scope = new ScopeTable();
        FunctionDeclaration global = functionDeclaration("global");
        FunctionDeclaration local = functionDeclaration("local");

        scope.registerDeclaration(global.getName(), global);
        scope.enter();
        scope.registerDeclaration(local.getName(), local);

        assertTrue(scope.findDeclaration(global.getName(), FunctionDeclaration.class, ScopeLookupMode.GLOBAL).isPresent());
        assertTrue(scope.findDeclaration(global.getName(), FunctionDeclaration.class, ScopeLookupMode.VISIBLE).isPresent());
        assertTrue(scope.findDeclaration(global.getName(), FunctionDeclaration.class, ScopeLookupMode.CURRENT).isEmpty());

        assertTrue(scope.findDeclaration(local.getName(), FunctionDeclaration.class, ScopeLookupMode.CURRENT).isPresent());
        assertTrue(scope.findDeclaration(local.getName(), FunctionDeclaration.class, ScopeLookupMode.VISIBLE).isPresent());
        assertTrue(scope.findDeclaration(local.getName(), FunctionDeclaration.class, ScopeLookupMode.GLOBAL).isEmpty());
    }

    @Test
    void typeHierarchyProvidesParentsAncestorsAndDescendants() {
        ClassDeclaration base = new ClassDeclaration(new SimpleIdentifier("Base"));
        ClassDeclaration child = new ClassDeclaration(
                List.of(),
                new SimpleIdentifier("Child"),
                List.of(),
                base.getTypeNode()
        );

        ScopeTable scope = new ScopeTable();
        scope.registerDeclaration(base.getName().getSimpleIdentifierOrThrow(), base);
        scope.registerDeclaration(child.getName().getSimpleIdentifierOrThrow(), child);

        UserType baseType = base.getTypeNode();
        UserType childType = child.getTypeNode();

        assertEquals(List.of(baseType), scope.directParents(childType).stream().toList());
        assertTrue(scope.ancestors(childType).contains(baseType));
        assertTrue(scope.descendants(baseType).contains(childType));
        assertTrue(scope.isSubtypeOf(childType, baseType));
        assertFalse(scope.isSubtypeOf(baseType, childType));
    }

    @Test
    void jsonSourceMapRoundTripRestoresScopeTable() {
        ClassDeclaration base = new ClassDeclaration(new SimpleIdentifier("Base"));
        ClassDeclaration child = new ClassDeclaration(
                List.of(),
                new SimpleIdentifier("Child"),
                List.of(),
                base.getTypeNode()
        );
        ClassDefinition baseDefinition = new ClassDefinition(base, new CompoundStatement());
        ClassDefinition childDefinition = new ClassDefinition(child, new CompoundStatement());

        ScopeTable scope = new ScopeTable();
        scope.registerDefinition(base.getName().getSimpleIdentifierOrThrow(), baseDefinition);
        scope.registerDefinition(child.getName().getSimpleIdentifierOrThrow(), childDefinition);

        ProgramEntryPoint root = new ProgramEntryPoint(List.of(baseDefinition, childDefinition));
        SourceMap sourceMap = new SourceMap(
                "class Base {}\nclass Child extends Base {}",
                root,
                Map.<Long, Pair<Integer, Integer>>of(),
                scope,
                "test"
        );

        JsonObject json = new JsonSerializer().serialize(sourceMap);
        JsonObject serializedScopeTable = json.getAsJsonObject("scope_table");
        assertTrue(serializedScopeTable.has("symbols"));
        assertTrue(serializedScopeTable.has("types"));
        assertTrue(serializedScopeTable.has("imports"));
        assertEquals(2, serializedScopeTable.getAsJsonObject("types").getAsJsonArray("hierarchy").size());

        SourceMap restored = new JsonDeserializer().deserializeSourceMap(json);
        ScopeTable restoredScope = restored.scopeTable();

        assertEquals(2, restoredScope.allDefinitions().size());
        assertTrue(restoredScope.findDeclaration(new SimpleIdentifier("Base"), ClassDeclaration.class, ScopeLookupMode.GLOBAL).isPresent());
        assertTrue(restoredScope.findDeclaration(new SimpleIdentifier("Child"), ClassDeclaration.class, ScopeLookupMode.GLOBAL).isPresent());

        Type restoredBaseType = restoredScope.findType(new SimpleIdentifier("Base"), ScopeLookupMode.GLOBAL).orElseThrow();
        Type restoredChildType = restoredScope.findType(new SimpleIdentifier("Child"), ScopeLookupMode.GLOBAL).orElseThrow();

        assertInstanceOf(UserType.class, restoredBaseType);
        assertInstanceOf(UserType.class, restoredChildType);
        assertTrue(restoredScope.isSubtypeOf((UserType) restoredChildType, (UserType) restoredBaseType));
        assertEquals(2, StreamSupport.stream(restored.root().spliterator(), false)
                .filter(nodeInfo -> nodeInfo.node() instanceof ClassDefinition)
                .map(nodeInfo -> (ClassDefinition) nodeInfo.node())
                .map(ClassDefinition::getDeclaration)
                .map(ClassDeclaration::getTypeNode)
                .map(Node::getId)
                .distinct()
                .count());
    }

    @Test
    void compoundStatementScopeRoundTripRestoresBodyBinding() {
        FunctionDeclaration local = functionDeclaration("local");
        CompoundStatement body = new CompoundStatement(List.of(local));
        ScopeTable scope = new ScopeTable();
        scope.enter(body);
        scope.registerDeclaration(local.getName(), local);

        assertTrue(body.getScope().isPresent());

        ProgramEntryPoint root = new ProgramEntryPoint(List.of(body));
        SourceMap sourceMap = new SourceMap(
                "{ local }",
                root,
                Map.<Long, Pair<Integer, Integer>>of(),
                scope,
                "test"
        );

        JsonObject json = new JsonSerializer().serialize(sourceMap);
        JsonObject serializedScopeTable = json.getAsJsonObject("scope_table");
        assertEquals(2, serializedScopeTable.getAsJsonArray("scopes").size());
        assertTrue(json.getAsJsonObject("origin")
                .getAsJsonArray("body")
                .get(0)
                .getAsJsonObject()
                .has("scope_id"));

        SourceMap restored = new JsonDeserializer().deserializeSourceMap(json);
        CompoundStatement restoredBody = StreamSupport.stream(restored.root().spliterator(), false)
                .map(nodeInfo -> nodeInfo.node())
                .filter(CompoundStatement.class::isInstance)
                .map(CompoundStatement.class::cast)
                .findFirst()
                .orElseThrow();

        assertTrue(restoredBody.getScope().isPresent());
        assertEquals(
                restoredBody.getScope().orElseThrow().getId(),
                restored.scopeTable().currentScopeId()
        );
        assertTrue(restored.scopeTable()
                .findDeclaration(local.getName(), FunctionDeclaration.class, ScopeLookupMode.CURRENT)
                .isPresent());
    }

    private static FunctionDeclaration functionDeclaration(String name) {
        return new FunctionDeclaration(new SimpleIdentifier(name), new UnknownType(), List.of());
    }
}
