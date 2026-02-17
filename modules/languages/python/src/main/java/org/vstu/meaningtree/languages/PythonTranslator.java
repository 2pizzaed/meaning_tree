package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigParameters;
import org.vstu.meaningtree.languages.configs.ConfigScope;
import org.vstu.meaningtree.languages.configs.ConfigValue;
import org.vstu.meaningtree.utils.tokens.TokenList;

import java.util.Map;

public class PythonTranslator extends LanguageTranslator {
    public static final int ID = 1;

    public PythonTranslator(Map<String, Object> rawStringConfig) {
        super(rawStringConfig);
        this.init(new PythonLanguage(this), new PythonViewer(this));
    }

    public PythonTranslator(Config config) {
        super(config);
        this.init(new PythonLanguage(this), new PythonViewer(this));
    }

    public PythonTranslator() {
        super();
        this.init(new PythonLanguage(this), new PythonViewer(this));
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
    protected Config extendConfigParameters() {
        var compoundComparisons = ConfigParameters.registerIfNotExists(this, "disableCompoundComparisons", new ConfigValue(false), ConfigScope.VIEWER);
        var typeAnno = ConfigParameters.registerIfNotExists(this, "disableTypeAnnotations", new ConfigValue(false), ConfigScope.VIEWER);
        var transUnitMode = ConfigParameters.translationUnitMode.withValue("short");

        return new Config(compoundComparisons, typeAnno, transUnitMode);
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
    public LanguageTranslator clone() {
        var clone = new PythonTranslator();
        clone._config = this.getConfig();
        return clone;
    }

}
