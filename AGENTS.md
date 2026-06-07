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

- `group:` and `case:` names are word-like identifiers parsed with regexes; keep names simple.
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

```powershell
mvn -pl modules/application -am package
```

Run it with:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar <command> [options]
```

Use the CLI for quick conversion/serialization checks when that is faster than writing a full test, but still add or update `.test` cases for durable conversion behavior.
