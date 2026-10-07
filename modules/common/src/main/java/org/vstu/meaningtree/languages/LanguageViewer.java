package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.helpers.ContextualNodeRenderer;
import org.vstu.meaningtree.languages.helpers.NodeRenderer;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.FeatureSupport;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.configs.ConfigParameters;
import org.vstu.meaningtree.languages.support.SupportIssue;
import org.vstu.meaningtree.languages.support.SupportReport;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.expressions.identifiers.SelfReference;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SuperClassReference;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.utils.InternalNode;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.ParenthesesFiller;
import org.vstu.meaningtree.utils.hooks.HookHandle;
import org.vstu.meaningtree.utils.hooks.HookOrder;
import org.vstu.meaningtree.utils.hooks.HookPhase;
import org.vstu.meaningtree.utils.modules.ImportPathConverter;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

abstract public class LanguageViewer extends TranslatorComponent {
    @FunctionalInterface
    private interface InternalRenderer {
        String render(Node node, Object context);
    }

    protected MeaningTree origin;
    protected ParenthesesFiller parenFiller;

    private final List<FeatureSupport> supportRules = new ArrayList<>();
    private final List<Class<? extends Node>> explicitUnsupportedNodes = new ArrayList<>();
    private final Map<Class<? extends Node>, InternalRenderer> renderers = new LinkedHashMap<>();

    
    private static final ClassValue<Boolean> INTERNAL_NODE_TYPE_CACHE = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            Class<?> current = type;
            while (current != null && current != Object.class) {
                if (current.isAnnotationPresent(InternalNode.class)) {
                    return true;
                }
                current = current.getSuperclass();
            }
            return false;
        }
    };

    public LanguageViewer(LanguageTranslator translator) {
        super(translator);
        // Токенайзер спрашивается лениво: на момент создания viewer'а он ещё не собран
        this.parenFiller = new ParenthesesFiller(
                this::mapToToken,
                text -> ctx.requireTokenizer().hasOperatorWithText(text)
        );
        registerReservedKeywordGuard();
    }

    /**
     * Точечные имена, которые называет импорт (без обращения к резолверу/файловой системе —
     * то же наивное имя, что видит {@link org.vstu.meaningtree.utils.analysis.imports.ImportResolver}).
     * <p>
     * Общая для всех вьюеров: извлечение имени не зависит от целевого языка, только от формы
     * узла — в отличие от того, что каждый вьюер потом с этим именем делает (своя таблица
     * "своих" модулей у Java/Python, таблица заголовков у C++), это осталось у них.
     */
    /**
     * Убирает из буфера импорты, которым в целевом языке нет соответствия, и решает, законно ли
     * это.
     *
     * @param renderedCode уже напечатанное тело программы; по нему проверяется, не осталось ли в
     *                     выводе ссылок на выбрасываемый импорт
     */
    protected void pruneUnrenderableImports(Predicate<Import> isUnrenderable, String renderedCode) {
        requireDroppableImports(ctx.imports().peek().stream().filter(isUnrenderable).toList(), renderedCode);
        ctx.imports().removeIf(isUnrenderable);
    }

    /**
     * Разрешает выбросить эти импорты или отказывает.
     * <p>
     * Само по себе удаление строки импорта ничего не переводит. Оно безобидно ровно тогда, когда
     * в выводе не осталось ни одной ссылки на импортированное: либо обращений не было вовсе
     * ({@code #include <vector>} без единого вектора), либо все они уже переписаны средствами
     * целевого языка ({@code java.util.ArrayList} напечатан питоновским {@code list}). Если же
     * имя в выводе осталось, программа выглядит целой и не работает — а это ровно то, чего
     * принцип "отказаться, но не соврать" не допускает.
     * <p>
     * Реестр библиотечных импортов на этот вопрос ответить не может: он знает соответствия
     * импортов, а обращения переписывают другие таблицы того же языка. Поэтому решение
     * принимается по напечатанному коду, а не по тому, нашлось ли имя в реестре.
     * <p>
     * Молчаливое удаление остаётся доступным явным включением
     * {@link ConfigParameters#silentlySkipUnknownImports}.
     */
    protected void requireDroppableImports(Collection<Import> unrenderable, String renderedCode) {
        if (unrenderable.isEmpty()
                || translator.getConfigParameter(ConfigParameters.silentlySkipUnknownImports).asBoolean()) {
            return;
        }
        List<String> stillReferenced = unrenderable.stream()
                .filter(this::mayBeNamedInCode)
                .map(this::describeImport)
                .filter(name -> isReferencedIn(renderedCode, name))
                .toList();
        if (stillReferenced.isEmpty()) {
            return;
        }
        throw new UnsupportedViewingException(
                ("Imports have no counterpart in %s, but the generated code still refers to them: %s. "
                        + "Enable silentlySkipUnknownImports to drop them anyway")
                        .formatted(translator.getLanguageName(), stillReferenced));
    }

    /**
     * Может ли имя импорта вообще встретиться в выводе ссылкой на импортированное.
     * <p>
     * Подключение стандартной библиотеки C++ ({@code #include <...>}) именует файл, а не
     * сущность: из {@code <format>} код зовёт {@code std::format}, из {@code <vector>} —
     * {@code std::vector}, и самим именем файла не пользуется никто. Совпадение этого слова с
     * чем-нибудь в выводе целевого языка — совпадение, а не ссылка: Java печатает
     * {@code String.format(...)} своим встроенным средством, и отказ выбросить подключение,
     * которого в Java не бывает вовсе, был ложным.
     * <p>
     * Локальное подключение ({@code #include "utils.h"}) проверяется по-прежнему: его имя —
     * имя файла проекта, по которому обычно назван и его код, и совпадение с именем в выводе
     * там значимо.
     */
    private boolean mayBeNamedInCode(Import importNode) {
        return !(importNode instanceof Include include)
                || include.getIncludeType() != Include.IncludeType.POINTY_BRACKETS_FORM;
    }

    /**
     * Осталась ли в коде ссылка на импортированное имя. Проверяется как последний сегмент
     * точечного имени: именно им обращаются к импортированной сущности, тогда как полный путь
     * в теле обычно не повторяется.
     */
    private boolean isReferencedIn(String renderedCode, String importName) {
        String simpleName = importName.substring(importName.lastIndexOf('.') + 1);
        simpleName = simpleName.substring(simpleName.lastIndexOf('/') + 1);
        int dot = simpleName.indexOf('.');
        if (dot > 0) {
            // имя файла заголовка: <stdio.h> -> stdio
            simpleName = simpleName.substring(0, dot);
        }
        if (simpleName.isEmpty()) {
            return false;
        }
        return Pattern.compile("\\b" + Pattern.quote(simpleName) + "\\b").matcher(renderedCode).find();
    }

    private String describeImport(Import importNode) {
        // Include не несёт точечного имени модуля — у него имя файла заголовка.
        if (importNode instanceof Include include) {
            return include.getFileName().getUnescapedValue();
        }
        List<String> names = moduleNamesOf(importNode);
        return names.isEmpty() ? importNode.getNodeUniqueName() : String.join(", ", names);
    }

    protected List<String> moduleNamesOf(Import importNode) {
        return switch (importNode) {
            case ImportMembersFromModule members -> List.of(ImportPathConverter.dottedName(members.getModuleName()));
            case ImportAllFromModule all -> List.of(ImportPathConverter.dottedName(all.getModuleName()));
            case ImportModules modules -> modules.getModulesNames().stream().map(ImportPathConverter::dottedName).toList();
            case ImportModule module -> List.of(ImportPathConverter.dottedName(module.getModuleName()));
            default -> List.of();
        };
    }

    /**
     * Сахар над {@link HookPhase#BEFORE_NODE_RENDER} для языковых хуков: подготовка узла
     * заданного типа перед рендерингом.
     */
    public final <T extends Node> HookHandle registerPreRenderPreparation(Class<T> nodeType, UnaryOperator<T> preparation) {
        Objects.requireNonNull(preparation, "preparation must not be null");
        return hooks.intercept(HookPhase.BEFORE_NODE_RENDER, nodeType,
                (node, value, context) -> Objects.requireNonNull(
                        preparation.apply(node),
                        "Pre-render preparation returned null for node type " + nodeType.getName()
                ));
    }

    /**
     * Сахар над {@link HookPhase#AFTER_NODE_RENDER} для языковых хуков: доработка строки,
     * полученной из узла заданного типа.
     */
    public final <T extends Node> HookHandle registerPostRenderPreparation(Class<T> nodeType, BiFunction<T, String, String> preparation) {
        return registerPostRenderPreparation(nodeType, HookOrder.NORMAL, preparation);
    }

    /**
     * То же, но с явным порядком. Инструментирование вывода должно регистрироваться с
     * {@link HookOrder#LATE}, иначе его разметка окажется внутри результата работы других
     * хуков.
     */
    public final <T extends Node> HookHandle registerPostRenderPreparation(Class<T> nodeType, HookOrder order,
                                                                          BiFunction<T, String, String> preparation) {
        Objects.requireNonNull(preparation, "preparation must not be null");
        return hooks.intercept(HookPhase.AFTER_NODE_RENDER, nodeType, order,
                (node, rendered, context) -> Objects.requireNonNull(
                        preparation.apply(node, rendered),
                        "Post-render preparation returned null for node type " + nodeType.getName()
                ));
    }

    protected final Node applyPreRenderPreparations(Node node) {
        Objects.requireNonNull(node, "node must not be null");
        return hooks.run(HookPhase.BEFORE_NODE_RENDER, node, node);
    }

    protected final <T extends Node> void registerRenderer(Class<T> nodeType, NodeRenderer<T> renderer) {
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        Objects.requireNonNull(renderer, "renderer must not be null");
        renderers.put(nodeType, (node, context) -> renderer.render(nodeType.cast(node)));
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Node, C> void registerRenderer(Class<T> nodeType, ContextualNodeRenderer<T, C> renderer) {
        Objects.requireNonNull(nodeType, "nodeType must not be null");
        Objects.requireNonNull(renderer, "renderer must not be null");
        renderers.put(nodeType, (node, context) -> renderer.render(nodeType.cast(node), (C) context));
    }

    public final boolean hasRegisteredRenderer(Class<? extends Node> nodeType) {
        return resolveRenderer(nodeType).isPresent();
    }

    public final Set<Class<? extends Node>> getRegisteredNodeTypes() {
        return Set.copyOf(renderers.keySet());
    }

    private Optional<InternalRenderer> resolveRenderer(Class<? extends Node> nodeType) {
        int bestDistance = Integer.MAX_VALUE;
        InternalRenderer bestRenderer = null;
        for (Map.Entry<Class<? extends Node>, InternalRenderer> entry : renderers.entrySet()) {
            Class<? extends Node> registeredType = entry.getKey();
            if (!registeredType.isAssignableFrom(nodeType)) {
                continue;
            }
            int distance = typeDistance(nodeType, registeredType);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestRenderer = entry.getValue();
            }
        }
        if (bestRenderer == null) {
            return Optional.empty();
        }
        return Optional.of(bestRenderer);
    }

    private static int typeDistance(Class<?> source, Class<?> target) {
        int distance = 0;
        Class<?> current = source;
        while (current != null && !current.equals(target)) {
            current = current.getSuperclass();
            distance++;
        }
        return current == null ? Integer.MAX_VALUE : distance;
    }

    protected String dispatchRenderer(Node node) {
        return dispatchRenderer(node, null);
    }

    protected String dispatchRenderer(Node node, Object context) {
        Optional<InternalRenderer> renderer = resolveRenderer(node.getClass());
        if (renderer.isEmpty()) {
            throw new UnsupportedViewingException("No renderer registered for node type " + node.getClass().getName());
        }
        return renderer.get().render(node, context);
    }

    protected String applyHooks(Node node, String result) {
        if (node == null) {
            return result;
        }
        return hooks.run(HookPhase.AFTER_NODE_RENDER, node, result);
    }

    protected List<SupportIssue> checkNodeSupport(Node node) {
        return checkNodeSupport(node, null);
    }

    protected void registerUnsupportedFeature(FeatureSupport feature) {
        supportRules.add(feature);
    }

    protected void registerUnsupportedFeature(Class<? extends Node> feature) {
        /**
         * Учтите, что этим методом обычно вносятся вспомогательные узлы, которые транслятор по умолчанию считает поддерживаемыми, но они вдруг не поддерживаются у вас
         * Полиморфные проверки не поддерживаются
         */
        explicitUnsupportedNodes.add(feature);
    }

    protected List<SupportIssue> checkNodeSupport(Node node, FeatureContext context) {
        List<SupportIssue> issues = new ArrayList<>();
        boolean isExplicitlyForbidden = explicitUnsupportedNodes.contains(node.getClass());
        if (!hasRegisteredRenderer(node.getClass()) && context.checkNodeIsRegistered() || isExplicitlyForbidden) {
            if (isInternalNodeTypeOrSuperclass(node.getClass()) && !isExplicitlyForbidden) {
                return issues;
            }
            issues.add(new SupportIssue(
                    translator.getLanguageName(),
                    node, null
            ));
            return issues;
        }
        for (FeatureSupport feature : supportRules) {
            if (!feature.matches(node, context)) {
                continue;
            }
            issues.add(new SupportIssue(
                    translator.getLanguageName(),
                    node,
                    feature
            ));
        }
        return issues;
    }

    private boolean isInternalNodeTypeOrSuperclass(Class<? extends Node> nodeType) {
        return INTERNAL_NODE_TYPE_CACHE.get(nodeType);
    }

    public SupportReport analyzeSupport(Node node) {
        return analyzeSupport(new MeaningTree(node), true);
    }

    public SupportReport analyzeSupport(MeaningTree tree, boolean includeNodeRegisterCheck) {
        List<SupportIssue> issues = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info == null || info.node() == null) {
                continue;
            }
            FeatureContext context = new FeatureContext(this, tree, info, info.node(), includeNodeRegisterCheck);
            issues.addAll(checkNodeSupport(info.node(), context));
        }
        return new SupportReport(issues);
    }

    public SupportReport analyzeSupport(MeaningTree tree) {
        return analyzeSupport(tree, true);
    }

    /**
     * Единственная точка диспетчеризации рендеринга — через неё проходит каждый узел.
     * Поэтому именно здесь ведётся стек кадров ({@link TranslatorContext#callFrames()}):
     * {@code try/finally} гарантирует, что контекст не протечёт при исключении.
     * <p>
     * В кадр кладётся {@code preparedNode} (после {@link HookPhase#BEFORE_NODE_RENDER}), а не
     * исходный узел: иначе после подмены узла подготовкой идентичность разъедется и
     * {@link TranslatorContext#depthOf(Node)} начнёт врать. По типу разницы нет.
     */
    public final String toString(Node node) {
        Objects.requireNonNull(node);
        Node preparedNode = applyPreRenderPreparations(node);
        if (preparedNode.hasLabel(Label.DUMMY)) {
            return "";
        }
        return renderPrepared(preparedNode, null);
    }

    /**
     * Рендеринг уже подготовленного узла с ведением стека кадров.
     * <p>
     * Через этот метод обязан проходить <b>каждый</b> путь диспетчеризации: у языка их может
     * быть больше одного (например, {@code PythonViewer} диспетчеризует с контекстом-отступом),
     * и путь в обход стека делает контекст разбора неполным — узел не увидят ни
     * {@link TranslatorContext#isInNode}, ни {@link TranslatorContext#getEnclosingNode}.
     *
     * @param preparedNode узел <b>после</b> {@link HookPhase#BEFORE_NODE_RENDER}: именно он
     *                     кладётся в кадр, иначе после подмены узла подготовкой идентичность
     *                     разъедется и {@link TranslatorContext#depthOf(Node)} начнёт врать
     * @param context      контекст рендерера или {@code null}
     */
    protected final String renderPrepared(Node preparedNode, Object context) {
        Objects.requireNonNull(preparedNode, "preparedNode must not be null");
        ctx.enterNode(preparedNode);
        try {
            return applyHooks(preparedNode, dispatchRenderer(preparedNode, context));
        } finally {
            ctx.leaveFrame();
        }
    }

    public abstract OperatorToken mapToToken(Expression expr);

    /**
     * Подготовка дерева перед рендерингом. Переопределение — простой путь для языка;
     * внешний потребитель может добиться того же, зарегистрировав перехватчик на
     * {@link HookPhase#BEFORE_TREE_RENDER}.
     */
    protected MeaningTree preprocessTree(MeaningTree tree) {
        return tree;
    }

    public String toString(MeaningTree mt) {
        MeaningTree tree = hooks.run(HookPhase.BEFORE_TREE_RENDER, mt, preprocessTree(mt));
        origin = tree;
        analyzeSupport(tree, false).throwAll();
        String result = toString(tree.getRootNode());
        return hooks.run(HookPhase.AFTER_TREE_RENDER, tree, result);
    }

    /**
     * Запрещает использовать идентификаторы, совпадающие с ключевыми словами целевого
     * языка.
     * <p>
     * Оформлено хуком, а не проверкой внутри {@link #toString(Node)}, чтобы политику можно
     * было заменить: например, вместо отказа переименовывать идентификатор в допустимый.
     * Регистрируется с {@link HookOrder#LATE}, чтобы проверять узел уже после всех
     * подготовок.
     */
    private void registerReservedKeywordGuard() {
        hooks.intercept(HookPhase.BEFORE_NODE_RENDER, SimpleIdentifier.class, HookOrder.LATE,
                (identifier, value, context) -> {
                    if (identifier instanceof SelfReference || identifier instanceof SuperClassReference) {
                        return value;
                    }
                    if (!ctx.requireTokenizer().isReservedKeyword(identifier.getName())) {
                        return value;
                    }
                    throw new UnsupportedViewingException(
                            "Identifier `%s` is a reserved keyword in %s"
                                    .formatted(identifier.getName(), translator.getLanguageName())
                    );
                });
    }

}

