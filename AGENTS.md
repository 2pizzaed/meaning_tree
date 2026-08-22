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
- For conversion-related changes, record the language conversion test status before and after the change — see [docs/references/conversion-tests.md](docs/references/conversion-tests.md).
- Prefer source-based investigation over guessing. Use `rg` to find relevant nodes, translators, serializers, configs, and tests.
- When project structure matters, request or generate a project tree before choosing modules/packages or planning broad changes.
- When implementing parser/conversion behavior, inspect the actual tree-sitter parse tree for the concrete source snippet. Use the grammar already wired into the relevant language module when possible; otherwise use a known external tree-sitter grammar/tool if needed. Do not infer tree-sitter node shapes from intuition.
- If a question depends on dependency or project source that is not open in the repository, inspect local Maven `*-sources.jar` files in `~/.m2/repository` before relying on assumptions.

## Adding And Changing MeaningTree Node Types

A new or changed node type is not complete until serializers/deserializers, `@TreeNode` annotations, remapping, and traversal are all handled correctly — see the full checklist in [docs/references/node-types.md](docs/references/node-types.md).

## Temporary And Durable Tests

- Use a temporary directory for quick experiments, one-off regression probes, and small programs written only to inspect tree-sitter parser behavior. If temporarily placed under a module's test directory because that is the easiest way to use its classpath or grammar, remove the probe after the investigation.
- Keep a test in the repository's normal test directory when it thoroughly exercises a complex feature or module from all relevant aspects and provides durable regression coverage.
- For a small test that is unlikely to remain useful, remove it after the work or leave it only in a temporary directory; do not turn exploratory probes into permanent test-suite clutter.
- Clean up transient test files and temporary test directories before handing off the work unless they intentionally remain in an ignored temporary location for continued investigation.

## Conversion Tests

Before/after baseline workflow and the full `.test` DSL reference (groups, cases, `main`/`alt`/`isolated` blocks, formatting rules) live in [docs/references/conversion-tests.md](docs/references/conversion-tests.md).

## Tree-Sitter Guidance

For parser or generator changes, create or reuse a tiny representative code snippet and inspect its tree-sitter tree before mapping it to MeaningTree nodes. This is especially important for ambiguous syntax, declarations, type annotations, loops, pattern-like constructs, and language-specific edge cases.

## CLI Notes

Build/run instructions for the CLI entry point (`org.vstu.meaningtree.Main`) live in [docs/references/cli.md](docs/references/cli.md).
