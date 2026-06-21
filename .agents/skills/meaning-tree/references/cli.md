# MeaningTree CLI Reference

Derived from `modules/application/src/main/java/org/vstu/meaningtree/Main.java`.

## Commands

```text
translate       Translate code between programming languages
generate        Generate code from a serialized meaning tree or node
list-langs      List all supported languages
node-hierarchy  List all supported nodes and their parents
```

Supported languages are defined in `SupportedLanguage.java`:

```text
java
python
c++
```

Use `list-langs` to confirm at runtime.

## Translator Modes

The `--mode` flag controls how program entry points are handled during parsing and code generation. Enum values: `expression`, `simple`, `procedural`, `full` (default).

```text
expression   Single expressions only, no statements or program structure.
simple       Strips entry point wrappers (Java main class+method, C++ main function,
             Python if-__name__ guard) and uses only the body statements.
procedural   Strips class wrappers but preserves top-level functions. For Java, converts
             class-wrapped code to procedural form (functions become standalone).
             A middle ground between simple and full.
full         Preserves the complete program structure: entry points, class wrappers,
             main functions, if-__name__ guards. When generating code to a language
             that requires entry point structure, creates one if missing.
```

## Common IO Rules

Input and output positional arguments use this shape:

```text
<input_file> [output_file]
```

`input_file` can be `-` to read UTF-8 text from stdin.

`output_file` defaults to `-`, which writes to stdout.

Files are read and written as UTF-8.

## translate

Purpose: parse source code from one supported language into a meaning tree, then either serialize the tree, translate it to another language, tokenize it, or generate a source map.

Syntax:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from <language> [--to <language>] [options] <input_file> [output_file]
```

Required:

```text
--from <language>  Source language. One of: java, python, c++.
```

At least one of these must be present:

```text
--to <language>              Target language. One of: java, python, c++.
--serialize <format>         Serialize parsed root node instead of generating code.
--tokenize-noconvert         Tokenize original source language without conversion.
```

Options:

```text
--prettify                   Pretty-print serializer output.
--source-map                 Output source map JSON instead of generated code.
--mode <mode>                Translator mode: expression, simple, procedural, full. Default: full.
--start-node-id <number>     Initial node id counter. Default: 0.
--start-token-id <number>    Initial token id counter. Default: 0.
--tokenize                   Tokenize target source code / meaning tree; normal conversion output is ignored.
--save-bytes                 Save byte positions in meaning tree annotations.
--tokenize-noconvert         Tokenize original source without translating to another language.
--detailed-tokens            Include extra token details, mainly useful for expressions.
--config <json>              Apply translator config from JSON. Keys are split between source and target translators if supported.
--skip-errors                Allow translator/parser to skip recoverable errors unless overridden by --config.
--serialize <format>         Serialization format: json, xml, rdf, rdf-turtle, dot.
--project <value>            Project source context as <projectRoot><pathSeparator><currentFileRelPath>.
```

Important behavior:

```text
If --serialize is present, translate serializes the parsed root node and returns immediately.
If --source-map and --tokenize are both present, source-map wins after printing a warning.
For token output, the code uses JSON unless a serializer format is otherwise active.
--skip-errors is enabled first in the base translator config and can still be overridden by explicit values inside --config.
--project is applied only to the source translator in translate mode.
--project must be passed as <projectRoot><pathSeparator><currentFileRelPath>, where <pathSeparator> is the platform path separator from Java File.pathSeparator.
On Windows, use `;` inside --project. On Unix-like systems, use `:`.
For --config, passing config without --to is supported; Main.java splits keys only for translators that are actually present.
```

Examples:

```powershell
# Translate Python to Java, stdout
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from python --to java example.py

# Translate Java to Python, write file
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from java --to python Example.java out.py

# Serialize a C++ root node as pretty JSON
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from c++ --serialize json --prettify main.cpp tree.json

# Serialize a Java root node as Graphviz DOT
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from java --serialize dot Example.java tree.dot

# Tokenize the original Python source without conversion
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from python --tokenize-noconvert --detailed-tokens example.py tokens.json

# Generate a source map for Python to Java
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from python --to java --source-map --prettify example.py source-map.json

# Parse Java with project context on Windows
java -jar modules/application/target/application-1.0-SNAPSHOT.jar translate --from java --to python --project "D:\work\demo;src\main\java\demo\Main.java" src\main\java\demo\Main.java out.py
```

## generate

Purpose: read a serialized meaning tree or node and generate target-language code, re-serialize to another format, produce target tokens, or generate a source map.

Syntax:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate [--to <language>] [options] <input_file> [output_file]
```

At least one of these must be present:

```text
--to <language>              Target language. One of: java, python, c++.
--serialize <format>         Re-serialize the deserialized tree/node to another output format.
```

Options:

```text
--source-map                 Output source map instead of code. Requires --to.
--start-token-id <number>    Initial token id counter. Default: 0.
--config <json>              Apply target translator config from JSON.
--skip-errors                Allow translator/parser to skip recoverable errors unless overridden by --config.
--input-type <type>          Serialized object type: meaning-tree, node. Default: meaning-tree.
--format <format>            Input serialization format: json, xml, rdf, rdf-turtle. Default: json.
--serialize <format>         Output serialization format: json, xml, rdf, rdf-turtle, dot.
--mode <mode>                Translator mode: expression, simple, procedural, full. Default: full.
--tokenize                   Tokenize target source code / meaning tree; normal conversion output is ignored. Requires --to.
--detailed-tokens            Include extra token details, mainly useful for expressions.
--prettify                   Pretty-print serializer output where applicable.
```

Important behavior:

```text
--serialize without --to, --source-map, or --tokenize: re-serializes the deserialized tree/node to the given output format (format conversion, e.g. json to dot).
--serialize with --source-map: controls the output format for source maps (default: json).
--serialize with --tokenize: controls the output format for tokens (default: json).
--to is required when --source-map or --tokenize is used.
--input-type node with --tokenize prints an error because tokenization requires a full meaning tree.
--skip-errors is enabled first in the base translator config and can still be overridden by explicit values inside --config.
--format controls only input deserialization; --serialize controls output serialization.
```

Examples:

```powershell
# Generate Java from a serialized meaning tree JSON
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to java --format json tree.json Example.java

# Generate Python from an XML meaning tree
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to python --format xml tree.xml out.py

# Generate code from a serialized node, not a whole meaning tree
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to java --input-type node --format json node.json

# Re-serialize a JSON meaning tree to Graphviz DOT (no target language needed)
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --serialize dot --format json --prettify tree.json tree.dot

# Re-serialize an XML meaning tree to JSON
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --serialize json --format xml tree.xml tree.json

# Generate source map from a meaning tree (output as JSON by default)
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to c++ --source-map --prettify tree.json source-map.json

# Generate source map in XML format
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to c++ --source-map --serialize xml --prettify tree.json source-map.xml

# Tokenize generated target code from a meaning tree (output as JSON by default)
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to python --tokenize --detailed-tokens --prettify tree.json tokens.json

# Tokenize with XML output format
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to python --tokenize --serialize xml tree.json tokens.xml
```

## list-langs

Syntax:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar list-langs
```

Expected output includes supported language names from `SupportedLanguage.getStringMap()`.

## node-hierarchy

Purpose: output JSON for all supported node classes and parent relationships using `JsonTypeHierarchyBuilder.generateHierarchyJsonObject()`.

Syntax:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar node-hierarchy [--prettify]
```

Note: `node-hierarchy` does not declare positional output parameters in `Main.java`; it always writes to stdout. Redirect with the shell when needed:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar node-hierarchy --prettify > hierarchy.json
```

## Serialization Formats

Serializers available from `Main.java`:

```text
json
xml
dot
rdf
rdf-turtle
```

Deserializers available from `Main.java`:

```text
json
xml
rdf
rdf-turtle
```

`dot` is output-only.

RDF format mapping:

```text
rdf         RDF/XML
rdf-turtle  TTL
```

## Known CLI Caveats

```text
The --mode description in JCommander help text says "expression, simple, procedural, full". All four values are valid.
translate --serialize serializes the root node, not the whole meaning tree.
generate --input-type defaults to meaning-tree; use node only when the serialized input is a single Node.
generate --format controls input deserialization; generate --serialize controls output serialization.
node-hierarchy always writes to stdout.
translate --project uses the Java platform path separator inside a single argument, so on Windows the separator is `;`, not `:`.
```
