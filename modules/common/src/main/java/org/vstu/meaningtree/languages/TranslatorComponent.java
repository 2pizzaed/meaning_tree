package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigParameter;

public abstract class TranslatorComponent {
    private Config config;
    protected TranslatorContext ctx;
    protected LanguageTranslator translator;

    public void setConfig(Config config) {
        this.config = config;
    }

    public TranslatorComponent(LanguageTranslator translator) {
        this.translator = translator;
        this.ctx = new TranslatorContext(this, translator);
    }

    public void rollbackContext() {
        translator._latestScopeTable = ctx.getGlobalScope();
        this.ctx = new TranslatorContext(this, translator);
    }

    protected ConfigParameter getConfigParameter(String id) {
        return config.get(id);
    }

    protected ConfigParameter getConfigParameter(ConfigParameter anyInstance) {
        return config.get(anyInstance.getId());
    }

    protected boolean isExpressionMode() {
        return getConfigParameter("translationUnitMode").asString().equals("expression");
    }

    protected boolean getConfigFlag(String id) {
        return getConfigParameter(id).asBoolean();
    }
}
