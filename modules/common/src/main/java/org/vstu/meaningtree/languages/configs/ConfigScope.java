package org.vstu.meaningtree.languages.configs;

public enum ConfigScope {
    VIEWER, // only applicable to viewer
    PARSER, // only applicable to parser
    TOKENIZER, // only applicable to tokenizer
    TRANSLATOR, // applicable for all translator components and translator at all
    ANY
}
