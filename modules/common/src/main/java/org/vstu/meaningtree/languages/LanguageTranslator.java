package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.treesitter.TSException;
import org.treesitter.TSNode;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.configs.*;
import org.vstu.meaningtree.languages.configs.params.*;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.languages.configs.parser.ConfigMapping;
import org.vstu.meaningtree.languages.configs.parser.ConfigParser;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Experimental;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenGroup;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public abstract class LanguageTranslator {
    protected LanguageParser _language;
    protected LanguageViewer _viewer;
    protected Config _config = new Config();

    public static Config getPredefinedCommonConfig() {
        return new Config(
                new ExpressionMode(false, ConfigScope.TRANSLATOR),
                new TranslationUnitMode(true, ConfigScope.VIEWER),
                new SkipErrors(true, ConfigScope.PARSER),
                new EnforceEntryPoint(true, ConfigScope.ANY)
        );
    }

    public abstract int getLanguageId();

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
                        "enforceEntryPoint",
                        EnforceEntryPoint::parse,
                        EnforceEntryPoint::new
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
                )
        );
    }

    /**
     * Создает транслятор языка
     * @param language - parser языка
     * @param viewer - viewer языка
     * @param rawConfig - конфигурация в формате "название - значение" в виде строки (тип будет выведен автоматически из строки)
     */
    protected LanguageTranslator(LanguageParser language, LanguageViewer viewer, Map<String, String> rawConfig) {
        _language = language;
        _viewer = viewer;

        var configBuilder = new ConfigBuilder();

        // Загрузка конфигов, специфических для конкретного языка
        for (var entry : rawConfig.entrySet()) {
            configBuilder.add(configParser.parse(entry.getKey(), entry.getValue()));
        }

        _config = _config.merge(getPredefinedCommonConfig(), getDeclaredConfig(), configBuilder.toConfig());

        if (language != null) {
            _language.setConfig(
                    _config.subset(ConfigScopedParameter.forScopes(ConfigScope.PARSER, ConfigScope.TRANSLATOR))
            );
        }

        if (viewer != null) {
            _viewer.setConfig(
                    _config.subset(ConfigScopedParameter.forScopes(ConfigScope.VIEWER, ConfigScope.TRANSLATOR))
            );
        }
    }

    public MeaningTree getMeaningTree(String code) {
        MeaningTree mt = _language.getMeaningTree(prepareCode(code));
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
        return mt;
    }

    protected void setViewer(LanguageViewer viewer) {
        _viewer = viewer;

        if (_viewer != null) {
            _viewer.setConfig(
                    getDeclaredConfig().subset(ConfigScopedParameter.forScopes(ConfigScope.VIEWER, ConfigScope.TRANSLATOR))
            );
        }
    }

    protected void setParser(LanguageParser parser) {
        _language = parser;
        if (_language != null) {
            _language.setConfig(
                    getDeclaredConfig().subset(ConfigScopedParameter.forScopes(ConfigScope.PARSER, ConfigScope.TRANSLATOR))
            );
        }
    }

    @Experimental
    public MeaningTree getMeaningTree(TSNode node, String code) {
        MeaningTree mt = _language.getMeaningTree(node, code);
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
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

    protected MeaningTree getMeaningTree(String code, HashMap<int[], Object> values) {
        MeaningTree mt = _language.getMeaningTree(prepareCode(code), values);
        mt.setLabel(new Label(Label.ORIGIN, getLanguageId()));
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
        return _viewer.toString(mt);
    }

    public Pair<Boolean, String> tryGetCode(MeaningTree mt) {
        try {
            String result = getCode(mt);
            return ImmutablePair.of(true, result);
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    public Pair<Boolean, TokenList> tryGetCodeAsTokens(MeaningTree mt) {
        try {
            TokenList result = getCodeAsTokens(mt);
            return ImmutablePair.of(true, result);
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    public String getCode(Node node) {
        return _viewer.toString(node);
    }

    public TokenList getCodeAsTokens(MeaningTree mt) {
        return getTokenizer().tokenizeExtended(mt);
    }

    protected <P, T extends ConfigScopedParameter<P>> Optional<P> getConfigParameter(Class<T> configClass) {
        return Optional.ofNullable(_config).flatMap(config -> config.get(configClass));
    }

    public abstract String prepareCode(String code);

    public abstract TokenList prepareCode(TokenList list);
}
