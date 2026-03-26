package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.helpers.ContextualNodeRenderer;
import org.vstu.meaningtree.languages.helpers.HookUtils;
import org.vstu.meaningtree.languages.helpers.NodeRenderer;
import org.vstu.meaningtree.languages.helpers.TemplateAwareViewer;
import org.vstu.meaningtree.languages.helpers.templates.ClasspathTemplateRepository;
import org.vstu.meaningtree.languages.helpers.templates.JinjavaTemplateEngine;
import org.vstu.meaningtree.languages.helpers.templates.TemplateEngine;
import org.vstu.meaningtree.languages.helpers.templates.TemplateRepository;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.support.FeatureSupport;
import org.vstu.meaningtree.languages.support.SupportIssue;
import org.vstu.meaningtree.languages.support.SupportReport;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.InternalNode;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.ParenthesesFiller;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

abstract public class LanguageViewer extends TranslatorComponent implements TemplateAwareViewer {
    @FunctionalInterface
    private interface InternalRenderer {
        String render(Node node, Object context);
    }

    protected MeaningTree origin;
    protected ParenthesesFiller parenFiller;

    private final List<HookUtils.NodePreparationEntry<? extends Node>> preRenderPreparations = new ArrayList<>();
    private final List<HookUtils.PostRenderPreparationEntry<? extends Node>> postRenderPreparations = new ArrayList<>();

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

    private TemplateRepository templateRepository;
    private TemplateEngine templateEngine;

    public LanguageViewer(LanguageTranslator translator) {
        super(translator);
        this.parenFiller = new ParenthesesFiller(this::mapToToken);
    }

    protected final <T extends Node> void registerPreRenderPreparation(Class<T> nodeType, UnaryOperator<T> preparation) {
        preRenderPreparations.add(new HookUtils.NodePreparationEntry<>(nodeType, preparation));
    }

    protected final <T extends Node> void registerPostRenderPreparation(Class<T> nodeType, BiFunction<T, String, String> preparation) {
        postRenderPreparations.add(new HookUtils.PostRenderPreparationEntry<>(nodeType, preparation));
    }

    protected final Node applyPreRenderPreparations(Node node) {
        Objects.requireNonNull(node, "node must not be null");
        Node preparedNode = node;
        for (HookUtils.NodePreparationEntry<? extends Node> preparation : preRenderPreparations) {
            if (!preparation.matches(preparedNode)) {
                continue;
            }
            preparedNode = Objects.requireNonNull(
                    preparation.apply(preparedNode),
                    "Pre-render preparation returned null for node type " + preparedNode.getClass().getName()
            );
        }
        return preparedNode;
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
        for (HookUtils.PostRenderPreparationEntry<? extends Node> preparation : postRenderPreparations) {
            if (!preparation.matches(node)) {
                continue;
            }
            result = Objects.requireNonNull(
                    preparation.apply(node, result),
                    "Post-render preparation returned null for node type " + node.getClass().getName()
            );
        }
        return result;
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
            if (!feature.matches(node)) {
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

    public final String toString(Node node) {
        Objects.requireNonNull(node);
        Node preparedNode = applyPreRenderPreparations(node);
        if (preparedNode.hasLabel(Label.DUMMY)) {
            return "";
        }
        String result = dispatchRenderer(preparedNode);
        return applyHooks(preparedNode, result);
    }

    public abstract OperatorToken mapToToken(Expression expr);

    public String toString(MeaningTree mt) {
        origin = mt;
        analyzeSupport(mt, false).throwAll();
        return toString(mt.getRootNode());
    }

    public void configureTemplateEngine(TemplateRepository templateRepository, TemplateEngine templateEngine) {
        this.templateRepository = templateRepository;
        this.templateEngine = templateEngine;
    }

    public void configureJinjaTemplateEngine(String classpathBasePath) {
        configureTemplateEngine(new ClasspathTemplateRepository(classpathBasePath), new JinjavaTemplateEngine());
    }

    public boolean isTemplateEngineConfigured() {
        return templateRepository != null && templateEngine != null;
    }

    public String renderTemplate(String templateKey, Map<String, Object> model) {
        if (!isTemplateEngineConfigured()) {
            throw new IllegalStateException("Template engine is not configured");
        }
        Map<String, Object> renderModel = prepareRenderModel(model, null);
        return templateEngine.render(templateRepository.getTemplateSource(templateKey), renderModel);
    }

    public String renderTemplate(String templateKey, Node node, Map<String, Object> model) {
        Map<String, Object> renderModel = prepareRenderModel(model, node);
        String result = renderTemplate(templateKey, renderModel);
        return applyHooks(node, result);
    }

    private Map<String, Object> prepareRenderModel(Map<String, Object> model, Node node) {
        Map<String, Object> renderModel = new HashMap<>();
        if (model != null) {
            renderModel.putAll(model);
        }
        renderModel.put("viewer", this);
        if (node != null) {
            renderModel.put("node", node);
        }
        renderModel.putIfAbsent("render", new TemplateRenderHelper(this, renderModel));
        return renderModel;
    }

    private record TemplateRenderHelper(LanguageViewer viewer, Map<String, Object> baseModel) {
            private TemplateRenderHelper(LanguageViewer viewer, Map<String, Object> baseModel) {
                this.viewer = Objects.requireNonNull(viewer);
                this.baseModel = Map.copyOf(baseModel);
            }

            public String node(String templateKey, Node node) {
                return viewer.renderTemplate(templateKey, node, baseModel);
            }

            public String template(String templateKey) {
                return viewer.renderTemplate(templateKey, baseModel);
            }
    }
}

