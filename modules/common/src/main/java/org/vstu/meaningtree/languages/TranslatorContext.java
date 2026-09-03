package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.NotNull;
import org.treesitter.TSNode;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.utils.analysis.types.SimpleTypeInferrer;
import org.vstu.meaningtree.utils.frames.Frame;
import org.vstu.meaningtree.utils.frames.FrameStack;
import org.vstu.meaningtree.utils.modules.ImportBuffer;
import org.vstu.meaningtree.utils.scopes.AssignmentBinding;
import org.vstu.meaningtree.utils.scopes.ScopePolicy;
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
    private final ImportBuffer imports = new ImportBuffer();
    private final Set<Long> rejectedNodeIds = new HashSet<>();
    private final Set<Long> ignoredNodeIds = new HashSet<>();

    /**
     * Открыл ли кадр собственную область видимости — по кадру на элемент, вершина отвечает
     * текущему кадру. Держится отдельно от {@link FrameStack}, потому что это факт об
     * отрисовке, а не о кадре: у разбора области открывает {@link BodyConstructor}.
     */
    private final Deque<Boolean> scopePerFrame = new ArrayDeque<>();

    TranslatorContext(TranslatorComponent component, LanguageTranslator translator) {
        this.owner = component;
        this.translator = translator;
        this.scope = new ScopeTable();
        this.scope.setAssignmentBinding(component.assignmentBinding());
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
        pushRenderScope(node);
    }

    void enterSource(TSNode source, Class<? extends Node> declaredType) {
        frameControl.enterSource(source, declaredType);
        // У разбора области открывает BodyConstructor: на спуске узла ещё нет, и спросить
        // политику не о чем. Кадр всё равно отмечаем, чтобы стеки не разъехались.
        scopePerFrame.push(false);
    }

    void completeFrame(Node produced) {
        frameControl.complete(produced);
    }

    void leaveFrame() {
        if (!scopePerFrame.isEmpty() && scopePerFrame.pop()) {
            scope.leave();
        }
        frameControl.leave();
    }

    /**
     * Открывает область видимости на кадре узла, если язык считает этот узел её границей.
     * <p>
     * Область привязана к кадру, а не к {@link StringBodyConstructor}, потому что кадр
     * гарантированно закрывается: {@link LanguageViewer#renderPrepared} снимает его в
     * {@code finally}. Конструктор тела такой гарантии не даёт — рендереры расходуют его
     * по-разному, и часть из них (например, {@code toStringCompoundStatement} в Java и C++)
     * забирает результат через {@code stringBuffer()}, ни разу не вызвав {@code getNodes()}.
     * Область, привязанная к нему, осталась бы незакрытой, и вложенность росла бы до конца
     * отрисовки.
     * <p>
     * Границу задаёт {@link ScopePolicy} языка — та же, по которой строит таблицу
     * {@code ScopeTableBuilder}. Поэтому таблица отрисовки получается той же формы, что и
     * построенная проходом по дереву, а не одной плоской областью, как раньше.
     * <p>
     * Владелец области здесь не проставляется: {@code setOwner} перепривязал бы
     * {@code CompoundStatement} дерева к области таблицы отрисовки, стерев привязку,
     * оставленную разбором.
     */
    private void pushRenderScope(Node node) {
        boolean opens = node instanceof CompoundStatement body
                && translator.getScopePolicy().opensScope(body, frames.at(1).flatMap(Frame::node).orElse(null));
        scopePerFrame.push(opens);
        if (opens) {
            scope.enter();
        }
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

    /** Тело без собственной области видимости; область открывает {@link #createNodeBody(boolean)}. */
    public BodyConstructor createNodeBody() {
        return createNodeBody(false);
    }

    /**
     * Помечает узел как отклонённый: если он окажется кандидатом на добавление в
     * {@link BodyConstructor} (через add/insert/substitute), тот его не добавит и не
     * зарегистрирует. Разовая пометка — снимается при первой проверке.
     * @param node отклоняемый узел
     * @return тот же узел
     */
    public Node rejectNode(Node node) {
        rejectedNodeIds.add(node.getId());
        return node;
    }

    /**
     * Помечает узел как игнорируемый для таблиц видимости: при регистрации узла в
     * {@link BodyConstructor} он не попадёт в {@link ScopeTable}, но сам узел всё равно
     * будет добавлен в тело/вывод. Разовая пометка — снимается при первой проверке.
     * @param node игнорируемый узел
     * @return тот же узел
     */
    public Node ignoreNode(Node node) {
        ignoredNodeIds.add(node.getId());
        return node;
    }

    boolean consumeRejection(Node node) {
        return rejectedNodeIds.remove(node.getId());
    }

    boolean consumeIgnore(Node node) {
        return ignoredNodeIds.remove(node.getId());
    }

    /**
     * Импорты, отложенные до отрисовки шапки программы. Собственного состояния у контекста
     * здесь нет — он только владеет буфером на время одной трансляции.
     */
    public ImportBuffer imports() {
        return imports;
    }

    public BodyConstructor createNodeBody(boolean newScope) {
        return new BodyConstructor(this, newScope);
    }

    /**
     * Заносит узел в текущую область видимости по его виду.
     * <p>
     * Единственное место, где узел превращается в запись таблицы. Им пользуются и обычное
     * наполнение тела через {@link BodyConstructor}, и замена уже добавленного узла
     * ({@link #substituteNode}): разойдись эти два пути, таблица после замены описывала бы
     * узел не так, как при первичной регистрации.
     * <p>
     * Сама диспетчеризация «что означает появление узла в области» живёт в
     * {@link ScopeTable#register(Node)} и общая со сборкой таблицы отдельным проходом по
     * готовому дереву ({@code org.vstu.meaningtree.utils.analysis.ScopeTableBuilder}), поэтому
     * два пути наполнения не могут разойтись. Здесь остаётся только выбор области: у разбора
     * это текущая область стека, у прохода — область, в которую он вошёл сам.
     */
    void registerInScope(@NotNull Node node) {
        scope.register(node);
    }

    /**
     * Убирает из текущей области видимости запись, созданную {@link #registerInScope}.
     * <p>
     * Нужна при замене узла: без неё в таблице остаётся объявление, которого в дереве больше
     * нет. Переменные не удаляются по имени вслепую — удаляется ровно то объявление, которое
     * регистрировалось, иначе замена одного члена стёрла бы одноимённый из внешней области.
     */
    private void unregisterFromScope(@NotNull Node node) {
        if (node instanceof ClassDefinition def) {
            scope.removeDeclaration(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def.getDeclaration());
        } else if (node instanceof FunctionDefinition def) {
            scope.removeDeclaration(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def.getDeclaration());
        } else if (node instanceof EnumDeclaration decl) {
            scope.removeDeclaration(decl.getName().getSimpleIdentifierOrThrow(), decl);
        }
    }

    /**
     * Заменяет узел в уже построенном теле, поддерживая таблицу областей видимости в
     * согласии с деревом.
     * <p>
     * Парсеры дорабатывают тело класса после того, как оно собрано: Python превращает функции
     * в методы, Java — {@code finalize} в деструктор. Прямой {@code CompoundStatement.substitute}
     * меняет только дерево, и в таблице остаётся исходное объявление: узел, которого в дереве
     * уже нет, с другим числом параметров и без владельца.
     *
     * @param body        тело, в котором заменяется узел; должно быть связано с областью
     *                    видимости, иначе таблицу обновлять негде
     * @param index       позиция заменяемого узла
     * @param replacement узел, который встаёт на его место
     */
    public void substituteNode(@NotNull CompoundStatement body, int index, @NotNull Node replacement) {
        Node previous = body.getNodes()[index];
        body.substitute(index, replacement);

        OptionalLong scopeId = body.getScopeId();
        if (scopeId.isEmpty() || scope.findScope(scopeId.getAsLong()).isEmpty()) {
            return;
        }

        scope.runInScope(scopeId.getAsLong(), () -> {
            unregisterFromScope(previous);
            registerInScope(replacement);
        });
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
