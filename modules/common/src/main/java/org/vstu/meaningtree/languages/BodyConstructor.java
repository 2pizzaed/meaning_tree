package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.declarations.EnumDeclaration;
import org.vstu.meaningtree.nodes.declarations.FieldDeclaration;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.scopes.ScopeTableElement;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BodyConstructor implements Iterable<Node> {
    protected TranslatorContext ctx;
    private boolean newScope;
    private ScopeTableElement scope;
    protected List<Node> nodes = new ArrayList<>();
    protected BodyConstructorIterator iterator = null;

    public BodyConstructor(TranslatorContext ctx, boolean newScope) {
        this.ctx = ctx;
        this.newScope = newScope;
        ctx.activeBodyConstructors.push(this);
        if (newScope) ctx.enterNewScope();
        scope = ctx.visibilityScope.scope();
    }

    public boolean isAlive() {
        return ctx.activeBodyConstructors.contains(this);
    }

    public int nestingCount() {
        return ctx.activeBodyConstructors.size();
    }

    public int totalNodeCount() {
        return nodes.size();
    }

    public int currentNodeIndex() {
        return iterator == null ? totalNodeCount() : iterator.index + 1;
    }

    public boolean has(Node node) {
        return nodes.contains(node);
    }

    public BodyConstructor add(Node node) {
        nodes.add(node);
        setNodeHook(node);
        return this;
    }

    public BodyConstructor insert(int index, Node node) {
        nodes.add(index, node);
        afterInsert(index);
        setNodeHook(node);
        return this;
    }

    public BodyConstructor insertBeforeLast(int offset, Node node) {
        if (offset == 0) {
            add(node);
        }
        return insert(currentNodeIndex() + offset, node);
    }

    public BodyConstructor substitute(int index, Node node) {
        nodes.set(index, node);
        setNodeHook(node);
        return this;
    }

    public BodyConstructor remove(int index) {
        nodes.remove(index);
        afterRemove(index);
        return this;
    }

    protected void afterInsert(int index) {
    }

    protected void afterRemove(int index) {
    }

    private void postprocess() {
        ctx.activeBodyConstructors.remove(this);
        if (newScope) {
            ctx.leaveScope();
        }
    }

    public static BodyConstructor createFrom(TranslatorContext ctx, boolean newScope, List<Node> nodes) {
        var result = new BodyConstructor(ctx, newScope);
        result.nodes = new ArrayList<>(nodes);
        return result;
    }

    private void setNodeHook(Node node) {
        if (node instanceof ClassDefinition def) {
            for (Node clsComponent : def.getBody().getNodes()) {
                if (clsComponent instanceof FieldDeclaration field) {
                    field.setParentDeclaration(def.getDeclaration());
                } else if (clsComponent instanceof MethodDefinition method) {
                    method.getDeclaration().setParentDeclaration(def.getDeclaration());
                }
            }
            ctx.visibilityScope.scope().registerDefinition(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def);
        } else if (node instanceof FunctionDefinition def) {
            ctx.visibilityScope.scope().registerDefinition(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def);
        } else if (node instanceof VariableDeclaration varDecl) {
            ctx.visibilityScope.scope().registerVariable(varDecl);
        } else if (node instanceof SeparatedVariableDeclaration sepDecl) {
            ctx.visibilityScope.scope().registerVariable(sepDecl);
        } else if (node instanceof EnumDeclaration decl) {
            ctx.visibilityScope.scope().registerDeclaration(decl.getName().getSimpleIdentifierOrThrow(), decl);
        } else if (node instanceof Import imprt) {
            ctx.visibilityScope.scope().registerImport(imprt);
        }
        ctx.processInfer(node);
    }

    public CompoundStatement build() {
        postprocess();
        return new CompoundStatement(nodes);
    }

    public void setScopeOwner(Node owner) {
        if (newScope) scope.setOwner(owner);
    }

    public List<Node> getNodes() {
        postprocess();
        return List.copyOf(nodes);
    }

    class BodyConstructorIterator implements Iterator<Node> {
        private final BodyConstructor parent;
        protected int index;

        public BodyConstructorIterator(BodyConstructor parent) {
            this.parent = parent;
            index = -1;
        }

        @Override
        public boolean hasNext() {
            return index + 1 < parent.nodes.size();
        }

        @Override
        public Node next() {
            index++;
            Node value = parent.nodes.get(index);
            parent.setNodeHook(value);
            return value;
        }

        @Override
        public void remove() {
            parent.nodes.remove(index);
            index--;
        }
    }

    @Override
    public @NotNull Iterator<Node> iterator() {
        iterator = new BodyConstructorIterator(this);
        return iterator;
    }
}
