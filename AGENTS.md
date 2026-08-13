# AGENTS.md

## Project Purpose

MeaningTree is a Java (21+) Maven multi-module project for parsing source code into a shared semantic AST (`MeaningTree`) and generating equivalent code, tokens, serializations, or source maps for supported languages. The project is moving from being only a language converter toward a language-independent static code analysis platform.

## Module And Package Map

- `modules/common`: core model and reusable infrastructure. Put semantic AST nodes under `nodes`, translator abstractions/configuration under `languages`, serializers under `serializers`, common exceptions/iterators/utilities under their matching packages.
- `modules/languages/java`: Java tree-sitter integration, parsing, and code generation.
- `modules/languages/python`: Python tree-sitter integration, parsing, and code generation.
- `modules/languages/cpp`: C/C++ tree-sitter integration, parsing, and code generation.
- `modules/application`: CLI entry point, supported-language registry, command wiring, and shaded runnable jar.
- `modules/test`: JUnit-based conversion test framework and `.test` resource files.
- `modules/utils`: shared helper utilities that do not belong to the core semantic model.

Before adding a feature, choose the narrowest module/package that owns the behavior. Do not put language-specific behavior into `common`; do not put reusable semantic model code into a language module.

## Development Rules

- Run a Maven build after changes that touch Java code: `mvn package` or a narrower command such as `mvn -pl modules/languages/java -am test` when appropriate.
- For conversion-related changes, record the language conversion test status before and after the change. This is temporary: not all conversion tests currently pass, so compare counts and failures to identify new regressions instead of assuming a clean baseline.
- Prefer source-based investigation over guessing. Use `rg` to find relevant nodes, translators, serializers, configs, and tests.
- When project structure matters, request or generate a project tree before choosing modules/packages or planning broad changes.
- When implementing parser/conversion behavior, inspect the actual tree-sitter parse tree for the concrete source snippet. Use the grammar already wired into the relevant language module when possible; otherwise use a known external tree-sitter grammar/tool if needed. Do not infer tree-sitter node shapes from intuition.
- If a question depends on dependency or project source that is not open in the repository, inspect local Maven `*-sources.jar` files in `~/.m2/repository` before relying on assumptions.

## Adding And Changing MeaningTree Node Types

- A new concrete `Node` type is not complete until the relevant serializers and deserializers support it. JSON support is mandatory: update `JsonNodeTypeClassMapper`, `JsonSerializer`, and `JsonDeserializer` as needed, and verify a serialize-deserialize round trip for the new node and its fields.
- The same rule applies to any change in an existing node type, not only to new types. Adding, removing, or renaming a field, changing what a getter returns, or changing a constructor signature all require updating both `JsonSerializer` and `JsonDeserializer` and re-checking the round trip: state that is written but never read back (or never written at all) is silently lost, and the code generated from a deserialized tree starts to differ from the original.
- A field is covered only when both directions handle it. If a value cannot be reconstructed from the serialized form alone (for example, a reference to a node that is not part of the serialized subtree), extend the format so it can be, instead of leaving a dangling reference.
- Before adding support to `UniversalSerializer` or `UniversalDeserializer`, ask the user whether Universal serialization is required for this change. Do not extend the Universal format without that confirmation.
- Mark every field that owns a child node, a collection/array of child nodes, or an optional child node with `@TreeNode`. The node traversal and replacement infrastructure discovers child relationships through these annotations, including annotated fields inherited from superclasses; without them, iteration over the tree will skip those children.
- Remap every node created or substituted outside parsing with `node.remap(origin)` (`Label.REMAPPED`). This covers nodes a viewer builds while rendering (inserted parentheses, `if __name__ == "__main__"` wrappers, a `for` loop rewritten as `while`, a declaration wrapped into a synthetic definition) and in-place replacements of existing nodes (type inference substituting an inferred type, an identifier replaced by `self`). The source map keys byte ranges by node id, and the consumer only holds the tree it passed in: an unmapped node puts an id nobody can resolve into the map, and its origin drops out of the map entirely. `SourceMapGeneratorTests` fails on both.
- Prefer not mutating the tree during rendering at all. If a pass must replace a node, it has to keep the result reproducible: rendering the same tree twice must produce the same node ids, otherwise every generation shifts the source map and invalidates ids already handed out.
- Use `@InternalNode` only for auxiliary node types that are normally nested inside a more meaningful language feature and should not be reported as an independently unsupported feature. `LanguageViewer` treats an internal node (or a subclass of one) as supported when it has no registered renderer during support analysis, unless that exact type is explicitly registered as unsupported. The annotation does not by itself make direct rendering work, so a renderer is still required if the node is dispatched independently.
- When adding a node, inspect neighboring node implementations and cover constructor/accessor semantics, equality or cloning behavior where relevant, traversal through every child field, and required serializer/deserializer mappings rather than treating the Java class alone as the whole feature.

## Temporary And Durable Tests

- Use a temporary directory for quick experiments, one-off regression probes, and small programs written only to inspect tree-sitter parser behavior. If temporarily placed under a module's test directory because that is the easiest way to use its classpath or grammar, remove the probe after the investigation.
- Keep a test in the repository's normal test directory when it thoroughly exercises a complex feature or module from all relevant aspects and provides durable regression coverage.
- For a small test that is unlikely to remain useful, remove it after the work or leave it only in a temporary directory; do not turn exploratory probes into permanent test-suite clutter.
- Clean up transient test files and temporary test directories before handing off the work unless they intentionally remain in an ignored temporary location for continued investigation.

## Conversion Test Baseline

Before a feature that may affect conversion tests:

```powershell
mvn -pl modules/test -am test
```

Capture at least:

- total tests run;
- number of passing/failing tests;
- failure names relevant to the feature.

After implementation, rerun the same command and compare. Fix new failures caused by the change, or document why a changed expectation is intentional.

## `.test` Conversion Test DSL

`.test` files live in `modules/test/src/main/resources` and are parsed by `TestsParser.java`, `TestGroup.java`, `TestCase.java`, and `SingleTestCode.java`.

Basic shape:

```yaml
group: GroupName
    case: TestCaseName
        python:
            a = 1
        java:
            int a = 1;
        c++:
            int a = 1;
```

Rules:

- `group:` and `case:` names are word-like identifiers parsed with regexes; keep names simple. Both headers accept optional JSON-valued configuration overrides: `group[translationUnitMode="full"]: EntryPoint` or `case[skipErrors=false]: Recovery`. A case overrides the same option inherited from its group.
- Inside a case, add `ignore <source> -> <target>;` to omit exactly that conversion direction from generated checks, for example `ignore python -> java;`. Use it only for known, intentional gaps; it does not suppress other directions.
- Each language block is introduced by `<language>:` or by a prefixed form: `main <language>:`, `alt <language>:`, `isolated <language>:`.
- Supported language names in current tests are `java`, `python`, and `c++`.
- At least two language blocks are normally needed for a useful conversion case.
- `main` marks the single source block for the case. Tests are generated as `(main -> target) == target` for every other language group.
- Without `main`, the framework generates language permutations and checks `source == (target -> source)`.
- `alt` provides alternative acceptable outputs for the same language. If a language uses `alt`, keep all non-main blocks for that language as `alt` blocks.
- `isolated` marks code that should not be used as a source for conversion when no `main` exists. Use it for language-specific forms that can be compared as targets but should not be translated from.
- Avoid comparing two alternative groups or using alternatives on both sides; `TestCombinator` removes meaningless combinations.

Formatting expectations:

- `CodeFormatter` normalizes CRLF, tabs to 4 spaces, blank lines, and leading/trailing whitespace for comparison.
- Java and C++ are not indentation-sensitive; Python is indentation-sensitive.
- Even with normalization, keep internal spacing stable within lines. Do not rely on the formatter to forgive arbitrary spacing changes around tokens.
- Use one blank line between language blocks and two blank lines between cases/groups as a readability convention.

## Tree-Sitter Guidance

For parser or generator changes, create or reuse a tiny representative code snippet and inspect its tree-sitter tree before mapping it to MeaningTree nodes. This is especially important for ambiguous syntax, declarations, type annotations, loops, pattern-like constructs, and language-specific edge cases.

## CLI Notes

The CLI entry point is `org.vstu.meaningtree.Main` in `modules/application`. Build the shaded CLI jar with:

```shell
mvn -pl modules/application -am package
```

Run it with:

```shell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar <command> [options]
```

Use the CLI for quick conversion/serialization checks when that is faster than writing a full test, but still add or update `.test` cases for durable conversion behavior.
