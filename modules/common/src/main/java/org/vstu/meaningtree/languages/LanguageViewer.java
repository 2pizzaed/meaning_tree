package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.iterators.utils.NodeInfo;
import org.vstu.meaningtree.languages.helpers.TemplateAwareViewer;
import org.vstu.meaningtree.languages.helpers.templates.ClasspathTemplateRepository;
import org.vstu.meaningtree.languages.helpers.templates.JinjavaTemplateEngine;
import org.vstu.meaningtree.languages.helpers.templates.TemplateEngine;
import org.vstu.meaningtree.languages.helpers.templates.TemplateRepository;
import org.vstu.meaningtree.languages.support.FeatureContext;
import org.vstu.meaningtree.languages.helpers.NodeRenderer;
import org.vstu.meaningtree.languages.support.SupportIssue;
import org.vstu.meaningtree.languages.support.SupportReport;
import org.vstu.meaningtree.languages.support.FeatureSupport;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.ParenthesesFiller;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.*;
import java.util.function.BiFunction;

/**
 * ПРЕДУПРЕЖДЕНИЕ: Не используйте в реализациях языков перегрузку toString для добавления отображения каждого узла
 * Это сломает многие фичи, т.к. toString(Node node) не только перенаправляет запросы к специальному методу,
 * но и добавляет логику хуков!
 */
abstract public class LanguageViewer extends TranslatorComponent implements TemplateAwareViewer {
    protected MeaningTree origin;
    protected ParenthesesFiller parenFiller;

    private List<BiFunction<Node, String, String>> postProcessFunctions = new ArrayList<>();
    private final List<FeatureSupport> supportRules = new ArrayList<>();
    private final Map<Class<? extends Node>, NodeRenderer<? extends Node>> renderers = new LinkedHashMap<>();

    private TemplateRepository templateRepository;
    private TemplateEngine templateEngine;

    public LanguageViewer(LanguageTranslator translator) {
        super(translator);
        this.parenFiller = new ParenthesesFiller(this::mapToToken);
    }

    public boolean registerPostprocessFunction(BiFunction<Node, String, String> function) {
        return this.postProcessFunctions.add(function);
    }

    public boolean removePostprocessFunction(BiFunction<Node, String, String> function) {
        return this.postProcessFunctions.remove(function);
    }

    protected abstract String formString(Node node);

    protected final <T extends Node> void registerRenderer(Class<T> nodeType, NodeRenderer<T> renderer) {
        renderers.put(Objects.requireNonNull(nodeType, "nodeType must not be null"),
                Objects.requireNonNull(renderer, "renderer must not be null"));
    }

    public final boolean hasRegisteredRenderer(Class<? extends Node> nodeType) {
        return resolveRenderer(nodeType).isPresent();
    }

    public final Set<Class<? extends Node>> getRegisteredNodeTypes() {
        return Set.copyOf(renderers.keySet());
    }

    protected final Optional<NodeRenderer<Node>> resolveRenderer(Class<? extends Node> nodeType) {
        int bestDistance = Integer.MAX_VALUE;
        NodeRenderer<? extends Node> bestRenderer = null;
        for (Map.Entry<Class<? extends Node>, NodeRenderer<? extends Node>> entry : renderers.entrySet()) {
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
        return Optional.of((NodeRenderer<Node>) bestRenderer);
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
        Optional<NodeRenderer<Node>> renderer = resolveRenderer(node.getClass());
        if (renderer.isEmpty()) {
            throw new UnsupportedViewingException("No renderer registered for node type " + node.getClass().getName());
        }
        return renderer.get().render(node);
    }

    protected String applyHooks(Node node, String result) {
        for (var function : postProcessFunctions) {
            result = function.apply(node, result);
        }
        return result;
    }

    protected List<SupportIssue> checkNodeSupport(Node node) {
        return checkNodeSupport(node, null);
    }

    protected void registerUnsupportedFeature(FeatureSupport feature) {
        supportRules.add(feature);
    }

    protected List<SupportIssue> checkNodeSupport(Node node, FeatureContext context) {
        List<SupportIssue> issues = new ArrayList<>();
        if (!hasRegisteredRenderer(node.getClass())) {
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

    public SupportReport analyzeSupport(Node node) {
        return analyzeSupport(new MeaningTree(node));
    }

    public SupportReport analyzeSupport(MeaningTree tree) {
        List<SupportIssue> issues = new ArrayList<>();
        for (NodeInfo info : tree) {
            if (info == null || info.node() == null) {
                continue;
            }
            FeatureContext context = new FeatureContext(this, tree, info, info.node());
            issues.addAll(checkNodeSupport(info.node(), context));
        }
        return new SupportReport(issues);
    }

    public final String toString(Node node) {
        var result = dispatchRenderer(node);
        return applyHooks(node, result);
    }

    public abstract OperatorToken mapToToken(Expression expr);

    public String toString(MeaningTree mt) {
        origin = mt;
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
