# MeaningTree Project Reference

Use this reference for project-wide investigation before diving into individual files.

## Project Role

MeaningTree provides a semantic/universal AST for source code and uses language-specific translators to parse source languages into a `MeaningTree`, serialize it, and generate code or tokens in target languages.

The repository is a Java 21 Maven multi-module project.

## Module Map

From the root `pom.xml`:

```text
modules/application       CLI application and supported-language registry
modules/common            MeaningTree core model, nodes, serializers, language abstractions
modules/languages/java    Java translator
modules/languages/python  Python translator
modules/languages/cpp     C++ translator
modules/test              Test framework/support code
modules/utils             Shared utilities
```

Check each module `pom.xml` before assuming dependencies or package ownership.

## High-Value Entry Points

```text
README.md
pom.xml
modules/application/pom.xml
modules/application/src/main/java/org/vstu/meaningtree/Main.java
modules/application/src/main/java/org/vstu/meaningtree/SupportedLanguage.java
modules/common/src/main/java/org/vstu/meaningtree/MeaningTree.java
modules/common/src/main/java/org/vstu/meaningtree/nodes
modules/common/src/main/java/org/vstu/meaningtree/languages
modules/common/src/main/java/org/vstu/meaningtree/serializers
modules/languages/java/src/main/java
modules/languages/python/src/main/java
modules/languages/cpp/src/main/java
modules/test/src/main/java
```

The checked-in `README.md` may display as mojibake in some terminals. Use it for intent, but do not rewrite encoding unless the user explicitly asks for an encoding fix.

## Investigation Workflow

1. Identify whether the request concerns CLI behavior, core meaning tree model, a language translator, serialization, tests, or Maven build.
2. Use `rg` to find types, methods, config keys, node classes, and serializer names before opening broad files.
3. Read the nearest module `pom.xml` to understand dependencies and Java version assumptions.
4. Trace behavior through interfaces in `modules/common` before reading language-specific implementations.
5. For language translation behavior, inspect the relevant translator module and compare with sibling translators only when the pattern is unclear.
6. For behavior crossing module boundaries, confirm the actual dependency direction from Maven rather than assuming package names.
7. For questions involving unavailable source or third-party APIs, inspect `*-sources.jar` files under `~/.m2/repository`.
8. Only clone external documentation when explicitly requested by the user.

## Search Patterns

```powershell
rg -n "class <Name>|interface <Name>|enum <Name>" modules
rg -n "extends LanguageTranslator|implements .*Translator|getMeaningTree|getCode|getCodeAsTokens" modules
rg -n "ConfigParameters|ConfigParameter|fromJson|toConfig" modules
rg -n "JsonSerializer|JsonDeserializer|XMLSerializer|RDFSerializer|GraphvizDotSerializer" modules
rg -n "setupId|startNodeId|startTokenId|bytePositionAnnotations" modules
```

Use `rg --files modules | rg "<name>|pom.xml"` to locate files quickly.

## CLI Relationship

The CLI lives in `modules/application` and delegates most work to project APIs:

```text
Main.java                  JCommander command definitions, IO, serializer dispatch
SupportedLanguage.java     maps java/python/c++ names to translator classes
LanguageTranslator         common translator abstraction
MeaningTree / Node         core semantic AST objects
serializers/*              JSON/XML/RDF/DOT conversion
SourceMapGenerator         source map output path
```

Load `references/cli.md` for exact CLI options and examples.

## Build And Test Commands

```powershell
mvn package
mvn test
mvn -pl modules/application -am package
mvn -pl modules/languages/java -am test
mvn -pl modules/languages/python -am test
mvn -pl modules/languages/cpp -am test
```

If Maven needs network access to resolve dependencies and sandboxing blocks it, request escalation rather than working around dependency resolution.

## Documentation Policy

Local repository docs under `docs/` may be read normally if present and relevant.

External docs are available at `https://github.com/CompPrehension/CompPrehension.github.io/tree/production/docs/meaning_tree`, but only clone/read them when the user explicitly asks for documentation.
