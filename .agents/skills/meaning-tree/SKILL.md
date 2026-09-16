---
name: meaning-tree
description: Work with the MeaningTree project through its command-line interface, configured MCP toolchain, public Java APIs, and implementation sources. Use when Codex needs to translate source code, serialize or deserialize meaning trees, generate code, tokens, source maps, language lists, or node hierarchies; understand project architecture; explain or troubleshoot MeaningTree behavior; inspect the implementation when documentation is missing or insufficient; or consult external MeaningTree documentation when explicitly requested. Do not use this skill as a development guide for modifying the MeaningTree repository.
---

# Meaning Tree Project

## Purpose

Meaning Tree is a Java library and console application for parsing, analyzing, serializing, and translating source code through a common semantic/universal AST representation called a meaning tree.

Use this skill to operate and understand MeaningTree through its public interfaces and implementation. Repository development conventions belong exclusively in the repository's `AGENTS.md` and are outside this skill's scope.

## Default Workflow

1. Prefer the configured MCP toolchain for supported operations.
2. For direct CLI usage, load `references/cli.md` before constructing commands or explaining behavior.
3. Use an existing MeaningTree CLI installation or runnable jar. If its location is unknown, ask the user for the executable or jar path.
4. When public documentation or observed CLI/MCP behavior is insufficient, load `references/project.md` and inspect the implementation read-only to determine the actual external contract or behavior.
5. Do not modify internal project source, prescribe implementation changes or tests, or provide repository development conventions under this skill. Follow the applicable `AGENTS.md` if the user's task is repository development.
6. Do not browse or clone documentation unless the user explicitly asks for MeaningTree documentation.
7. If the user explicitly asks for documentation, clone `https://github.com/CompPrehension/CompPrehension.github.io` branch `production` into a temporary directory and read only `docs/meaning_tree` from that clone.

## MCP Toolchain Server

Before choosing how to run an operation, inspect the tools available in the current agent client. Treat the MCP server as available only when its MeaningTree tools appear in that tool list; a configured URL, a running server process, or a reachable `/mcp` endpoint alone is not sufficient. Raw server tool names are `meaning-tree__translate`, `meaning-tree__generate`, `meaning-tree__list-langs`, and `meaning-tree__node-hierarchy`, but clients may add a server prefix or normalize the names.

When the tools are present, prefer them for routine translation, serialization, generation, language listing, and node hierarchy queries. Read the selected tool's input schema before calling it because MCP parameter names and payload shapes may differ from CLI flags.

When the tools are absent, do not use `curl`, hand-written JSON-RPC, or another HTTP client as a substitute for an MCP tool during routine MeaningTree operations. Use the CLI fallback instead. Direct transport-level requests are appropriate only when the task explicitly asks to test or diagnose the MCP server itself.

Use the CLI directly when the MCP server is not configured or running, when the task specifically asks for CLI commands, when reproducing a command-line failure, or when an operation is not exposed by the MCP schemas. Keep `references/cli.md` as the source for CLI flags, artifact paths, and usage caveats.

## External Documentation

Only when the user explicitly asks for documentation, clone the `production` branch of `https://github.com/CompPrehension/CompPrehension.github.io` into a temporary directory. Read only `docs/meaning_tree` and remove the clone when it is no longer needed.

## Key References

- `references/project.md`: read-only implementation map and source-investigation workflow for resolving external usage questions.
- `references/cli.md`: CLI commands, options, examples, and caveats derived from `Main.java`.
