package org.vstu.meaningtree.languages.templates;

import java.util.Map;

public interface TemplateEngine {
    String render(String templateSource, Map<String, Object> model);
}
