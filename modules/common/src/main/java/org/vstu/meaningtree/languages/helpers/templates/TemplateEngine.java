package org.vstu.meaningtree.languages.helpers.templates;

import java.util.Map;

public interface TemplateEngine {
    String render(String templateSource, Map<String, Object> model);
}
