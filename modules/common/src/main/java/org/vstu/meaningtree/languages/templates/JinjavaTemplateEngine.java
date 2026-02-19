package org.vstu.meaningtree.languages.templates;

import com.hubspot.jinjava.Jinjava;

import java.util.Map;

public class JinjavaTemplateEngine implements TemplateEngine {
    private final Jinjava jinjavaInstance;

    public JinjavaTemplateEngine() {
        this.jinjavaInstance = new Jinjava();
    }

    @Override
    public String render(String templateSource, Map<String, Object> model) {
        return jinjavaInstance.render(templateSource, model);
    }
}
