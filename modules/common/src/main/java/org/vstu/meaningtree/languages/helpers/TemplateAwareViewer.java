package org.vstu.meaningtree.languages.helpers;

import org.vstu.meaningtree.languages.helpers.templates.TemplateEngine;
import org.vstu.meaningtree.languages.helpers.templates.TemplateRepository;
import org.vstu.meaningtree.nodes.Node;

import java.util.Map;

public interface TemplateAwareViewer {
    void configureTemplateEngine(TemplateRepository templateRepository, TemplateEngine templateEngine);

    void configureJinjaTemplateEngine(String classpathBasePath);

    void disableTemplateEngine();

    boolean isTemplateEngineConfigured();

    String renderTemplate(String templateKey, Map<String, Object> model);

    String renderTemplate(String templateKey, Node node, Map<String, Object> model);
}
