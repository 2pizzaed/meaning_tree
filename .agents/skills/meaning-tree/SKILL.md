---
name: meaning-tree
description: Work with the Meaning Tree Java project as a whole. Use when Codex needs to study or modify project architecture, modules, translators, semantic AST nodes, serializers, tests, Maven build, or the command-line application; run, debug, explain, or generate MeaningTree CLI commands; inspect local Maven source jars for project or dependency sources; or optionally consult external MeaningTree documentation only when the user explicitly asks for docs.
---

# Meaning Tree Project

## Purpose

Meaning Tree is a Java library and console application for parsing, analyzing, serializing, and translating source code through a common semantic/universal AST representation called a meaning tree.

Use this skill for both project-wide code investigation and CLI work. Treat the CLI as one entry point into the broader Maven project, not as the whole project.

## Default Workflow

1. For general project questions, load `references/project.md` first to choose the relevant modules, packages, and search strategy.
2. For CLI usage, load `references/cli.md` before constructing commands or explaining command behavior.
3. Inspect repository sources directly. Do not answer architecture or behavior questions from memory when relevant source files are available.
4. Also locate relevant `*-sources.jar` files in the local Maven repository (`~/.m2/repository`) when the answer depends on source code outside the currently opened files or on Maven dependencies.
5. Prefer local repository sources and local Maven source jars over web content.
6. Do not browse or clone documentation unless the user explicitly asks for project documentation.
7. If the user explicitly asks for documentation, clone `https://github.com/CompPrehension/CompPrehension.github.io` branch `production` into a temporary directory and read only `docs/meaning_tree` from that clone.
8. When running the CLI, build the shaded application jar first if it is missing or stale.

## MCP Toolchain Server

If `compph-toolchain-server` is available as an MCP server in the current client, prefer calling its MCP tools instead of shelling out to the Meaning Tree CLI for routine translation, serialization, generation, language listing, and node hierarchy queries. The MCP server exposes the same toolchain through generated tools named `<module>__<method>`, such as `meaning-tree__translate`, `meaning-tree__generate`, `meaning-tree__list-langs`, and `meaning-tree__node-hierarchy`.

Use the CLI directly when the MCP server is not configured or running, when the task specifically asks for CLI commands, when reproducing a command-line failure, or when you need behavior not exposed by the MCP schemas. Keep `references/cli.md` as the source for CLI flags, artifact paths, and fallback/debug workflows.

## Source Jar Lookup

For project/dependency questions, find source jars locally before guessing behavior from APIs. Use PowerShell patterns like:

```powershell
Get-ChildItem -Path "$HOME\.m2\repository" -Recurse -Filter "*-sources.jar" |
  Where-Object { $_.FullName -match "meaningtree|tree-sitter|jcommander|jena|graphviz|gson" } |
  Select-Object FullName
```

To inspect a source jar without extracting it permanently:

```powershell
jar tf "<path-to-sources.jar>" | Select-String "<ClassName>|<package/path>"
jar xf "<path-to-sources.jar>" "<path/inside/jar/ClassName.java>"
Get-Content "<path/inside/jar/ClassName.java>"
```

Extract into a temporary directory if multiple files are needed. Do not modify files in `.m2`.

## External Documentation

Only when the user explicitly asks for documentation:

```powershell
$tmp = Join-Path $env:TEMP ("meaning-tree-docs-" + [guid]::NewGuid())
git clone --depth 1 --branch production https://github.com/CompPrehension/CompPrehension.github.io $tmp
Get-ChildItem -Path (Join-Path $tmp "docs\meaning_tree") -Recurse -File
```

Read only files under `docs/meaning_tree`. Remove the temporary clone when no longer needed if cleanup is appropriate.

## Key References

- `references/project.md`: project structure, module map, source investigation workflow.
- `references/cli.md`: CLI commands, options, examples, and caveats derived from `Main.java`.

## Build And Run

Use Java 21+. Build all modules:

```powershell
mvn package
```

Build the CLI application and required modules:

```powershell
mvn -pl modules/application -am package
```

Run the shaded CLI jar:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar <command> [options]
```

If the jar is absent, inspect `modules/application/target` after packaging; the shade plugin config sets `org.vstu.meaningtree.Main` as the manifest main class.
