package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.params.ExpressionMode;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;
import org.vstu.meaningtree.utils.tokens.TokenType;

import java.util.HashMap;
import java.util.Map;

public class JavaTranslator extends LanguageTranslator {
    public static final int ID = 2;

    public JavaTranslator(Map<String, String> rawConfig) {
        super(new JavaLanguage(), null, rawConfig);
        this.setViewer(new JavaViewer(this.getTokenizer()));
    }

    public JavaTranslator() {
        super(new JavaLanguage(), null, new HashMap<>());
        this.setViewer(new JavaViewer(this.getTokenizer()));
    }

    @Override
    public int getLanguageId() {
        return ID;
    }

    @Override
    public LanguageTokenizer getTokenizer() {
        return new JavaTokenizer(this);
    }

    @Override
    public String prepareCode(String code) {
        boolean expressionMode = getConfigParameter(ExpressionMode.class).orElse(false);

        if (expressionMode) {
            if (!code.endsWith(";")) {
                code += ";";
            }
            code = String.format("class Main { public static void main(String[] args) {%s} }", code);
        }

        return code;
    }

    @Override
    public TokenList prepareCode(TokenList list) {
        boolean expressionMode = getConfigParameter(ExpressionMode.class).orElse(false);

        if (expressionMode) {
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
}
