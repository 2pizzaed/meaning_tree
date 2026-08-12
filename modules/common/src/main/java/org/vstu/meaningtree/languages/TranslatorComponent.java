package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigParameter;
import org.vstu.meaningtree.utils.frames.FrameStack;
import org.vstu.meaningtree.utils.hooks.HookHost;
import org.vstu.meaningtree.utils.hooks.HookRegistry;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

public abstract class TranslatorComponent implements HookHost {
    private Config config;
    protected TranslatorContext ctx;
    protected LanguageTranslator translator;

    /**
     * Реестр хуков компонента. Языковые хуки регистрируются наследником при создании,
     * хуки прогона — внешним потребителем через {@link HookRegistry#openScope()}.
     */
    protected final HookRegistry hooks = new HookRegistry(this);

    public void setConfig(Config config) {
        this.config = config;
    }

    public TranslatorComponent(LanguageTranslator translator) {
        this.translator = translator;
        this.ctx = new TranslatorContext(this, translator);
    }

    /**
     * Реестр хуков компонента — точка встраивания для внешних потребителей.
     */
    public HookRegistry hooks() {
        return hooks;
    }

    /**
     * Сбросить состояние текущей трансляции: контекст создаётся заново, хуки прогона
     * снимаются.
     * <p>
     * Раньше отсюда же таблица областей видимости публиковалась в транслятор — и побеждал
     * тот компонент, который откатился последним, включая токенизатор с его пустой таблицей.
     * Публикацией теперь занимается {@link LanguageTranslator}: только он знает, какая фаза
     * завершилась и завершилась ли успешно.
     */
    public void rollbackContext() {
        this.ctx = new TranslatorContext(this, translator);
        hooks.clearRunScoped();
    }

    /**
     * Контекст текущей трансляции. Пересоздаётся при {@link #rollbackContext()}.
     */
    public TranslatorContext context() {
        return ctx;
    }

    @Override
    public ScopeTable hookScope() {
        return ctx.getScopeTable();
    }

    @Override
    public FrameStack hookFrames() {
        return ctx.callFrames();
    }

    @Override
    public String hookLanguageName() {
        return translator.getLanguageName();
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

    public LanguageTranslator getTranslator() {
        return translator;
    }

    protected boolean getConfigFlag(String id) {
        return getConfigParameter(id).asBoolean();
    }
}
