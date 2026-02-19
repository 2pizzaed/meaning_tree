package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.templates.ClasspathTemplateRepository;
import org.vstu.meaningtree.languages.templates.JinjavaTemplateEngine;
import org.vstu.meaningtree.languages.templates.TemplateEngine;
import org.vstu.meaningtree.languages.templates.TemplateRepository;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.ParenthesesFiller;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    protected String applyHooks(Node node, String result) {
        for (var function : postProcessFunctions) {
            result = function.apply(node, result);
        }
        return result;
    }

    public final String toString(Node node) {
        var result = formString(node);
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

    public void disableTemplateEngine() {
        templateRepository = null;
        templateEngine = null;
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

    public static final class TemplateRenderHelper {
        private final LanguageViewer viewer;
        private final Map<String, Object> baseModel;

        public TemplateRenderHelper(LanguageViewer viewer, Map<String, Object> baseModel) {
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
