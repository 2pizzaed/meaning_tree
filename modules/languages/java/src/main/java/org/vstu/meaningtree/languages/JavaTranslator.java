package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;
import org.vstu.meaningtree.utils.tokens.TokenType;

import java.util.Map;

public class JavaTranslator extends LanguageTranslator {
    public static final int ID = 2;

    public JavaTranslator(Map<String, Object> rawConfig) {
        super(rawConfig);
        this.init(new JavaLanguage(this), new JavaViewer(this));
    }

    public JavaTranslator() {
        super();
        this.init(new JavaLanguage(this), new JavaViewer(this));
    }

    public JavaTranslator(Config config) {
        super(config);
        this.init(new JavaLanguage(this), new JavaViewer(this));
    }

    @Override
    public int getLanguageId() {
        return ID;
    }

    @Override
    public String getLanguageName() {
        return "java";
    }

    @Override
    protected Config extendConfigParameters() {
        return null;
    }

    @Override
    public LanguageTokenizer getTokenizer() {
        return new JavaTokenizer(this);
    }

    @Override
    public String prepareCode(String code) {
        if (isExpressionMode()) {
            if (!code.endsWith(";")) {
                code += ";";
            }
            code = String.format("class Main { public static void main(String[] args) {%s} }", code);
        }

        return code;
    }

    @Override
    public TokenList prepareCode(TokenList list) {
        if (isExpressionMode()) {
            if (!list.getLast().type.equals(TokenType.SEPARATOR)) {
                list.add(new Token(";", TokenType.SEPARATOR));
            }

            TokenList final_ = getTokenizer().tokenize("class Main { public static void main(String[] args) {;%s} }", true);
            int marker = final_.indexOf(
                    final_.stream().filter((Token t) -> t.value.equals(";")).findFirst().orElse(null)
            );
            final_.remove(marker);
            final_.addAll(
                    marker,
                    list
            );
            return final_;
        }

        return list;
    }

    @Override
    public LanguageTranslator clone() {
        var clone = new JavaTranslator();
        clone._config = this.getConfig();
        return clone;
    }

}
