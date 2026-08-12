package org.vstu.meaningtree.languages;

import org.treesitter.TSNode;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.analysis.types.SimpleTypeInferrer;
import org.vstu.meaningtree.utils.frames.Frame;
import org.vstu.meaningtree.utils.frames.FrameStack;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.nio.file.Path;
import java.util.*;

public class TranslatorContext {
    protected TranslatorComponent owner;
    protected LanguageTranslator translator;
    protected LanguageTokenizer tokenizer = null;

    /**
     * Таблица областей видимости текущей трансляции.
     * <p>
     * Сама {@link ScopeTable} держит стек областей внутри себя ({@link ScopeTable#enter()} /
     * {@link ScopeTable#leave()}), поэтому отдельного «глобального» экземпляра здесь не нужно:
     * корневая область — это область таблицы до первого {@code enter()}.
     */
    protected ScopeTable scope;

    /**
     * Контекст разбора: стек узлов, внутри которых ядро находится прямо сейчас.
     * Ведут его {@link LanguageViewer#toString(Node)} и {@link LanguageParser#parseTSNode},
     * снаружи он только читается — прикладные вопросы через методы этого класса,
     * низкоуровневые — через {@link #callFrames()}.
     */
    private final FrameStack.Control frameControl = FrameStack.createControlled();
    private final FrameStack frames = frameControl.stack();

    protected Deque<BodyConstructor> activeBodyConstructors = new ArrayDeque<>();
    private Map<String, Object> ctxVariables = new HashMap<>();
    private List<Import> imports = new ArrayList<>();

    TranslatorContext(TranslatorComponent component, LanguageTranslator translator) {
        this.owner = component;
        this.translator = translator;
        this.scope = new ScopeTable();
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
        return SimpleTypeInferrer.inference(expression, scope);
    }

    /**
     * Выполнить выведение типа для узла. Может установить тип в узлах в качестве побочного эффекта
     * @param node данный узел
     */
    public void processInfer(Node node) {
        SimpleTypeInferrer.inference(node, scope);
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

    /***
     * Ищет зарегистрированный тип данных (чаще всего, UserType) по идентификатору
     * @param typeName идентификатор типа
     * @return найденный (или нет) тип
     */
    public Optional<Type> lookupRegisteredType(Identifier typeName) {
        return scope.findType(typeName);
    }

    public Optional<VariableDeclaration> lookupVariable(String variableName) {
        return lookupVariable(variableName, null);
    }

    public Optional<VariableDeclaration> lookupVariable(String variableName, Type varType) {
       return scope.getVariableDeclaration(new SimpleIdentifier(variableName), varType);
    }

    public Optional<Definition> lookupDefinition(String definitionName) {
        return lookupDefinition(definitionName, null);
    }

    public Optional<Definition> lookupDefinition(String definitionName, Class<? extends Declaration> type) {
        return scope.findDefinition(new SimpleIdentifier(definitionName), type);
    }

    public Optional<Declaration> lookupDeclaration(String declarationName) {
        return scope.findDeclaration(new SimpleIdentifier(declarationName), null);
    }

    public Optional<Declaration> lookupDeclaration(Type type) {
        return scope.findTypeDeclaration(type);
    }

    public Optional<Declaration> lookupDeclaration(String declarationName, Class<? extends Declaration> type) {
        return scope.findDeclaration(new SimpleIdentifier(declarationName), type);
    }

    /**
     * Проверяет, что текущий узел обрабатывается где-то внутри узла заданного типа.
     * Текущий узел считается: {@code isInNode(t)} истинно и когда обрабатывается сам {@code t}.
     */
    public boolean isInNode(Class<? extends Node> nodeType) {
        return frames.depthOf(nodeType) > 0;
    }

    /**
     * То же, но ровно на заданном уровне: {@code 0} — текущий обрабатываемый узел,
     * {@code 1} — на уровень выше.
     */
    public boolean isInNode(Class<? extends Node> nodeType, int level) {
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        return frames.at(level)
                .map(frame -> nodeType.isAssignableFrom(frame.nodeType()))
                .orElse(false);
    }

    /** Ровно на уровень выше текущего узла. Эквивалентно {@code isInNode(nodeType, 1)}. */
    public boolean isDirectlyInNode(Class<? extends Node> nodeType) {
        return isInNode(nodeType, 1);
    }

    /**
     * Глубина вложенности в узлы заданного типа, считая текущий: ноль — мы не внутри такого узла.
     */
    public int depthOf(Class<? extends Node> nodeType) {
        return frames.depthOf(nodeType);
    }

    /**
     * Глубина вложенности в конкретный узел (сравнение по ссылке) — признак повторного входа.
     * <p>
     * Только для viewer'ов: в парсере узла на спуске не существует, поэтому ответ был бы
     * не «не внутри», а «вопрос здесь не имеет смысла» — а это разные вещи.
     */
    public int depthOf(Node node) {
        requireViewerOwner("depthOf(Node)");
        return frames.depthOf(node);
    }

    /** Типы обрабатываемых узлов от текущего наружу; {@code get(0)} — тип текущего узла. */
    public List<Class<? extends Node>> nodeTypeHierarchy() {
        return frames.nodeTypeHierarchy();
    }

    /** Тип узла на уровень выше текущего. */
    public Optional<Class<? extends Node>> parentType() {
        return parentFrame().map(Frame::nodeType);
    }

    /** Кадр на уровень выше текущего. Эквивалентно {@code callFrame(1)}. */
    public Optional<Frame> parentFrame() {
        return callFrame(1);
    }

    /** Кадр на заданном уровне: {@code 0} — текущий, {@code 1} — на уровень выше. */
    public Optional<Frame> callFrame(int level) {
        return frames.at(level);
    }

    /**
     * Вход на второй ярус — стек кадров целиком. Нужен там, где первого яруса не хватает:
     * поиск с выдачей кадра, {@link org.treesitter.TSNode} предка, реентерабельность.
     */
    public FrameStack callFrames() {
        return frames;
    }

    /**
     * Ближайший объемлющий узел заданного типа — сам объект, а не тип и не кадр. Поиск идёт
     * наружу и считает текущий узел.
     * <p>
     * <b>Только для viewer'ов.</b> В парсере на спуске узла предка не существует (кадр несёт
     * {@link org.treesitter.TSNode} и заявленный тип), поэтому здесь бросается исключение:
     * «нет такого предка» и «этот вопрос здесь не имеет смысла» — разные ответы, и смешивать
     * их нельзя. Низкоуровневый {@code callFrames().nearestFrame(type)} остаётся общим для
     * обоих компонентов.
     */
    public <T extends Node> Optional<T> getEnclosingNode(Class<T> type) {
        requireViewerOwner("getEnclosingNode");
        Objects.requireNonNull(type, "type must not be null");
        return frames.nearestFrame(type)
                .flatMap(Frame::node)
                .map(type::cast);
    }

    private void requireViewerOwner(String methodName) {
        if (!(owner instanceof LanguageViewer)) {
            throw new IllegalStateException(
                    "Method %s is applicable only for language viewers".formatted(methodName));
        }
    }

    // --- Двигать стек кадров может только ядро ---

    void enterNode(Node node) {
        frameControl.enterNode(node);
    }

    void enterSource(TSNode source, Class<? extends Node> declaredType) {
        frameControl.enterSource(source, declaredType);
    }

    void completeFrame(Node produced) {
        frameControl.complete(produced);
    }

    void leaveFrame() {
        frameControl.leave();
    }

    public ScopeTable getScopeTable() {
        return scope;
    }

    public Optional<Path> getProjectRootPath() {
        return translator.getProjectRootPath();
    }

    public Optional<Path> getCurrentFileRelPath() {
        return translator.getCurrentFileRelPath();
    }

    public boolean hasSourceContext() {
        return translator.hasSourceContext();
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
        scope.enter();
    }

    public void leaveScope() {
        scope.leave();
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
