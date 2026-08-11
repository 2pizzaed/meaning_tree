# MeaningTree Project Reference

Load this reference to understand the architecture, navigate the implementation, or resolve behavior that the CLI reference, public documentation, or direct observation does not explain. Use the implementation as a source of truth, but keep skill-driven investigation read-only. Repository development rules belong in `AGENTS.md`.

## Contents

- Project role and runtime shape
- Module map
- Core architecture: tree model, translation, serialization, analysis, scopes, metadata, and tokens
- Application and auxiliary modules
- High-value entry points
- Read-only investigation workflow and search targets

## Project Role

MeaningTree parses supported programming languages into a shared semantic AST and can generate code, tokens, serializations, and source maps from that representation. The implementation is a Java 21 Maven multi-module project.

## Runtime Shape

The main flow is:

```text
source text
  -> LanguageTranslator
  -> language-specific tree-sitter parser
  -> MeaningTree semantic AST
  -> viewer / tokenizer / serializer / source-map generator / analyzer
  -> generated code, tokens, serialized data, mappings, or metrics
```

`LanguageTranslator` is the main facade. It coordinates configuration and source context, prepares source code, invokes a `LanguageParser`, finalizes the resulting tree and scope state, delegates code generation to a `LanguageViewer`, and exposes tokenization and feature-support analysis. Java, Python, and C++ modules supply the concrete parser, viewer, tokenizer, and translator implementations.

## Module Map

```text
modules/application       CLI commands, IO/format dispatch, supported-language registry, shaded app
modules/common            semantic model, translator framework, serializers, traversal, analysis, metadata
modules/languages/java    Java tree-sitter parser, viewer, tokenizer, translator, program transformations
modules/languages/python  Python parser/viewer/tokenizer/translator and Python-specific transformations
modules/languages/cpp     C/C++ parser, viewer, tokenizer, and translator
modules/test              conversion-test framework plus behavioral examples and focused JUnit tests
modules/utils             optional combinator and Swing/Graphviz tree visualization helpers
```

Use this map only to locate evidence about externally observable behavior. Do not treat it as guidance for where or how to implement changes.

## Core Architecture

### Semantic tree and traversal

- `MeaningTree` owns the root `Node`, labels, a lazily built node-ID index, traversal, lookup, cloning, and cache-aware node replacement.
- `nodes` defines the language-independent AST: declarations, definitions, statements, expressions, types, imports/modules, IO, memory operations, and capability interfaces.
- `Node` exposes identity, labels, cloning, child descriptors, and replacement. Child relationships are discovered through `@TreeNode` fields.
- `iterators` provides DFS, BFS, and direct-node iteration. `NodeInfo` carries the node, parent, field descriptor, depth, and path context; field descriptors identify scalar, array, or collection child slots.

### Language translation framework

- `languages/LanguageTranslator` is the public orchestration facade for parsing, generation, tokenization, configuration, source context, latest scope information, and feature support.
- `LanguageParser` wraps tree-sitter parsing and semantic-node construction. Query helpers cache/execute tree-sitter queries and track parse sessions.
- `LanguageViewer` renders semantic nodes into target code through registered renderers, preprocessing hooks, templates, parentheses handling, and support checks.
- `LanguageTokenizer` produces `TokenList` output from source or generated code and can emit detailed operator/operand structure and navigable pseudo-tokens.
- `languages/configs` models typed, scoped configuration shared by translators, parsers, and viewers.
- `languages/helpers/templates` provides classpath template lookup and Jinjava rendering; `languages/helpers` also contains renderer and hook abstractions.
- `languages/support` represents unsupported semantic features and produces `SupportReport`/`SupportIssue` results independently of raw node-class support.

### Serialization and interchange

- `serializers/json` is the direct JSON node/tree/source-map/token representation and contains the node-type registry used for deserialization and hierarchy output.
- `serializers/xml` converts through the JSON representation.
- `serializers/rdf` maps the universal serialized model to and from Jena RDF models.
- `serializers/dot` emits Graphviz DOT for tree visualization and is output-only in the CLI.
- `serializers/model` contains the serializer interfaces, intermediate serialized-node model, Universal serializer/deserializer, labels, lists, and IO aliases.

### Analysis, scopes, metadata, and tokens

- `utils/scopes` maintains lexical scopes plus program-wide symbol, definition, type, hierarchy, and import indexes. Parsers populate a `ScopeTable`, which is also carried in source maps.
- `utils/analysis` contains cyclomatic-complexity analysis, expression-value evaluation, loop-iteration estimation, symbol resolution, and simple type inference.
- `SourceMapGenerator` instruments viewer output to associate node IDs with UTF-8 byte ranges and packages generated code, scope data, language, project context, and metrics in `SourceMap`.
- `utils/tokens` models plain, pseudo, operand, and operator tokens; token groups preserve source ranges and operator metadata such as arity, associativity, position, and operand roles.
- General utilities cover labels, byte positions/ranges, observable lists, replacement results, parentheses insertion, tree-sitter helpers, annotations, hooks, and transliteration.

### Application and auxiliary modules

- `Main` exposes `translate`, `generate`, `list-langs`, and `node-hierarchy`; it handles files/stdin/stdout, configs, modes, serializers, token output, and source maps.
- `SupportedLanguage` maps external language names to the Java, Python, and C++ translator implementations.
- `modules/utils` contains `Combinator` for ordered pair permutations and `Visualizer`, which renders DOT through Graphviz into an interactive Swing view. Do not confuse this module with `modules/common/.../utils`, which contains core analysis and runtime infrastructure.
- `modules/test` includes both the conversion DSL resources and focused tests for serialization, traversal, scopes, and analysis; it can be useful as executable evidence of current behavior.

## High-Value Entry Points

```text
README.md
pom.xml
modules/application/src/main/java/org/vstu/meaningtree/Main.java
modules/application/src/main/java/org/vstu/meaningtree/SupportedLanguage.java
modules/common/src/main/java/org/vstu/meaningtree/MeaningTree.java
modules/common/src/main/java/org/vstu/meaningtree/nodes/Node.java
modules/common/src/main/java/org/vstu/meaningtree/languages/LanguageTranslator.java
modules/common/src/main/java/org/vstu/meaningtree/languages/LanguageParser.java
modules/common/src/main/java/org/vstu/meaningtree/languages/LanguageViewer.java
modules/common/src/main/java/org/vstu/meaningtree/languages/LanguageTokenizer.java
modules/common/src/main/java/org/vstu/meaningtree/languages/SourceMapGenerator.java
modules/common/src/main/java/org/vstu/meaningtree/languages/configs
modules/common/src/main/java/org/vstu/meaningtree/languages/support
modules/common/src/main/java/org/vstu/meaningtree/serializers
modules/common/src/main/java/org/vstu/meaningtree/iterators
modules/common/src/main/java/org/vstu/meaningtree/utils/analysis
modules/common/src/main/java/org/vstu/meaningtree/utils/scopes
modules/common/src/main/java/org/vstu/meaningtree/utils/tokens
modules/languages/java/src/main/java
modules/languages/python/src/main/java
modules/languages/cpp/src/main/java
modules/test/src/main/resources
modules/utils/src/main/java/org/vstu/meaningtree
```

The checked-in `README.md` may display as mojibake in some terminals. Read it for intent, but do not rewrite its encoding as part of an external-usage investigation.

## Read-Only Investigation Workflow

1. Reproduce or identify the question at the public boundary: MCP operation, CLI command, serialized format, generated output, or Java API call.
2. Use `rg` to locate the relevant command, option, type, configuration key, or error message instead of reading broad source trees.
3. Trace CLI behavior from `Main.java` and `SupportedLanguage.java` into the relevant public abstraction in `modules/common`; for Java API questions, begin with `MeaningTree` or `LanguageTranslator`.
4. Inspect a language implementation only when the behavior is language-specific. Compare sibling implementations only when needed to distinguish a shared contract from language-specific behavior.
5. Inspect serializers and deserializers when the question concerns accepted formats, field names, round trips, or format limitations.
6. Inspect resources under `modules/test` as examples of current behavior, while remembering that tests are evidence rather than external documentation.
7. If behavior depends on a library whose source is not present in the repository, inspect an available local Maven `*-sources.jar` before making assumptions.
8. Report the externally relevant conclusion and cite the source locations that establish it. Do not turn the investigation into implementation work unless the user separately requests repository changes; then follow `AGENTS.md`.

## Useful Search Targets

Search for command names and external API surfaces such as:

```text
translate
generate
list-langs
node-hierarchy
LanguageTranslator
getMeaningTree
getCode
getCodeAsTokens
JsonSerializer
JsonDeserializer
ConfigParameter
SourceMapGenerator
```

Prefer repository sources and available local Maven source jars over guesses. Consult external MeaningTree documentation only when the user explicitly requests it.
