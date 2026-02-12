package org.vstu.meaningtree;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.SourceMapGenerator;
import org.vstu.meaningtree.languages.configs.Config;
import org.vstu.meaningtree.languages.configs.ConfigBuilder;
import org.vstu.meaningtree.languages.configs.ConfigParameter;
import org.vstu.meaningtree.languages.configs.ConfigParameters;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.serializers.dot.GraphvizDotSerializer;
import org.vstu.meaningtree.serializers.json.JsonDeserializer;
import org.vstu.meaningtree.serializers.json.JsonSerializer;
import org.vstu.meaningtree.serializers.json.JsonTypeHierarchyBuilder;
import org.vstu.meaningtree.serializers.model.IOAlias;
import org.vstu.meaningtree.serializers.model.IOAliases;
import org.vstu.meaningtree.serializers.rdf.RDFDeserializer;
import org.vstu.meaningtree.serializers.rdf.RDFSerializer;
import org.vstu.meaningtree.serializers.xml.XMLDeserializer;
import org.vstu.meaningtree.serializers.xml.XMLSerializer;
import org.vstu.meaningtree.utils.tokens.Token;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class Main {

    public enum TranslatorMode {
        simple,
        full,
        expression;

        public ConfigParameter getConfigEntry() {
            if (this.equals(simple)) {
                return ConfigParameters.translationUnitMode.withValue("simple");
            } else if (this.equals(expression)) {
                return ConfigParameters.translationUnitMode.withValue("expression");
            }
            return ConfigParameters.translationUnitMode.withValue("full");
        }
    }

    @Parameters(commandDescription = "Generate code from given meaning tree representation")
    public static class GenerateCommand {
        @Parameter(names = "--source-map", description = "Output source map instead code")
        private boolean outputSourceMap = false;

        @Parameter(names = "--to", description = "Target language")
        private String toLanguage;

        @Parameter(names = "--start-token-id", description = "Start id for token id counter")
        private long startTokenId = 0;

        @Parameter(names = "--config", description = "Apply config from JSON file")
        private String configPath = null;

        @Parameter(names = "--input-type", description = "Type of serialized object: meaning-tree, node")
        private String type = "meaning-tree";

        @Parameter(names = "--format", description = "Serialization format of input file: json, xml, rdf, rdf-turtle")
        private String serializeFormat = "json";

        @Parameter(description = "<input_file> [output_file]", required = true)
        private java.util.List<String> positionalParams;

        @Parameter(names = "--mode", description = "Translator mode (expression, short, full)")
        private TranslatorMode translatorMode = TranslatorMode.full;

        @Parameter(names = "--tokenize", description = "Tokenize target source code / meaning tree (convert output will be ignored)")
        private boolean performTokenize = false;

        @Parameter(names = "--detailed-tokens", description = "Make tokens (if tokenize option is selected) with additional information (for expressions)")
        private boolean detailedTokens = false;

        @Parameter(names = "--prettify", description = "Prettify serializer output")
        private boolean prettify = false;

        public String getToLanguage() {
            return toLanguage;
        }

        public String getSerializeFormat() {
            return serializeFormat;
        }

        public String getInputFile() {
            return positionalParams.getFirst();
        }

        public String getOutputFile() {
            return positionalParams.size() > 1 ? positionalParams.get(1) : "-";
        }

        public boolean isNode() {
            return type.equals("node");
        }
    }

    @Parameters(commandDescription = "Translate code between programming languages")
    public static class TranslateCommand {
        @Parameter(names = "--prettify", description = "Prettify serializer output")
        private boolean prettify = false;

        @Parameter(names = "--source-map", description = "Output source map instead code")
        private boolean outputSourceMap = false;

        @Parameter(names = "--mode", description = "Translator mode (expression, short, full)")
        private TranslatorMode translatorMode = TranslatorMode.full;

        @Parameter(names = "--start-node-id", description = "Start id for node id counter")
        private long startNodeId = 0;

        @Parameter(names = "--start-token-id", description = "Start id for token id counter")
        private long startTokenId = 0;

        @Parameter(names = "--from", description = "Source language", required = true)
        private String fromLanguage;

        @Parameter(names = "--to", description = "Target language")
        private String toLanguage;

        @Parameter(names = "--tokenize", description = "Tokenize target source code / meaning tree (convert output will be ignored)")
        private boolean performTokenize = false;

        @Parameter(names = "--save-bytes", description = "Save byte positions in meaning tree")
        private boolean saveBytes = false;

        @Parameter(names = "--tokenize-noconvert", description = "Tokenize target source code without conversion to other language")
        private boolean performOriginTokenize = false;

        @Parameter(names = "--detailed-tokens", description = "Make tokens (if tokenize option is selected) with additional information (for expressions)")
        private boolean detailedTokens = false;

        @Parameter(names = "--config", description = "Apply config from JSON file")
        private String configPath = null;

        @Parameter(names = "--serialize", description = "Serialization format: json, xml, rdf, rdf-turtle, dot")
        private String serializeFormat;

        @Parameter(description = "<input_file> [output_file]", required = true)
        private java.util.List<String> positionalParams;

        public String getFromLanguage() {
            return fromLanguage;
        }

        public String getToLanguage() {
            return toLanguage;
        }

        public String getSerializeFormat() {
            return serializeFormat;
        }

        public String getInputFile() {
            return positionalParams.getFirst();
        }

        public String getOutputFile() {
            return positionalParams.size() > 1 ? positionalParams.get(1) : "-";
        }
    }

    @Parameters(commandDescription = "List all supported languages")
    public static class ListLangsCommand {}

    @Parameters(commandDescription = "List all supported nodes and their parents")
    public static class NodeHierarchyCommand {
        @Parameter(names = "--prettify", description = "Prettify output")
        private boolean prettify = false;
    }

    public static Map<String, Class<? extends LanguageTranslator>> translators =
            SupportedLanguage.getStringMap();

    private static String serializeRdf(Serializable node, String format) {
        Model model = new RDFSerializer().serialize(node);
        StringWriter writer = new StringWriter();
        model.write(writer, format);
        return writer.toString();
    }

    private static Serializable deserializeRdf(String text, boolean isNode, String format) {
        Model model = ModelFactory.createDefaultModel();
        model.read(new StringReader(text), null, format);
        return isNode ? new RDFDeserializer().deserialize(model) : new RDFDeserializer().deserializeTree(model);
    }

    private static final IOAliases<BiFunction<Serializable, Boolean, String>> serializers = new IOAliases<>(List.of(
            new IOAlias<>("json", (node, pretty) -> {
                JsonObject json = new JsonSerializer().serialize(node);
                var builder = new GsonBuilder().disableHtmlEscaping();
                if (pretty) {
                    builder = builder.setPrettyPrinting();
                }
                return builder.create().toJson(json);
            }),
            new IOAlias<>("xml", (node, pretty) -> new XMLSerializer(pretty).serialize(node)),
            new IOAlias<>("dot", (node, pretty) -> new GraphvizDotSerializer().serialize(node)),
            new IOAlias<>("rdf", (node, pretty) -> serializeRdf(node, "RDF/XML")),
            new IOAlias<>("rdf-turtle", (node, pretty) -> serializeRdf(node, "TTL"))
    ));

    private static final IOAliases<BiFunction<String, Boolean, Serializable>> deserializers = new IOAliases<>(List.of(
            new IOAlias<>("json", (text, node) -> node ?
                    new JsonDeserializer().deserialize(JsonParser.parseString(text).getAsJsonObject()) :
                    new JsonDeserializer().deserializeTree(JsonParser.parseString(text).getAsJsonObject())
            ),
            new IOAlias<>("xml", (text, node) -> node ?
                    new XMLDeserializer().deserialize(text) : new XMLDeserializer().deserializeTree(text)),
            new IOAlias<>("rdf", (text, node) -> deserializeRdf(text, node, "RDF/XML")),
            new IOAlias<>("rdf-turtle", (text, node) -> deserializeRdf(text, node, "TTL"))
    ));

    public static void main(String[] args) throws Exception {
        TranslateCommand translateCommand = new TranslateCommand();
        ListLangsCommand listLangsCommand = new ListLangsCommand();
        GenerateCommand generateCommand = new GenerateCommand();
        NodeHierarchyCommand nodeHierarchyCommand = new NodeHierarchyCommand();

        JCommander jc = JCommander.newBuilder()
                .addCommand("translate", translateCommand)
                .addCommand("generate", generateCommand)
                .addCommand("list-langs", listLangsCommand)
                .addCommand("node-hierarchy", nodeHierarchyCommand)
                .build();

        jc.parse(args);

        String parsed = jc.getParsedCommand();
        if ("list-langs".equals(parsed)) {
            listSupportedLanguages();
        } else if ("translate".equals(parsed)) {
            runTranslation(translateCommand);
        } else if ("generate".equals(parsed)) {
            runGeneration(generateCommand);
        } else if ("node-hierarchy".equals(parsed)) {
            viewHierarchy(nodeHierarchyCommand);
        } else {
            jc.usage();
        }
    }

    private static void viewHierarchy(NodeHierarchyCommand nodeHierarchyCommand) {
        var builder = new GsonBuilder().disableHtmlEscaping();
        if (nodeHierarchyCommand.prettify) {
            builder = builder.setPrettyPrinting();
        }
        writeOutput(builder.create().toJson(JsonTypeHierarchyBuilder.generateHierarchyJsonObject()), "-");
    }

    private static void listSupportedLanguages() {
        System.out.println("Supported languages: " + String.join(", ", translators.keySet()));
    }

    private static void runGeneration(GenerateCommand cmd) throws Exception {
        String toLanguage = cmd.getToLanguage();
        String inputFilePath = cmd.getInputFile();
        String outputFilePath = cmd.getOutputFile();
        String serializeFormat = cmd.getSerializeFormat();

        // Validate that either --to or --serialize is specified
        if (toLanguage == null) {
            System.err.println("Target language must be specified");
            return;
        }

        if (cmd.performTokenize && cmd.isNode()) {
            System.err.println("Meaning Tree must be specified for tokenizer");
        }

        if (!translators.containsKey(toLanguage)) {
            System.err.println("Unsupported target language: " + toLanguage + ". Supported languages: " + translators.keySet());
            return;
        }

        Config config = new Config(cmd.translatorMode.getConfigEntry());
        if (cmd.configPath != null) {
            JsonElement element = JsonParser.parseString(Files.readString(Path.of(cmd.configPath)));
            Config jsonConfig = new ConfigBuilder().fromJson(translators.get(toLanguage), element.getAsJsonObject()).toConfig();
            config = config.merge(jsonConfig);
        }

        String code = readCode(inputFilePath);
        LanguageTranslator toTranslator =
                translators.get(toLanguage).getDeclaredConstructor(Config.class).newInstance(config);
        var object = deserializers.apply(serializeFormat, function -> function.apply(code, cmd.isNode()));
        if (object.isEmpty()) {
            System.err.println("Unknown serialization format: " + serializeFormat + ". " + deserializers.getSupportedFormatsMessage());
        }
        var target = object.get();
        if (cmd.isNode()) {
            if (cmd.outputSourceMap) {
                SourceMapGenerator srcMapGen = new SourceMapGenerator(toTranslator);
                var srcMap = srcMapGen.process((Node) target);
                serializers.apply("json", function -> function.apply(srcMap, cmd.prettify))
                        .ifPresentOrElse(
                                result -> writeOutput(result, outputFilePath),
                                () -> System.err.println("Unknown serialization error")
                        );
            } else {
                String translatedCode = toTranslator.getCode((Node) target);
                writeOutput(translatedCode, outputFilePath);
            }
        } else {
            if (cmd.outputSourceMap) {
                SourceMapGenerator srcMapGen = new SourceMapGenerator(toTranslator);
                var srcMap = srcMapGen.process((MeaningTree) target);
                serializers.apply("json", function -> function.apply(srcMap, cmd.prettify))
                        .ifPresentOrElse(
                                result -> writeOutput(result, outputFilePath),
                                () -> System.err.println("Unknown serialization error")
                        );
            } else if (cmd.performTokenize) {
                Token.setupId(cmd.startTokenId);
                var tokens = toTranslator.getCodeAsTokens((MeaningTree) target, true, cmd.detailedTokens, false);
                serializers.apply(serializeFormat == null ? "json" : serializeFormat, function -> function.apply(tokens, cmd.prettify))
                        .ifPresentOrElse(
                                result -> writeOutput(result, outputFilePath),
                                () -> System.err.println("Unknown serialization format: " + serializeFormat + ". " + serializers.getSupportedFormatsMessage())
                        );
            } else {
                String translatedCode = toTranslator.getCode((MeaningTree) target);
                writeOutput(translatedCode, outputFilePath);
            }
        }
    }

    private static void runTranslation(TranslateCommand cmd) throws Exception {
        Node.setupId(cmd.startNodeId);

        String fromLanguage = cmd.getFromLanguage().toLowerCase();
        String toLanguage = cmd.getToLanguage();
        String inputFilePath = cmd.getInputFile();
        String outputFilePath = cmd.getOutputFile();
        String serializeFormat = cmd.getSerializeFormat();

        // Validate that either --to or --serialize is specified
        if (toLanguage == null && serializeFormat == null && !cmd.performOriginTokenize) {
            System.err.println("Either --to (target language) or --serialize (format) must be specified");
            return;
        }

        if (!translators.containsKey(fromLanguage)) {
            System.err.println("Unsupported source language: " + fromLanguage + ". Supported languages: " + translators.keySet());
            return;
        }

        if (toLanguage != null && !translators.containsKey(toLanguage.toLowerCase())) {
            System.err.println("Unsupported target language: " + toLanguage + ". Supported languages: " + translators.keySet());
            return;
        }

        // Read source code (from file or stdin)
        String code = readCode(inputFilePath);

        // Instantiate source-language translator
        Config fromConfig = new Config(cmd.translatorMode.getConfigEntry(),
                ConfigParameters.bytePositionAnnotations.withValue(cmd.saveBytes));
        Config toConfig = fromConfig.clone();
        if (cmd.configPath != null) {
            var element = JsonParser.parseString(Files.readString(Path.of(cmd.configPath))).getAsJsonObject();
            var fromTranslatorClass = translators.get(fromLanguage);
            var toTranslatorClass = translators.get(toLanguage);
            JsonObject toJson = new JsonObject();
            JsonObject fromJson = new JsonObject();
            for (String key : element.keySet()) {
                if (ConfigParameters.exists(fromTranslatorClass, key)) {
                    fromJson.add(key, element.get(key));
                } else if  (ConfigParameters.exists(toTranslatorClass, key)) {
                    toJson.add(key, element.get(key));
                }
            }
            fromConfig = fromConfig.merge(new ConfigBuilder().fromJson(fromTranslatorClass, fromJson).toConfig());
            toConfig = toConfig.merge(new ConfigBuilder().fromJson(toTranslatorClass, toJson).toConfig());
        }

        LanguageTranslator fromTranslator =
                translators.get(fromLanguage).getDeclaredConstructor(Config.class).newInstance(fromConfig);
        var meaningTree = fromTranslator.getMeaningTree(code);
        final var rootNode = meaningTree.getRootNode();

        // Handle serialization if requested
        if (serializeFormat != null) {
            serializers.apply(serializeFormat, function -> function.apply(rootNode, cmd.prettify))
                    .ifPresentOrElse(
                            result -> writeOutput(result, outputFilePath),
                            () -> System.err.println("Unknown serialization format: " + serializeFormat + ". " + serializers.getSupportedFormatsMessage())
                    );
            return;
        }

        // Instantiate target-language translator and generate code
        if (cmd.performOriginTokenize) {
            Token.setupId(cmd.startTokenId);
            var tokens = fromTranslator.getCodeAsTokens(meaningTree, true, cmd.detailedTokens, false);
            serializers.apply(serializeFormat == null ? "json" : serializeFormat, function -> function.apply(tokens, cmd.prettify))
                    .ifPresentOrElse(
                            result -> writeOutput(result, outputFilePath),
                            () -> System.err.println("Unknown serialization format: " + serializeFormat + ". " + serializers.getSupportedFormatsMessage())
                    );
        } else if (toLanguage != null) {
            LanguageTranslator toTranslator =
                    translators.get(toLanguage.toLowerCase()).getDeclaredConstructor(Map.class).newInstance(toConfig);

            if (cmd.outputSourceMap && cmd.performTokenize) {
                System.err.println("Source map building and tokenizing are both required. Defaulting to using only `outputSourceMap`");
            }

            if (cmd.outputSourceMap) {
                SourceMapGenerator srcMapGen = new SourceMapGenerator(toTranslator);
                var srcMap = srcMapGen.process(meaningTree);
                serializers.apply("json", function -> function.apply(srcMap, cmd.prettify))
                        .ifPresentOrElse(
                                result -> writeOutput(result, outputFilePath),
                                () -> System.err.println("Unknown serialization error")
                        );
            } else if (cmd.performTokenize) {
                Token.setupId(cmd.startTokenId);
                var tokens = toTranslator.getCodeAsTokens(meaningTree, true, cmd.detailedTokens, false);
                serializers.apply(serializeFormat == null ? "json" : serializeFormat, function -> function.apply(tokens, cmd.prettify))
                        .ifPresentOrElse(
                                result -> writeOutput(result, outputFilePath),
                                () -> System.err.println("Unknown serialization format: " + serializeFormat + ". " + serializers.getSupportedFormatsMessage())
                        );
            } else {
                String translatedCode = toTranslator.getCode(meaningTree);
                writeOutput(translatedCode, outputFilePath);
            }
        }
    }

    private static void writeOutput(String content, String outputFilePath) {
        try {
            if ("-".equals(outputFilePath)) {
                System.out.println(content);
            } else {
                try (PrintWriter out = new PrintWriter(new FileWriter(outputFilePath, StandardCharsets.UTF_8, false))) {
                    out.print(content);
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing output: " + e.getMessage());
        }
    }

    private static String readCode(String filePath) throws IOException {
        if ("-".equals(filePath)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int c;
            while ((c = System.in.read()) != -1) {
                buffer.write(c);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        } else {
            return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)), StandardCharsets.UTF_8);
        }
    }
}
