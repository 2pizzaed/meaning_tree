# Conversion Test Baseline And `.test` DSL

## Baseline Workflow

Before a feature that may affect conversion tests:

```powershell
mvn -pl modules/test -am test
```

Capture at least:

- total tests run;
- number of passing/failing tests;
- failure names relevant to the feature.

After implementation, rerun the same command and compare. Fix new failures caused by the change, or document why a changed expectation is intentional.

This baseline is temporary: not all conversion tests currently pass, so compare counts and failures to identify new regressions instead of assuming a clean baseline.

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
