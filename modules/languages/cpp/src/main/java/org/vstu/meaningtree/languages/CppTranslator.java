package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigParameters;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.ConfigValue;
import org.vstu.meaningtree.utils.tokens.Token;
import org.vstu.meaningtree.utils.tokens.TokenList;
import org.vstu.meaningtree.utils.tokens.TokenType;

import java.util.Map;

public class CppTranslator extends LanguageTranslator {
    public static final int ID = 0;

    public CppTranslator(Map<String, Object> rawConfig) {
        super(rawConfig);
        this.init(new CppLanguage(this), new CppViewer(this));
    }

    public CppTranslator() {
        super();
        this.init(new CppLanguage(this), new CppViewer(this));
    }

    @Override
    public int getLanguageId() {
        return ID;
    }

    @Override
    public String getLanguageName() {
        return "c++";
    }

    @Override
    protected Config extendConfigParameters() {
        var cMode = ConfigParameters.registerIfNotExists(this, "preferC", new ConfigValue(false), ConfigScope.ANY);
        return new Config(cMode);
    }

    @Override
    public LanguageTokenizer getTokenizer() {
        return new CppTokenizer(this);
    }

    @Override
    public String prepareCode(String code) {
        if (isExpressionMode()) {
            if (!code.endsWith(";")) {
                code += ";";
            }
            code = String.format("int main() {%s}", code);
        }

        return code;
    }

    @Override
    public TokenList prepareCode(TokenList list) {
        if (isExpressionMode()) {
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

    @Override
    public LanguageTranslator clone() {
        var clone = new CppTranslator();
        clone._config = this.getConfig();
        return clone;
    }

}
