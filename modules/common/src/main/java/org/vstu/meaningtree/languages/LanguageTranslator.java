package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.Nullable;
import org.treesitter.TSException;
import org.treesitter.TSNode;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigBuilder;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.params.*;
import org.vstu.meaningtree.languages.configs.parser.ConfigMapping;
import org.vstu.meaningtree.languages.configs.parser.ConfigParser;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Experimental;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.scopes.ScopeTable;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenGroup;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class LanguageTranslator implements Cloneable {
    protected LanguageParser _language;
    protected LanguageViewer _viewer;

    protected Config _config = new Config();
    protected ScopeTable _latestScopeTable = null;

    public static Config getPredefinedCommonConfig() {
        return new Config(
                new ExpressionMode(false),
                new TranslationUnitMode(true),
                new SkipErrors(false),
                new BytePositionAnnotationMode(false)
        );
    }

    public abstract int getLanguageId();

    public abstract String getLanguageName();

    protected Config getDeclaredConfig() { return new Config(); }

    protected ConfigParser configParser = defaultConfigParser();

    protected ConfigParser defaultConfigParser() {
        return new ConfigParser(
                new ConfigMapping<>(
                        "disableCompoundComparisonConversion",
                        DisableCompoundComparisonConversion::parse,
                        DisableCompoundComparisonConversion::new
                ),
                new ConfigMapping<>(
                        "expressionMode",
                        ExpressionMode::parse,
                        ExpressionMode::new
                ),
                new ConfigMapping<>(
                        "skipErrors",
                        SkipErrors::parse,
                        SkipErrors::new
                ),
                new ConfigMapping<>(
                        "translationUnitMode",
                        TranslationUnitMode::parse,
                        TranslationUnitMode::new
                ),
                new ConfigMapping<>(
                        "bytePositionsAnnotate",
                        BytePositionAnnotationMode::parse,
                        BytePositionAnnotationMode::new
                )
        );
    }

    /**
     * Создает транслятор языка
     * Требует дальнейшей инициализации методом init(parser, viewer)
     * @param rawConfig - конфигурация в формате "название - значение" в виде строки (тип будет выведен автоматически из строки)
     */
    protected LanguageTranslator(Map<String, String> rawConfig) {
        var configBuilder = new ConfigBuilder();

        // Загрузка конфигов, специфических для конкретного языка
        for (var entry : rawConfig.entrySet()) {
            var parsed = configParser.parse(entry.getKey(), entry.getValue());
            if (parsed != null) {
                configBuilder.add(parsed);
            }
        }

        _config = _config.merge(getPredefinedCommonConfig(), getDeclaredConfig(), configBuilder.toConfig());
    }

    @Nullable
    public ScopeTable getLatestScopeTable() {
        return _latestScopeTable;
    }

    public MeaningTree getMeaningTree(String code) {
        MeaningTree mt = _language.getMeaningTree(prepareCode(code));
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
        _language.rollbackContext();
        return mt;
    }

    protected void init(LanguageParser parser, LanguageViewer viewer) {
        _language = parser;
        _viewer = viewer;

        if (parser != null) {
            _language.setConfig(
                    _config.subset(ConfigScopedParameter.forScopes(ConfigScope.PARSER, ConfigScope.TRANSLATOR, ConfigScope.ANY))
            );
        }

        if (viewer != null) {
            _viewer.setConfig(
                    _config.subset(ConfigScopedParameter.forScopes(ConfigScope.VIEWER, ConfigScope.TRANSLATOR, ConfigScope.ANY))
            );
        }
    }

    @Experimental
    public MeaningTree getMeaningTree(TSNode node, String code) {
        MeaningTree mt = _language.getMeaningTree(node, code);
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
        _language.rollbackContext();
        return mt;
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
        MeaningTree mt = _language.getMeaningTree(prepareCode(code), values);
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
        _language.rollbackContext();
        return mt;
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

    public String getCode(MeaningTree mt) {
        var result = _viewer.toString(mt);
        _viewer.rollbackContext();
        return result;
    }

    public Pair<Boolean, String> tryGetCode(MeaningTree mt) {
        try {
            String result = getCode(mt);
            return ImmutablePair.of(true, result);
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
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

    public String getCode(Node node) {
        var result = _viewer.toString(node);
        _viewer.rollbackContext();
        return result;
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
        var tokenizer = getTokenizer().setEnabledNavigablePseudoTokens(enableWhitespaces);
        if (detailedTokens) {
            return tokenizer.tokenizeExtended(mt);
        } else {
            String code = getCode(mt);
            return tokenizer.tokenize(code, skipPreparations);
        }
    }

    public TokenList getCodeAsTokens(MeaningTree mt,
                                     boolean enableWhitespaces) {
        return getCodeAsTokens(mt, enableWhitespaces, true, false);
    }

    protected <P, T extends ConfigScopedParameter<P>> Optional<P> getConfigParameter(Class<T> configClass) {
        return Optional.ofNullable(_config).flatMap(config -> config.get(configClass));
    }

    public abstract String prepareCode(String code);

    public abstract TokenList prepareCode(TokenList list);

    @Override
    public abstract LanguageTranslator clone();

    protected Config getConfig() {
        return _config;
    }
}
