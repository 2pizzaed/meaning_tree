package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigScopedParameter;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.ParenthesesFiller;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

abstract public class LanguageViewer {
    private Config _config;
    protected MeaningTree origin;
    private List<BiFunction<Node, String, String>> postProcessFunctions = new ArrayList<>();

    public LanguageViewer() {
        this.parenFiller = new ParenthesesFiller(this::mapToToken);
    }

    public LanguageViewer(LanguageTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.parenFiller = new ParenthesesFiller(this::mapToToken);
    }

    public boolean registerPostprocessFunction(BiFunction<Node, String, String> function) {
        return this.postProcessFunctions.add(function);
    }

    public boolean removePostprocessFunction(BiFunction<Node, String, String> function) {
        return this.postProcessFunctions.remove(function);
    }

    protected LanguageTokenizer tokenizer;
    protected ParenthesesFiller parenFiller;

    protected abstract String formString(Node node);

    public final String toString(Node node) {
        var result = formString(node);
        for (var function : postProcessFunctions) {
            result = function.apply(node, result);
        }
        return result;
    }

    public abstract OperatorToken mapToToken(Expression expr);

    public String toString(MeaningTree mt) {
        origin = mt;
        return toString(mt.getRootNode());
    }

    void setConfig(Config config) {
        _config = config;
    }

    protected <P, T extends ConfigScopedParameter<P>> Optional<P> getConfigParameter(Class<T> configClass) {
        return Optional.ofNullable(_config).flatMap(config -> config.get(configClass));
    }
}
