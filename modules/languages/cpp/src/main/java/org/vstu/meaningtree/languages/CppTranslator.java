package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.params.ExpressionMode;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;
import org.vstu.meaningtree.utils.tokens.TokenType;

import java.util.HashMap;
import java.util.Map;

public class CppTranslator extends LanguageTranslator {
    public static final int ID = 0;

    public CppTranslator(Map<String, String> rawConfig) {
        super(new CppLanguage(), null, rawConfig);
        this.setViewer(new CppViewer(this.getTokenizer()));
    }

    public CppTranslator() {
        super(new CppLanguage(), null, new HashMap<>());
        this.setViewer(new CppViewer(this.getTokenizer()));
    }

    @Override
    public int getLanguageId() {
        return ID;
    }

    @Override
    public LanguageTokenizer getTokenizer() {
        return new CppTokenizer(this);
    }

    @Override
    public String prepareCode(String code) {
        boolean expressionMode = getConfigParameter(ExpressionMode.class).orElse(false);

        if (expressionMode) {
            if (!code.endsWith(";")) {
                code += ";";
            }
            code = String.format("int main() {%s}", code);
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
            TokenList final_ = getTokenizer().tokenize("int main() {}", false);
            final_.addAll(
                    final_.indexOf(
                            final_.stream().filter((Token t) -> t.value.equals("{")).findFirst().orElse(null)
                    ),
                    list
            );
            return final_;
        }

        return list;
    }

}
