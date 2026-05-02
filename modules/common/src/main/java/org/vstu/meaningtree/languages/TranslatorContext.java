package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.scopes.ScopeTable;
import org.vstu.meaningtree.utils.scopes.SimpleTypeInferrer;

import java.lang.reflect.Method;
import java.util.*;

public class TranslatorContext {
    protected TranslatorComponent owner;
    protected LanguageTranslator translator;
    protected LanguageTokenizer tokenizer = null;

    protected ScopeTable globalScope;
    protected ScopeTable visibilityScope;

    protected Deque<BodyConstructor> activeBodyConstructors = new ArrayDeque<>();
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
        return activeBodyConstructors.getFirst();
    }

    public StringBodyConstructor getNearestUnfilledViewerBody() {
        if (!(owner instanceof LanguageViewer)) {
            throw new IllegalStateException("This method is applicable only for language viewers");
        }
        return (StringBodyConstructor) activeBodyConstructors.getFirst();
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
        return Optional.ofNullable((T) value);
    }

    public boolean check(String name, Object value) {
        var ctxVar = get(name, value.getClass());
        return ctxVar.map(o -> o.equals(value)).orElse(false);
    }

    public void set(String name, Object value) {
        ctxVariables.put(name, value);
    }

    public void remove(String name) {
        ctxVariables.remove(name);
    }

    public Optional<Type> lookupRegisteredType(Identifier typeName) {
        return lookupRegisteredType(typeName, false);
    }

    /***
     * Ищет зарегистрированный тип данных (чаще всего, UserType) по идентификатору
     * @param typeName идентификатор типа
     * @param useGlobalScope искать ли в глобальной области
     * @return найденный (или нет) тип
     */
    public Optional<Type> lookupRegisteredType(Identifier typeName, boolean useGlobalScope) {
        if (useGlobalScope) {
            return globalScope.scope().findType(typeName);
        } else {
            return visibilityScope.scope().findType(typeName);
        }
    }

    public Optional<VariableDeclaration> lookupVariable(String variableName) {
        return lookupVariable(variableName, null);
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

    public List<Class<? extends Node>> getTranslatingNodeTypeHierarchy() {
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
                                if (Node.class.isAssignableFrom(returnType) && !returnType.equals(Node.class)) {
                                    translatingNodes.add(returnType.asSubclass(Node.class));
                                }
                            } else if (LanguageViewer.class.isAssignableFrom(cls)) {
                                Class<?>[] params = m.getParameterTypes();
                                if (params.length > 0 && Node.class.isAssignableFrom(params[0]) && !params[0].equals(Node.class)) {
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

    // Проверяет, что текущая функция парсера/viewer обрабатывается внутри заданного типа узла
    public boolean isInNode(Class<? extends Node> nodeType) {
        var h = getTranslatingNodeTypeHierarchy();
        return h.stream().anyMatch(x -> nodeType.isAssignableFrom(x));
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

    public StringBodyConstructor viewingIterateBody(CompoundStatement compoundStatement) {
        return viewingIterateBody(compoundStatement.getNodeList());
    }

    public StringBodyConstructor viewingIterateBody(ProgramEntryPoint entryPoint) {
        return viewingIterateBody(entryPoint.getBody());
    }

    public StringBodyConstructor viewingIterateBody(List<Node> entryPoint) {
        return StringBodyConstructor.createFrom(this, false, entryPoint);
    }

    public BodyConstructor startWalkCompoundStatement(CompoundStatement compoundStatement, boolean newScope) {
        var res = new BodyConstructor(this, newScope);
        res.nodes = new ArrayList<>(List.of(compoundStatement.getNodes()));
        return res;
    }

}
