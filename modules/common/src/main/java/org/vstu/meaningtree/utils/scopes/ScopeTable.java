package org.vstu.meaningtree.utils.scopes;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.modules.Import;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ScopeDeclarationStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;

import java.io.Serializable;
import java.util.*;
import java.util.function.Supplier;

/**
 * {@code TypeScope} управляет стеком областей видимости типов,
 * поддерживая операции входа и выхода из областей {@link ScopeTableElement}.
 */
public class ScopeTable implements Serializable {
    /**
     * Program-wide declarations and definitions.
     */
    @NotNull
    private final SymbolIndex symbols;

    /**
     * Program-wide type registry and user type hierarchy.
     */
    @NotNull
    private final TypeIndex types;

    /**
     * Program-wide imports.
     */
    @NotNull
    private final ImportIndex imports;

    @NotNull
    private final Map<Long, ScopeTableElement> scopes;

    /**
     * Члены пользовательских типов: владелец — имя — все одноимённые декларации.
     * <p>
     * Отдельно от лексических областей видимости потому, что метод виден не по вложенности
     * блоков, а по владельцу: два класса в одной области могут объявлять одноимённые методы,
     * и это не перегрузка друг друга.
     */
    @NotNull
    private final Map<UserType, Map<SimpleIdentifier, List<FunctionDeclaration>>> members;

    /**
     * Обратный индекс «декларация — её группа».
     * <p>
     * Ключи сравниваются по ссылке: {@code Node.equals} сравнивает по значению и без id,
     * поэтому две одинаковые по значению декларации разных классов схлопнулись бы в один ключ.
     */
    @NotNull
    private final Map<FunctionDeclaration, OverloadGroup> groupByDeclaration;

    private long nextScopeId;

    /**
     * Правило языка, по которому присваивание выбирает переменную. Свойство программы, а не
     * области: одна таблица описывает один разбор, и разные его части не могут следовать
     * разным правилам.
     */
    @NotNull
    private AssignmentBinding assignmentBinding = AssignmentBinding.ENCLOSING;

    /**
     * Текущая область сущностей.
     */
    @NotNull
    private ScopeTableElement current;

    /**
     * Создаёт менеджер областей видимости с корневой областью.
     */
    public ScopeTable() {
        this.symbols = new SymbolIndex();
        this.types = new TypeIndex();
        this.imports = new ImportIndex();
        this.scopes = new LinkedHashMap<>();
        this.members = new LinkedHashMap<>();
        this.groupByDeclaration = new IdentityHashMap<>();
        this.nextScopeId = 1;
        this.current = createScope(null, null);
    }

    /**
     * Входит в новую область видимости.
     */
    public void enter() {
        enter(null);
    }

    /**
     * Входит в новую область видимости, связанную с AST-узлом.
     */
    public void enter(@Nullable Node owner) {
        current = createScope(current, owner);
    }

    /**
     * Входит в родительскую область.
     * Если родительской области нет, ничего не происходит.
     *
     * @throws IllegalStateException если текущая область корневая
     */
    public void leave() {
        leave(false);
    }

    /**
     * Входит в родительскую область.
     *
     * @param rootScopeMustExist Когда {@code true}, выбрасывает исключение
     *                           {@code IllegalStateException}, если родительской
     *                           области видимости не существует
     */
    public void leave(boolean rootScopeMustExist) {
        ScopeTableElement parent = current.getParent();
        if (parent != null) {
            current = parent;
            return;
        }

        if (rootScopeMustExist) {
            throw new IllegalStateException("Cannot leave root scope");
        }
    }

    @NotNull
    public AssignmentBinding getAssignmentBinding() {
        return assignmentBinding;
    }

    public void setAssignmentBinding(@NotNull AssignmentBinding assignmentBinding) {
        this.assignmentBinding = assignmentBinding;
    }

    public ScopeTableElement scope() {
        return current;
    }

    public Collection<ScopeTableElement> allScopes() {
        return List.copyOf(scopes.values());
    }

    public Optional<ScopeTableElement> findScope(long id) {
        return Optional.ofNullable(scopes.get(id));
    }

    public long currentScopeId() {
        return current.getId();
    }

    public ScopeTableElement restoreScope(long id, @Nullable Long parentId, @Nullable Node owner) {
        ScopeTableElement existing = scopes.get(id);
        if (existing != null) {
            existing.setOwner(owner);
            return existing;
        }

        ScopeTableElement parent = parentId == null ? null : scopes.get(parentId);
        ScopeTableElement restored = new ScopeTableElement(id, parent, owner);
        scopes.put(id, restored);
        nextScopeId = Math.max(nextScopeId, id + 1);
        return restored;
    }

    public void setCurrentScope(long id) {
        ScopeTableElement scope = scopes.get(id);
        if (scope == null) {
            throw new IllegalArgumentException("Unknown scope id: " + id);
        }
        current = scope;
    }

    /**
     * Выполняет действие так, как если бы текущей была область {@code scopeId}, и возвращает
     * курсор на место.
     * <p>
     * Курсор — общее состояние таблицы, поэтому каждый, кто его двигал, обязан был вернуть на
     * место сам: пять проходов анализа держали один и тот же {@code try/finally} вокруг пары
     * {@link #setCurrentScope}. Достаточно забыть {@code finally} в одном из них — и все
     * следующие поиски пойдут не из той области, причём молча.
     * <p>
     * Неизвестная область — не ошибка, а штатный случай: дерево могло прийти из десериализации
     * или быть собрано в обход разбора, и тогда у тела нет привязанной области. Тогда действие
     * выполняется в текущей области — ровно то, что делали все вызывающие руками.
     *
     * @param scopeId область, в которой нужно выполнить действие; {@code null} или неизвестный
     *                идентификатор означает «остаться в текущей»
     */
    public <T> T inScope(@Nullable Long scopeId, @NotNull Supplier<T> action) {
        long previousScopeId = current.getId();
        boolean switched = scopeId != null && scopes.containsKey(scopeId);
        if (switched) {
            setCurrentScope(scopeId);
        }
        try {
            return action.get();
        } finally {
            // Область могла исчезнуть, пока действие выполнялось: возвращаться некуда, и
            // падать на выходе из finally нельзя — оно затёрло бы исходное исключение.
            if (switched && scopes.containsKey(previousScopeId)) {
                setCurrentScope(previousScopeId);
            }
        }
    }

    /** {@link #inScope} для действия без результата. */
    public void runInScope(@Nullable Long scopeId, @NotNull Runnable action) {
        inScope(scopeId, () -> {
            action.run();
            return null;
        });
    }

    /**
     * {@link #inScope} для узла: действие выполняется в области того тела, внутри которого узел
     * лежит по дереву.
     * <p>
     * Пара «найти ближайшую область по цепочке родителей, затем выполнить в ней» была дословно
     * повторена в каждом проходе анализа, которому нужно разрешать имена там, где они написаны,
     * а не там, где их застал обход. Копий было столько же, сколько проходов, и разойтись они
     * могли молча: обход, забывший подняться до тела, ищет имя не в той области и находит чужое.
     */
    public <T> T inScopeOf(@Nullable NodeInfo info, @NotNull Supplier<T> action) {
        return inScope(nearestScopeId(info), action);
    }

    /**
     * Область ближайшего объемлющего тела, включая сам узел, если он и есть тело.
     *
     * @return {@code null}, если ни одно тело по цепочке родителей не привязано к области —
     * штатный случай для дерева, собранного в обход разбора или пришедшего из десериализации
     */
    @Nullable
    public static Long nearestScopeId(@Nullable NodeInfo info) {
        for (NodeInfo current = info; current != null; current = current.parent()) {
            if (current.node() instanceof CompoundStatement compound && compound.getScopeId().isPresent()) {
                return compound.getScopeId().getAsLong();
            }
        }
        return null;
    }

    public void setCurrentScopeOwner(@Nullable Node owner) {
        current.setOwner(owner);
    }

    /**
     * Регистрирует узел в текущей области видимости так, как того требует его тип.
     * <p>
     * Единственное место, где решается «что именно означает появление этого узла в области»:
     * этим методом пользуются и попутное наполнение при разборе
     * ({@code TranslatorContext.registerInScope}), и отдельный проход по готовому дереву
     * ({@code ScopeTableBuilder}). Раньше это была пара дословных копий, которые полагались на
     * ручную синхронизацию.
     * <p>
     * Метод не чист: у членов {@link ClassDefinition} он проставляет владельца
     * ({@code setParentDeclaration}), потому что без обратной ссылки член неотличим от свободной
     * функции, а больше эту связь взять неоткуда. Побочный эффект идемпотентен — повторный вызов
     * на том же дереве присваивает то же значение, — поэтому повторное построение таблицы дерево
     * не портит.
     */
    public void register(@NotNull Node node) {
        if (node instanceof ClassDefinition def) {
            for (Node clsComponent : def.getBody().getNodes()) {
                if (clsComponent instanceof FieldDeclaration field) {
                    field.setParentDeclaration(def.getDeclaration());
                } else if (clsComponent instanceof MethodDeclaration method) {
                    method.setParentDeclaration(def.getDeclaration());
                } else if (clsComponent instanceof MethodDefinition method) {
                    method.getDeclaration().setParentDeclaration(def.getDeclaration());
                }
            }
            registerDefinition(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def);
        } else if (node instanceof FunctionDefinition def) {
            registerDefinition(def.getDeclaration().getName().getSimpleIdentifierOrThrow(), def);
        } else if (node instanceof VariableDeclaration varDecl) {
            registerVariable(varDecl);
        } else if (node instanceof SeparatedVariableDeclaration sepDecl) {
            registerVariable(sepDecl);
        } else if (node instanceof EnumDeclaration decl) {
            registerDeclaration(decl.getName().getSimpleIdentifierOrThrow(), decl);
        } else if (node instanceof Import imprt) {
            registerImport(imprt);
        } else if (node instanceof CatchClause clause && clause.hasName()) {
            registerCatchVariable(Objects.requireNonNull(clause.getName()), clause.getExceptionTypes());
        } else if (node instanceof ScopeDeclarationStatement declaration) {
            registerScopeDeclaration(declaration);
        }
    }

    /**
     * Записывает привязки, объявленные {@code global} / {@code nonlocal}, в текущую область.
     * <p>
     * Живёт здесь, а не в разборе, потому что тот же оператор встречается и в готовом дереве:
     * без общей точки {@code ScopeTableBuilder} восстановил бы таблицу без привязок, и
     * перестроенная таблица разошлась бы с собранной при разборе.
     */
    public void registerScopeDeclaration(@NotNull ScopeDeclarationStatement declaration) {
        for (SimpleIdentifier name : declaration.getNames()) {
            ScopeTableElement target = switch (declaration.getKind()) {
                case GLOBAL -> scopes.get(rootScopeId());
                case NONLOCAL -> nonlocalTarget(name);
            };
            // Цель, совпавшая с текущей областью, — не ошибка, а вырожденный случай:
            // `global x` в самом модуле и `nonlocal` без объемлющей функции ничего не
            // перенаправляют, потому что перенаправлять некуда.
            if (target != null && target != current) {
                current.rebindName(name, target);
            }
        }
    }

    /**
     * Область, которую связывает {@code nonlocal}: ближайшая объемлющая функция.
     * <p>
     * Области классов пропускаются — тело класса своих имён вложенным функциям не отдаёт, и
     * {@code nonlocal} в них не смотрит. Корневая тоже: имя модуля связывает {@code global},
     * а {@code nonlocal} до него не доходит. Область, где имя уже объявлено, предпочтительнее
     * ближайшей: между объявлением и присваиванием могут лежать вложенные функции.
     * <p>
     * Отсутствие подходящей области в Python — {@code SyntaxError}, но разбор принимает и
     * фрагменты кода, вырванные из своего окружения, поэтому вместо отказа возвращается
     * ближайшая подходящая область либо {@code null}, если её нет вовсе.
     */
    @Nullable
    private ScopeTableElement nonlocalTarget(@NotNull SimpleIdentifier name) {
        ScopeTableElement nearest = null;
        for (ScopeTableElement scope = current.getParent(); scope != null; scope = scope.getParent()) {
            if (scope.getParent() == null || scope.belongsToClass().isPresent()) {
                continue;
            }
            if (scope.hasVariable(name)) {
                return scope;
            }
            if (nearest == null) {
                nearest = scope;
            }
        }
        return nearest;
    }

    /**
     * Переменная исключения видна как обычная локальная, но объявления-узла у неё нет: в модели
     * это имя на {@link CatchClause}, а не {@code VariableDeclaration}. Поэтому регистрируется
     * только тип и только в текущей области — {@code changeVariableType} здесь не подходит, он
     * поднялся бы к одноимённой внешней переменной и перезаписал её тип, хотя переменная
     * исключения её затеняет, а не меняет.
     * <p>
     * Тип известен, только когда ветвь перехватывает ровно один тип: у multi-catch и
     * python-кортежа статический тип — это надтип перечисленных, вычислить который модель пока
     * не умеет.
     */
    public void registerCatchVariable(@NotNull SimpleIdentifier name, @NotNull List<Type> exceptionTypes) {
        Type type = exceptionTypes.size() == 1 ? exceptionTypes.getFirst() : new UnknownType();
        current.restoreVariable(name, type, null);
    }

    public void registerVariable(@NotNull VariableDeclaration variableDeclaration) {
        current.registerVariable(variableDeclaration);
    }

    public void registerVariable(@NotNull SeparatedVariableDeclaration variableDeclaration) {
        current.registerVariable(variableDeclaration);
    }

    public void removeVariable(@NotNull SimpleIdentifier name) {
        current.removeVariable(name);
    }

    public boolean hasVariable(@NotNull SimpleIdentifier name) {
        return current.getVariableType(name) != null;
    }

    @Nullable
    public Type getVariableType(@NotNull SimpleIdentifier name) {
        return current.getVariableType(name);
    }

    public Optional<VariableDeclaration> getVariableDeclaration(@NotNull SimpleIdentifier name, @Nullable Type type) {
        return current.getVariableDeclaration(name, type);
    }

    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type, boolean createIfNotExists) {
        current.changeVariableType(name, type, createIfNotExists, assignmentBinding);
    }

    /**
     * Тип переменной, которую свяжет присваивание этому имени в текущей области, по правилу
     * {@link #getAssignmentBinding()}. Отсутствие типа означает, что присваивание вводит новое
     * имя, а не меняет существующее.
     */
    @Nullable
    public Type getAssignmentTargetType(@NotNull SimpleIdentifier name) {
        return current.getAssignmentTargetType(name, assignmentBinding);
    }

    /** Объявлено ли имя в текущей области связанным снаружи ({@code global} / {@code nonlocal}). */
    public boolean isRebound(@NotNull SimpleIdentifier name) {
        return current.resolveBinding(name) != current;
    }

    public void changeVariableType(@NotNull SimpleIdentifier name, @NotNull Type type) {
        changeVariableType(name, type, true);
    }

    public void registerImport(@NotNull Import importDeclaration) {
        imports.registerImport(importDeclaration);
    }

    public void registerTypeDeclaration(@NotNull Type type, @NotNull Declaration declaration) {
        types.registerTypeDeclaration(type, declaration);
    }

    public Identifier registerType(@NotNull Identifier name, @NotNull Type type) {
        if (current.getParent() != null) {
            return current.registerType(name, type);
        }
        return types.registerType(name, type);
    }

    /**
     * Тип, который объявление вводит в программу. Есть только у объявлений пользовательских
     * типов: классов, структур и перечислений.
     */
    @Nullable
    static Type declaredTypeOf(@NotNull Declaration declaration) {
        return switch (declaration) {
            case ClassDeclaration cls -> cls.getTypeNode();
            case EnumDeclaration enumDeclaration -> enumDeclaration.getTypeNode();
            default -> null;
        };
    }

    public void registerDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        if (current.getParent() == null) {
            symbols.registerDeclaration(name, declaration);
            Type type = declaredTypeOf(declaration);
            if (type != null) {
                registerTypeDeclaration(type, declaration);
                registerType(name, type);
            }
        } else {
            current.registerDeclaration(name, declaration);
            // Иерархия предков — структурный факт о фрагменте, а не о видимости имён, поэтому
            // регистрируется независимо от глубины scope: вложенный класс тоже должен попасть
            // в TypeHierarchy, хотя его объявление остаётся видимым только локально.
            Type type = declaredTypeOf(declaration);
            if (type != null) {
                types.registerHierarchy(type, declaration);
            }
        }
    }

    /**
     * Убирает объявление из текущей области видимости. Маршрут тот же, что у
     * {@link #registerDeclaration}: корневая область — глобальный индекс, вложенная — локальный.
     *
     * @return {@code true}, если объявление было найдено и удалено
     */
    public boolean removeDeclaration(@NotNull SimpleIdentifier name, @NotNull Declaration declaration) {
        return current.getParent() == null
                ? symbols.removeDeclaration(name, declaration)
                : current.removeDeclaration(name, declaration);
    }

    public void registerDefinition(@NotNull SimpleIdentifier name, @NotNull Definition definition) {
        registerDeclaration(name, definition.getDeclaration());
        symbols.registerDefinition(definition.getDeclaration(), definition);
    }

    public void registerDefinition(@NotNull Declaration declaration, @NotNull Definition definition) {
        symbols.registerDefinition(declaration, definition);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name, @Nullable Class<? extends Declaration> clazz) {
        return findDeclaration(name, clazz, ScopeLookupMode.VISIBLE);
    }

    public Optional<Declaration> findDeclaration(@NotNull SimpleIdentifier name,
                                                 @Nullable Class<? extends Declaration> clazz,
                                                 @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclaration(name, clazz);
            case GLOBAL -> symbols.findDeclaration(name, clazz);
            case VISIBLE -> current.findDeclaration(name, clazz)
                    .or(() -> symbols.findDeclaration(name, clazz));
        };
    }

    /**
     * Все одноимённые декларации: перегрузки одного имени в ближайшей области видимости,
     * где это имя объявлено. Пустой список означает, что имя не найдено.
     *
     * @see #findDeclaration(SimpleIdentifier, Class, ScopeLookupMode) одиночный вариант,
     *      возвращающий последнюю подходящую декларацию
     */
    public List<Declaration> findDeclarations(@NotNull SimpleIdentifier name,
                                              @Nullable Class<? extends Declaration> clazz) {
        return findDeclarations(name, clazz, ScopeLookupMode.VISIBLE);
    }

    public List<Declaration> findDeclarations(@NotNull SimpleIdentifier name,
                                              @Nullable Class<? extends Declaration> clazz,
                                              @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclarations(name, clazz);
            case GLOBAL -> symbols.findDeclarations(name, clazz);
            case VISIBLE -> {
                var local = current.findDeclarations(name, clazz);
                yield local.isEmpty() ? symbols.findDeclarations(name, clazz) : local;
            }
        };
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz) {
        return findDeclaration(clazz, ScopeLookupMode.VISIBLE);
    }

    public List<Declaration> findDeclaration(@NotNull Class<? extends Declaration> clazz,
                                             @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentDeclaration(clazz);
            case GLOBAL -> symbols.findDeclaration(clazz);
            case VISIBLE -> {
                var localDeclarations = current.findDeclaration(clazz);
                yield localDeclarations.isEmpty() ? symbols.findDeclaration(clazz) : localDeclarations;
            }
        };
    }

    public Optional<Definition> findDefinition(@NotNull SimpleIdentifier name, @Nullable Class<? extends Declaration> declarationClass) {
        return findDefinition(name, declarationClass, ScopeLookupMode.VISIBLE);
    }

    public Optional<Definition> findDefinition(@NotNull SimpleIdentifier name,
                                               @Nullable Class<? extends Declaration> declarationClass,
                                               @NotNull ScopeLookupMode mode) {
        return findDeclaration(name, declarationClass, mode).flatMap(this::findDefinition);
    }

    public Optional<Definition> findDefinition(@NotNull Declaration declaration) {
        return symbols.findDefinition(declaration);
    }

    public List<Definition> findDefinition(@NotNull Class<? extends Definition> clazz) {
        return symbols.findDefinition(clazz);
    }

    public Optional<Type> findType(@NotNull Identifier name) {
        return findType(name, ScopeLookupMode.VISIBLE);
    }

    public Optional<Type> findType(@NotNull Identifier name, @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentType(name);
            case GLOBAL -> types.findType(name);
            case VISIBLE -> current.findType(name).or(() -> types.findType(name));
        };
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type) {
        return findTypeDeclaration(type, ScopeLookupMode.VISIBLE);
    }

    public Optional<Declaration> findTypeDeclaration(@NotNull Type type, @NotNull ScopeLookupMode mode) {
        return switch (mode) {
            case CURRENT -> current.findCurrentTypeDeclaration(type);
            case GLOBAL -> types.findTypeDeclaration(type);
            case VISIBLE -> current.findTypeDeclaration(type).or(() -> types.findTypeDeclaration(type));
        };
    }

    public TypeHierarchy typeHierarchy() {
        return types.hierarchy();
    }

    public Map<UserType, Set<UserType>> userTypeHierarchy() {
        return typeHierarchy().asMap();
    }

    public Set<UserType> directParents(@NotNull UserType type) {
        return typeHierarchy().directParents(type);
    }

    public Set<UserType> ancestors(@NotNull UserType type) {
        return typeHierarchy().ancestors(type);
    }

    public Set<UserType> descendants(@NotNull UserType type) {
        return typeHierarchy().descendants(type);
    }

    public boolean isSubtypeOf(@NotNull UserType possibleSubtype, @NotNull UserType possibleParent) {
        return typeHierarchy().isSubtypeOf(possibleSubtype, possibleParent);
    }

    public Set<UserType> userTypes() {
        return typeHierarchy().userTypes();
    }

    /**
     * Идентификатор корневой области видимости. Она создаётся в конструкторе и существует
     * всегда, поэтому это безопасная точка отсчёта для деклараций, у которых не нашлось
     * охватывающего блока.
     */
    public long rootScopeId() {
        return scopes.keySet().iterator().next();
    }

    /**
     * Сбрасывает всё, что построено по дереву проходами анализа: членов типов и группы
     * перегрузок.
     * <p>
     * Нужен перед повторной индексацией. Без него второй проход по тому же дереву дописывает
     * членов и группы к уже имеющимся, и таблица начинает описывать программу, которой нет:
     * один метод числится дважды, а группа перегрузок — вчетверо больше своего размера.
     * Лексическое содержимое областей (переменные, типы, импорты) сюда не входит — оно
     * построено разбором, а не анализом.
     */
    public void clearAnalysisIndexes() {
        members.clear();
        groupByDeclaration.clear();
        scopes.values().forEach(ScopeTableElement::clearOverloadGroups);
    }

    /**
     * Регистрирует член пользовательского типа: метод, конструктор или иную callable-сущность,
     * принадлежащую владельцу.
     */
    public void registerMember(@NotNull UserType owner, @NotNull FunctionDeclaration member) {
        members.computeIfAbsent(owner, key -> new LinkedHashMap<>())
                .computeIfAbsent(member.getName(), key -> new ArrayList<>())
                .add(member);
    }

    /** Все одноимённые члены владельца в порядке регистрации. */
    public List<FunctionDeclaration> findMembers(@NotNull UserType owner, @NotNull SimpleIdentifier name) {
        return List.copyOf(members.getOrDefault(owner, Map.of()).getOrDefault(name, List.of()));
    }

    /** Все члены владельца в порядке регистрации. */
    public List<FunctionDeclaration> findMembers(@NotNull UserType owner) {
        return members.getOrDefault(owner, Map.of()).values().stream()
                .flatMap(List::stream)
                .toList();
    }

    /**
     * Создаёт и запоминает группу перегрузок. Единственный способ получить
     * {@link OverloadGroup}: таблица остаётся единственным владельцем групп, поэтому обратный
     * индекс не может разойтись с их списком.
     */
    public OverloadGroup registerOverloadGroup(long scopeId,
                                               @NotNull SimpleIdentifier name,
                                               @NotNull OverloadKind kind,
                                               @Nullable UserType owner,
                                               @NotNull List<FunctionDeclaration> declarations) {
        OverloadGroup group = new OverloadGroup(scopeId, name, kind, owner, declarations);
        // Группа принадлежит своей области видимости, а не таблице: только так поиск может идти
        // вверх по родителям и учитывать затенение. Область, которой уже нет, не должна ронять
        // индексацию, поэтому осиротевшая группа уходит в корень.
        ScopeTableElement home = scopes.getOrDefault(scopeId, scopes.get(rootScopeId()));
        home.registerOverloadGroup(group);
        for (FunctionDeclaration declaration : group.declarations()) {
            groupByDeclaration.put(declaration, group);
        }
        return group;
    }

    /**
     * Группа, в которую входит декларация.
     * <p>
     * Пусто означает, что группы не строились вовсе — например, при
     * {@code skipOptimizations}, когда конвейер анализа не выполняется, — а не что декларация
     * не перегружена: неперегруженное имя тоже образует группу из одного элемента.
     */
    public Optional<OverloadGroup> findOverloadGroup(@NotNull FunctionDeclaration declaration) {
        return Optional.ofNullable(groupByDeclaration.get(declaration));
    }

    /** Все построенные группы перегрузок, собранные по всем областям видимости. */
    public List<OverloadGroup> overloadGroups() {
        return scopes.values().stream().flatMap(scope -> scope.overloadGroups().stream()).toList();
    }

    /** Группы, в которых имя действительно перегружено, то есть несёт больше одной сигнатуры. */
    public List<OverloadGroup> overloadedGroups() {
        return overloadGroups().stream().filter(OverloadGroup::isOverloaded).toList();
    }

    /**
     * Ближайшая видимая из текущей области группа свободных функций с этим именем.
     * <p>
     * Это и есть отбор кандидатов по видимости: вложенная декларация затеняет внешнюю, а
     * декларация из соседней недоступной области кандидатом не становится вовсе.
     */
    public Optional<OverloadGroup> findVisibleFunctionGroup(@NotNull SimpleIdentifier name) {
        return current.findVisibleFunctionGroup(name);
    }

    /** Глобальные декларации: имя — все одноимённые декларации в порядке регистрации. */
    public Map<Identifier, List<Declaration>> allDeclarations() {
        return symbols.allDeclarations();
    }

    public Map<Declaration, Definition> allDefinitions() {
        return symbols.allDefinitions();
    }

    public Map<Identifier, Type> allTypes() {
        return types.allTypes();
    }

    public Map<Type, Declaration> allTypeDeclarations() {
        return types.allTypeDeclarations();
    }

    public Set<Import> allImports() {
        return imports.allImports();
    }

    @Override
    public String toString() {
        return current.toString();
    }

    private ScopeTableElement createScope(@Nullable ScopeTableElement parent, @Nullable Node owner) {
        ScopeTableElement scope = new ScopeTableElement(nextScopeId++, parent, owner);
        scopes.put(scope.getId(), scope);
        if (owner instanceof CompoundStatement compoundStatement) {
            compoundStatement.bindScope(scope);
        }
        return scope;
    }
}
