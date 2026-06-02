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
--mode <mode>                Translator mode. Actual enum values: simple, full, expression. Default: full.
--start-node-id <number>     Initial node id counter. Default: 0.
--start-token-id <number>    Initial token id counter. Default: 0.
--tokenize                   Tokenize target source code / meaning tree; normal conversion output is ignored.
--save-bytes                 Save byte positions in meaning tree annotations.
--tokenize-noconvert         Tokenize original source without translating to another language.
--detailed-tokens            Include extra token details, mainly useful for expressions.
--config <json>              Apply translator config from JSON. Keys are split between source and target translators if supported.
--serialize <format>         Serialization format: json, xml, rdf, rdf-turtle, dot.
```

Important behavior:

```text
If --serialize is present, translate serializes the parsed root node and returns immediately.
If --source-map and --tokenize are both present, source-map wins after printing a warning.
For token output, the code uses JSON unless a serializer format is otherwise active.
For --config, do not pass --config without --to unless you verified code paths; Main.java reads the target translator class while splitting config.
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
```

## generate

Purpose: read a serialized meaning tree or node and generate target-language code, target tokens, or a source map.

Syntax:

```powershell
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to <language> [options] <input_file> [output_file]
```

Required:

```text
--to <language>  Target language. One of: java, python, c++.
```

Options:

```text
--source-map                 Output source map JSON instead of code.
--start-token-id <number>    Initial token id counter. Default: 0.
--config <json>              Apply target translator config from JSON.
--input-type <type>          Serialized object type: meaning-tree, node. Default: meaning-tree.
--format <format>            Input serialization format: json, xml, rdf, rdf-turtle. Default: json.
--mode <mode>                Translator mode. Actual enum values: simple, full, expression. Default: full.
--tokenize                   Tokenize target source code / meaning tree; normal conversion output is ignored.
--detailed-tokens            Include extra token details, mainly useful for expressions.
--prettify                   Pretty-print serializer output where applicable.
```

Important behavior:

```text
--to is always required, even for --source-map or --tokenize.
--input-type node with --tokenize prints an error because tokenization requires a full meaning tree.
--format controls deserialization. For token output, the same format is used if supported; otherwise JSON is the safer expectation.
```

Examples:

```powershell
# Generate Java from a serialized meaning tree JSON
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to java --format json tree.json Example.java

# Generate Python from an XML meaning tree
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to python --format xml tree.xml out.py

# Generate code from a serialized node, not a whole meaning tree
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to java --input-type node --format json node.json

# Generate source map from a meaning tree
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to c++ --source-map --prettify tree.json source-map.json

# Tokenize generated target code from a meaning tree
java -jar modules/application/target/application-1.0-SNAPSHOT.jar generate --to python --tokenize --detailed-tokens --prettify tree.json tokens.json
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
The --mode description says "expression, short, full", but the enum is simple/full/expression. Use simple, not short.
translate --serialize serializes the root node, not the whole meaning tree.
generate --input-type defaults to meaning-tree; use node only when the serialized input is a single Node.
node-hierarchy always writes to stdout.
```
