package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.treesitter.TSException;
import org.treesitter.TSNode;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.ConcurrentTranslationException;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.exceptions.UnsupportedConfigParameterException;
import org.vstu.meaningtree.languages.configs.*;
import org.vstu.meaningtree.languages.support.SupportReport;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Experimental;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.scopes.ScopeTable;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenGroup;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class LanguageTranslator implements Cloneable {
    protected LanguageParser _language;
    protected LanguageViewer _viewer;

    protected Config _config = ConfigParameters.defaultConfig();

    /**
     * Таблица областей видимости последнего разбора. Пишется только по завершении разбора.
     */
    private ScopeTable _parseScopeTable = null;

    /**
     * Таблица областей видимости последнего рендеринга. Пишется только по завершении
     * рендеринга. Отдельная от {@link #_parseScopeTable}, потому что viewer строит свою
     * таблицу заново, а не читает построенную парсером (§3.4 обзора — источник не устранён).
     */
    private ScopeTable _renderScopeTable = null;

    /**
     * Какая из двух таблиц заполнена последней. Ровно то, что возвращает
     * {@link #getLatestScopeTable()}.
     */
    private ScopeTable _latestScopeTable = null;

    private Path _projectRootPath = null;
    private Path _currentFileRelPath = null;

    /**
     * Владение транслятором: поток, который сейчас внутри трансляции, и глубина повторных
     * входов из него же. Оба поля читаются и пишутся только под {@link #ownershipLock}.
     */
    private final Object ownershipLock = new Object();
    private Thread _owner = null;
    private String _ownerOperation = null;
    private int _ownerDepth = 0;

    public abstract int getLanguageId();

    public abstract String getLanguageName();

    protected abstract Config extendConfigParameters();

    public Config getDefaultConfig() {
        Config extended = extendConfigParameters();
        Config target = ConfigParameters.defaultConfig();
        if (extended != null) {
            // Config неизменяем: merge возвращает новый экземпляр, результат нельзя отбрасывать
            target = target.merge(extended);
        }
        return target;
    }

    /**
     * Создает транслятор языка
     * Требует дальнейшей инициализации методом init(parser, viewer)
     * @param rawConfig - конфигурация в формате "название - значение" в виде строки (тип будет выведен автоматически из строки)
     */
    public LanguageTranslator(Map<String, Object> rawConfig) {
        Config extended = Optional.ofNullable(extendConfigParameters()).orElse(new Config());
        var configBuilder = new ConfigBuilder().fromRawMap(this.getClass(), rawConfig);
        _config = _config.merge(extended, configBuilder.toConfig());
    }

    public LanguageTranslator(Config config) {
        _config = _config.merge(
                Optional.ofNullable(extendConfigParameters()).orElse(new Config()),
                config);
    }

    private void setupConfig(Config additional) {
        _config = _config.merge(
                Optional.ofNullable(extendConfigParameters()).orElse(new Config()),
                additional);
        for (ConfigParameter param : _config) {
            if (param.isNull() && param.isReadOnly()) {
                throw new UnsupportedConfigParameterException("You must extend read-only parameter with non-null value `%s`".formatted(param.getId()));
            }
        }
    }

    public LanguageTranslator() {
        this(new Config());
    }

    /**
     * Таблица областей видимости последней <b>завершённой</b> фазы — разбора или рендеринга,
     * смотря что было позже.
     * <p>
     * Раньше сюда писал любой компонент из {@code rollbackContext()}, включая токенизатор с
     * его всегда пустой таблицей, — то есть значение зависело от того, кто последним
     * откатился, и после {@code getCodeAsTokens} оказывалось пустым. Теперь публикуют только
     * две фазы, и только успешно завершившиеся: упавшая трансляция таблицу не подменяет.
     * <p>
     * Если нужна конкретная фаза, а не «последняя», спрашивайте {@link #getParseScopeTable()}
     * или {@link #getRenderScopeTable()} — они не зависят от порядка вызовов.
     */
    @Nullable
    public ScopeTable getLatestScopeTable() {
        return _latestScopeTable;
    }

    /** Таблица последнего успешного разбора. */
    @Nullable
    public ScopeTable getParseScopeTable() {
        return _parseScopeTable;
    }

    /** Таблица последнего успешного рендеринга. */
    @Nullable
    public ScopeTable getRenderScopeTable() {
        return _renderScopeTable;
    }

    private void publishParseScopeTable() {
        _parseScopeTable = _language.context().getScopeTable();
        _latestScopeTable = _parseScopeTable;
    }

    private void publishRenderScopeTable() {
        _renderScopeTable = _viewer.context().getScopeTable();
        _latestScopeTable = _renderScopeTable;
    }

    /**
     * Выполнить операцию, эксклюзивно владея транслятором.
     * <p>
     * Транслятор и его компоненты держат состояние текущей трансляции, общее на компонент,
     * поэтому две одновременные трансляции затирают друг друга. Здесь это ловится сразу:
     * поток, пришедший к занятому транслятору, получает
     * {@link ConcurrentTranslationException} с указанием взять свой экземпляр. Повторный
     * вход из того же потока разрешён и обязан быть — вложенные вызовы штатны
     * ({@code getCodeAsTokens} зовёт {@code getCode}, токенизатор зовёт парсер).
     * <p>
     * Это не делает транслятор параллельным: это делает нарушение видимым. Молчаливое
     * ожидание на мониторе было бы хуже — оно прячет ошибку использования и превращает её в
     * необъяснимую задержку, а состояние всё равно общее.
     */
    protected final <T> T exclusively(String operation, Supplier<T> body) {
        acquireOwnership(operation);
        try {
            return body.get();
        } finally {
            releaseOwnership();
        }
    }

    private void acquireOwnership(String operation) {
        Thread current = Thread.currentThread();
        synchronized (ownershipLock) {
            if (_owner == null) {
                _owner = current;
                _ownerOperation = operation;
                _ownerDepth = 1;
                return;
            }
            if (_owner == current) {
                _ownerDepth++;
                return;
            }
            throw new ConcurrentTranslationException(
                    ("Translator %s is already in use: thread '%s' is running %s, "
                            + "thread '%s' asked for %s. One translator serves one thread at a time — "
                            + "give each thread its own instance (translator.clone()).")
                            .formatted(getLanguageName(), _owner.getName(), _ownerOperation,
                                    current.getName(), operation));
        }
    }

    private void releaseOwnership() {
        synchronized (ownershipLock) {
            _ownerDepth--;
            if (_ownerDepth == 0) {
                _owner = null;
                _ownerOperation = null;
            }
        }
    }

    public LanguageTranslator withSourceContext(Path projectRootPath, Path currentFileRelPath) {
        Objects.requireNonNull(projectRootPath, "projectRootPath must not be null");
        Objects.requireNonNull(currentFileRelPath, "currentFileRelPath must not be null");

        Path normalizedProjectRoot = projectRootPath.normalize();
        Path normalizedCurrentFile = currentFileRelPath.normalize();

        if (!normalizedProjectRoot.isAbsolute()) {
            throw new IllegalArgumentException("projectRootPath must be absolute");
        }
        if (normalizedCurrentFile.isAbsolute()) {
            throw new IllegalArgumentException("currentFileRelPath must be relative");
        }

        _projectRootPath = normalizedProjectRoot;
        _currentFileRelPath = normalizedCurrentFile;
        return this;
    }

    public Optional<Path> getProjectRootPath() {
        return Optional.ofNullable(_projectRootPath);
    }

    public Optional<Path> getCurrentFileRelPath() {
        return Optional.ofNullable(_currentFileRelPath);
    }

    public boolean hasSourceContext() {
        return _projectRootPath != null && _currentFileRelPath != null;
    }

    protected void clearSourceContext() {
        _projectRootPath = null;
        _currentFileRelPath = null;
    }

    public MeaningTree getMeaningTree(String code) {
        return exclusively("getMeaningTree(String)", () -> {
            MeaningTree mt = null;
            try {
                mt = _language.getMeaningTree(prepareCode(code));
                return mt;
            } finally {
                finalizeParsingState(mt);
            }
        });
    }

    protected void init(LanguageParser parser, LanguageViewer viewer) {
        _language = parser;
        _viewer = viewer;

        if (parser != null) {
            _language.setConfig(
                    _config.subset(ConfigParameter.forScopes(ConfigScope.PARSER, ConfigScope.TRANSLATOR, ConfigScope.ANY))
            );
        }

        if (viewer != null) {
            _viewer.setConfig(
                    _config.subset(ConfigParameter.forScopes(ConfigScope.VIEWER, ConfigScope.TRANSLATOR, ConfigScope.ANY))
            );
        }
    }

    @Experimental
    public MeaningTree getMeaningTree(TSNode node, String code) {
        return exclusively("getMeaningTree(TSNode, String)", () -> {
            MeaningTree mt = null;
            try {
                mt = _language.getMeaningTree(node, code);
                return mt;
            } finally {
                finalizeParsingState(mt);
            }
        });
    }

    @Experimental
    public Pair<Boolean, MeaningTree> tryGetMeaningTree(TSNode node, String code) {
        try {
            return ImmutablePair.of(true, getMeaningTree(node, code));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    public Pair<Boolean, MeaningTree> tryGetMeaningTree(String code) {
        try {
            return ImmutablePair.of(true, getMeaningTree(code));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    /**
     * Получить meaning tree
     * @param code код
     * @param values пары байтовой позиции (start, end) и значений для присваивания их ассоциированным с ними узлов
     * @return meaning tree
     */
    protected MeaningTree getMeaningTree(String code, HashMap<int[], Object> values) {
        return exclusively("getMeaningTree(String, values)", () -> {
            MeaningTree mt = null;
            try {
                mt = _language.getMeaningTree(prepareCode(code), values);
                return mt;
            } finally {
                finalizeParsingState(mt);
            }
        });
    }

    private void finalizeMeaningTree(MeaningTree mt) {
        _language.postProcessTree(mt);
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
    }

    /**
     * Завершение разбора: доработать дерево, опубликовать таблицу и — <b>обязательно</b> —
     * сбросить контекст парсера.
     * <p>
     * Сброс в {@code finally} потому, что упавший разбор оставляет контекст в произвольном
     * состоянии (незакрытые области видимости, недостроенные тела), и следующая трансляция на
     * этом же трансляторе начала бы с чужого мусора. Таблица при этом публикуется только при
     * успехе: {@code getLatestScopeTable()} после неудачи должен показывать прошлый удачный
     * разбор, а не обломки.
     */
    private void finalizeParsingState(@Nullable MeaningTree mt) {
        try {
            if (mt != null) {
                finalizeMeaningTree(mt);
                publishParseScopeTable();
            }
        } finally {
            _language.rollbackContext();
            clearSourceContext();
        }
    }

    public MeaningTree getMeaningTree(TokenList tokenList) {
        MeaningTree mt = getMeaningTree(String.join(" ", tokenList.stream().map((Token t) -> t.value).toList()));
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
        return mt;
    }

    public Pair<Boolean, MeaningTree> tryGetMeaningTree(TokenList tokens) {
        try {
            return ImmutablePair.of(true, getMeaningTree(tokens));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    /**
     * Получить meaning tree
     * @param tokenList токены
     * @param tokenValueTags пары диапазона токенов и значений для присваивания их ассоциированным с ними узлов
     * @return meaning tree с заданными значениями для узлов
     */
    public MeaningTree getMeaningTree(TokenList tokenList, Map<TokenGroup, Object> tokenValueTags) {
        HashMap<int[], Object> codeValueTag = new HashMap<>();
        for (TokenGroup grp : tokenValueTags.keySet()) {
            assert grp.source == tokenList;
            int start = 0;
            for (int i = 0; i < grp.start; i++) {
                start += grp.source.get(i).value.getBytes(StandardCharsets.UTF_8).length;
                start += 1;
            }
            int stop = start + 1;
            for (int i = start; i < grp.stop; i++) {
                stop += grp.source.get(i).value.getBytes(StandardCharsets.UTF_8).length;
                if (i != grp.stop - 1) {
                    stop += 1;
                }
            }
            codeValueTag.put(new int[] {start, stop}, tokenValueTags.get(grp));
        }
        return getMeaningTree(String.join(" ", tokenList.stream().map((Token t) -> t.value).toList()), codeValueTag);
    }

    public Pair<Boolean, MeaningTree> tryGetMeaningTree(TokenList tokens, Map<TokenGroup, Object> tokenValueTags) {
        try {
            return ImmutablePair.of(true, getMeaningTree(tokens, tokenValueTags));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    public abstract LanguageTokenizer getTokenizer();

    public SupportReport analyzeSupport(MeaningTree tree) {
        return _viewer.analyzeSupport(tree);
    }

    public SupportReport analyzeSupport(Node node) {
        return _viewer.analyzeSupport(node);
    }

    public Pair<Boolean, String> tryGetCode(MeaningTree mt) {
        try {
            String result = getCode(mt);
            return ImmutablePair.of(true, result);
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    public String getCode(Node node) {
        return exclusively("getCode(Node)", () -> render(() -> _viewer.toString(node)));
    }

    public String getCode(MeaningTree mt) {
        return exclusively("getCode(MeaningTree)", () -> render(() -> _viewer.toString(mt)));
    }

    /**
     * Рендеринг с гарантированным сбросом контекста viewer'а.
     * <p>
     * Раньше {@code rollbackContext()} стоял после вызова, а не в {@code finally}, поэтому
     * упавший рендеринг оставлял в viewer'е свою таблицу областей видимости, незакрытые
     * области и недостроенные тела — и следующий {@code getCode} на том же трансляторе
     * начинал с них. Через {@code tryGetCode}, который гасит исключение и возвращает
     * «не получилось», это был штатный сценарий, а не экзотика.
     */
    private String render(Supplier<String> rendering) {
        try {
            String result = rendering.get();
            publishRenderScopeTable();
            return result;
        } finally {
            _viewer.rollbackContext();
        }
    }

    public Pair<Boolean, TokenList> tryGetCodeAsTokens(MeaningTree mt, boolean enableWhitespaces,
                                                       boolean detailedTokens, boolean skipPreparations
    ) {
        try {
            TokenList result = getCodeAsTokens(mt, enableWhitespaces, detailedTokens, skipPreparations);
            return ImmutablePair.of(true, result);
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }


    /**
     * Получает токены по дереву без прямого получения токенизатора (синтаксический сахар)
     * @param mt дерево MeaningTree
     * @param enableWhitespaces создавать токены для пробельных символы
     * @param detailedTokens детализация токенов (для выражений)
     * @param skipPreparations пропустить подготовительный этап (например, при false в Java в режиме только выражений, сниппеты кода будут вложены в статическую функцию main класса Main)
     * @return список токенов
     */
    public TokenList getCodeAsTokens(MeaningTree mt,
                                     boolean enableWhitespaces,
                                     boolean detailedTokens,
                                     boolean skipPreparations) {
        return exclusively("getCodeAsTokens(MeaningTree)", () -> {
            var tokenizer = getTokenizer().setEnabledNavigablePseudoTokens(enableWhitespaces);
            if (detailedTokens) {
                return tokenizer.tokenizeExtended(mt);
            } else {
                String code = getCode(mt);
                return tokenizer.tokenize(code, skipPreparations);
            }
        });
    }

    public TokenList getCodeAsTokens(String code,
                                     boolean enableWhitespaces,
                                     boolean skipPreparations) {
        return exclusively("getCodeAsTokens(String)", () -> {
            var tokenizer = getTokenizer().setEnabledNavigablePseudoTokens(enableWhitespaces);
            return tokenizer.tokenize(code, skipPreparations);
        });
    }


    public TokenList getCodeAsTokens(MeaningTree mt,
                                     boolean enableWhitespaces) {
        return getCodeAsTokens(mt, enableWhitespaces, true, false);
    }

    public ConfigParameter getConfigParameter(String id) {
        return _config.get(id);
    }

    public ConfigParameter getConfigParameter(ConfigParameter anyInstance) {
        return _config.get(anyInstance.getId());
    }

    public boolean isExpressionMode() {
        return getConfigParameter("translationUnitMode").asString().equals("expression");
    }

    public boolean isSkipErrors() {
        return getConfigParameter(ConfigParameters.skipErrors).asBoolean();
    }

    /**
     * Отключён ли конвейер анализа после разбора. См.
     * {@link ConfigParameters#skipOptimizations}.
     */
    public boolean isSkipOptimizations() {
        return getConfigParameter(ConfigParameters.skipOptimizations).asBoolean();
    }

    public boolean getConfigFlag(String id) {
        return getConfigParameter(id).asBoolean();
    }

    public abstract String prepareCode(String code);

    public abstract TokenList prepareCode(TokenList list);

    @Override
    public abstract LanguageTranslator clone();

    public abstract LanguageTranslator clone(Config config);

    protected Config getConfig() {
        return _config;
    }
}
