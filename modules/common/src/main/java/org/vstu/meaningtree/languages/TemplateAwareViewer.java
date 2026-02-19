package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.templates.TemplateEngine;
import org.vstu.meaningtree.languages.templates.TemplateRepository;
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
