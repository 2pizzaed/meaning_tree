package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.params.DisableCompoundComparisonConversion;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.util.HashMap;
import java.util.Map;

public class PythonTranslator extends LanguageTranslator {
    public static final int ID = 1;

    public PythonTranslator(Map<String, String> rawStringConfig) {
        super(new PythonLanguage(), null, rawStringConfig);
        this.setViewer(new PythonViewer(this.getTokenizer()));
    }

    public PythonTranslator() {
        super(new PythonLanguage(), null, new HashMap<>());
        this.setViewer(new PythonViewer(this.getTokenizer()));
    }

    @Override
    public int getLanguageId() {
        return ID;
    }

    @Override
    public String getLanguageName() {
        return "python";
    }

    @Override
    public LanguageTokenizer getTokenizer() {
        return new PythonTokenizer(this);
    }

    @Override
    public String prepareCode(String code) {
        return code;
    }

    @Override
    public TokenList prepareCode(TokenList list) {
        return list;
    }

    @Override
    protected Config getDeclaredConfig() {
        return new Config(new DisableCompoundComparisonConversion(false, ConfigScope.TRANSLATOR));
    }

    @Override
    public LanguageTranslator clone() {
        var clone = new PythonTranslator();
        clone._config = this.getConfig();
        return clone;
    }

}
