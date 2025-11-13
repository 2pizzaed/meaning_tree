package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.EnumDeclaration;
import org.vstu.meaningtree.nodes.declarations.SeparatedVariableDeclaration;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.scopes.ScopeTable;
import org.vstu.meaningtree.utils.scopes.ScopeTableElement;
import org.vstu.meaningtree.utils.scopes.SimpleTypeInferrer;

import java.lang.reflect.Method;
import java.util.*;

public class TranslatorContext {
    private TranslatorComponent owner;
    private LanguageTranslator translator;
    private LanguageTokenizer tokenizer = null;

    private ScopeTable globalScope;
    private ScopeTable visibilityScope;

    private Deque<BodyConstructor> activeBodyConstructors = new ArrayDeque<>();
    private Map<String, Object> ctxVariables = new HashMap<>();
    private List<Import> imports = new ArrayList<>();

    TranslatorContext(TranslatorComponent component, LanguageTranslator translator) {
        this.owner = component;
        this.translator = translator;
        this.globalScope = new ScopeTable();
        this.visibilityScope = globalScope;
    }

    public LanguageTokenizer requireTokenizer() {
        if (tokenizer == null) {
            this.tokenizer = translator.getTokenizer();
        }
        return tokenizer;
    }

    public BodyConstructor getNearestUnfilledBody() {
        return activeBodyConstructors.getLast();
    }

    /**
     * Вывести тип из выражения и вернуть его
     * @param expression данное выражение
     * @return выведенный тип
     */
    public Type inferType(Expression expression) {
        return SimpleTypeInferrer.inference(expression, visibilityScope);
    }

    /**
     * Выполнить выведение типа для узла. Может установить тип в узлах в качестве побочного эффекта
     * @param node данный узел
     */
    public void processInfer(Node node) {
        SimpleTypeInferrer.inference(node, visibilityScope);
    }

    boolean isBodyFinished() {
        return activeBodyConstructors.isEmpty();
    }

    public Optional<Boolean> getFlag(String flag) {
        return get(flag, Boolean.class);
    }

    public <T> Optional<T> get(String name, Class<T> type) {
        var value = ctxVariables.getOrDefault(name, null);
        if (value != null && !value.getClass().isAssignableFrom(type)) {
            return Optional.empty();
        }
        return Optional.of((T) value);
    }

    public void set(String name, Object value) {
        ctxVariables.put(name, value);
    }

    public void remove(String name) {
        ctxVariables.remove(name);
    }

    public Optional<Type> lookupType(Identifier typeName) {
        return lookupType(typeName, false);
    }

    public Optional<Type> lookupType(Identifier typeName, boolean useGlobalScope) {
        if (useGlobalScope) {
            return globalScope.scope().findType(typeName);
        } else {
            return visibilityScope.scope().findType(typeName);
        }
    }

    public Optional<VariableDeclaration> lookupVariable(String variableName) {
        return lookupVariable(variableName);
    }

    public Optional<VariableDeclaration> lookupVariable(String variableName, Type varType) {
       return visibilityScope.scope().getVariableDeclaration(new SimpleIdentifier(variableName), varType);
    }

    public Optional<Definition> lookupDefinition(String definitionName) {
        return lookupDefinition(definitionName, null);
    }

    public Optional<Definition> lookupDefinition(String definitionName, Class<? extends Declaration> type) {
        return visibilityScope.scope().findDefinition(new SimpleIdentifier(definitionName), type);
    }

    public Optional<Declaration> lookupDeclaration(String declarationName) {
        return visibilityScope.scope().findDeclaration(new SimpleIdentifier(declarationName), null);
    }

    public Optional<Declaration> lookupDeclaration(Type type) {
        return visibilityScope.scope().findTypeDeclaration(type);
    }

    public Optional<Declaration> lookupDeclaration(String declarationName, Class<? extends Declaration> type) {
        return visibilityScope.scope().findDeclaration(new SimpleIdentifier(declarationName), type);
    }

    public List<Class<? extends Node>> getTranslatingNodeHierarchy() {
        List<Class<? extends Node>> translatingNodes = new ArrayList<>();

        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stack) {
            try {
                Class<?> cls = Class.forName(element.getClassName());

                if (TranslatorComponent.class.isAssignableFrom(cls)) {
                    String methodName = element.getMethodName();

                    // Находим метод (без параметров или с любыми)
                    Method[] methods = cls.getDeclaredMethods();
                    for (Method m : methods) {
                        if (m.getName().equals(methodName)) {
                            if (LanguageParser.class.isAssignableFrom(cls)) {
                                Class<?> returnType = m.getReturnType();
                                if (Node.class.isAssignableFrom(returnType)) {
                                    translatingNodes.add(returnType.asSubclass(Node.class));
                                }
                            } else if (LanguageViewer.class.isAssignableFrom(cls)) {
                                Class<?>[] params = m.getParameterTypes();
                                if (params.length > 0 && Node.class.isAssignableFrom(params[0])) {
                                    translatingNodes.add(params[0].asSubclass(Node.class));
                                }
                            }
                            break;
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // Игнорируем системные фреймы
            }
        }
        return translatingNodes;
    }

    public ScopeTable getGlobalScope() {
        return globalScope;
    }

    public ScopeTable getVisibilityScope() {
        return visibilityScope;
    }

    public BodyConstructor createNodeBody() {
        return new BodyConstructor(this, getFlag("scopeForEachCompound").orElse(false));
    }

    public void preserveImport(Import importNode) {
        imports.add(importNode);
    }

    public List<Import> flushImports() {
        var dump = List.copyOf(imports);
        imports.clear();
        return dump;
    }

    public BodyConstructor createNodeBody(boolean newScope) {
        return new BodyConstructor(this, newScope);
    }

    public void enterNewScope() {
        visibilityScope.enter();
    }

    public void leaveScope() {
        visibilityScope.leave();
    }

    public BodyConstructor iterateBody(CompoundStatement compoundStatement) {
        return iterateBody(compoundStatement.getNodeList());
    }

    public BodyConstructor iterateBody(ProgramEntryPoint entryPoint) {
        return iterateBody(entryPoint.getBody());
    }

    public BodyConstructor iterateBody(List<Node> entryPoint) {
        return BodyConstructor.createFrom(this, false, entryPoint);
    }

    public BodyConstructor startWalkCompoundStatement(CompoundStatement compoundStatement, boolean newScope) {
        var res = new BodyConstructor(this, newScope);
        res.nodes = new ArrayList<>(List.of(compoundStatement.getNodes()));
        return res;
    }

    public static class BodyConstructor implements Iterable<Node> {
        private TranslatorContext ctx;
        private boolean newScope;
        private ScopeTableElement scope;
        private List<Node> nodes = new ArrayList<>();

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

        public int count() {
            return ctx.activeBodyConstructors.size();
        }

        public void add(Node node) {
            nodes.add(node);
            setNodeHook(node);
        }

        public void insert(int index, Node node) {
            nodes.add(index, node);
            setNodeHook(node);
        }

        public void substitute(int index, Node node) {
            nodes.set(index, node);
            setNodeHook(node);
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
            ctx.processInfer(node);
            if (node instanceof ClassDefinition def) {
                ctx.visibilityScope.scope().registerDefinition(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def);
            } else if (node instanceof FunctionDefinition def) {
                ctx.visibilityScope.scope().registerDefinition(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def);
            } else if (node instanceof MethodDefinition def) {
                ctx.visibilityScope.scope().registerDefinition(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def);
            } else if (node instanceof VariableDeclaration varDecl) {
                ctx.visibilityScope.scope().registerVariable(varDecl);
            } else if (node instanceof SeparatedVariableDeclaration sepDecl) {
                ctx.visibilityScope.scope().registerVariable(sepDecl);
            } if (node instanceof EnumDeclaration decl) {
                ctx.visibilityScope.scope().registerDeclaration(decl.getName().getSimpleIdentifierOrThrow(), decl);
            }
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
            private final Iterator<Node> base;
            private final BodyConstructor parent;

            public BodyConstructorIterator(Iterator<Node> base, BodyConstructor parent) {
                this.base = base;
                this.parent = parent;
            }

            @Override
            public boolean hasNext() {
                return base.hasNext();
            }

            @Override
            public Node next() {
                Node value = base.next();
                parent.setNodeHook(value);
                return value;
            }

            @Override
            public void remove() {
                base.remove();
            }
        }

        @Override
        public @NotNull Iterator<Node> iterator() {
            return new BodyConstructorIterator(nodes.iterator(), this);
        }
    }
}
