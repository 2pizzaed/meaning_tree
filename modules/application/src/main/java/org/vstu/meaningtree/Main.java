package org.vstu.meaningtree;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.apache.jena.rdf.model.Model;
import org.vstu.meaningtree.languages.LanguageTranslator;
import org.vstu.meaningtree.languages.SourceMapGenerator;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.serializers.json.JsonSerializer;
import org.vstu.meaningtree.serializers.json.JsonTypeHierarchyBuilder;
import org.vstu.meaningtree.serializers.model.IOAlias;
import org.vstu.meaningtree.serializers.model.IOAliases;
import org.vstu.meaningtree.serializers.rdf.RDFSerializer;
import org.vstu.meaningtree.serializers.xml.XMLSerializer;
import org.vstu.meaningtree.utils.tokens.Token;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class Main {

    public enum TranslatorMode {
        simple,
        full,
        expression;

        public Map.Entry<String, String> getConfigEntry() {
            if (this.equals(simple)) {
                return Map.entry("translationUnitMode", "false");
            } else if (this.equals(expression)) {
                return Map.entry("expressionMode", "true");
            }
            return Map.entry("translationUnitMode", "true");
        }
    }

    @Parameters(commandDescription = "Translate code between programming languages")
    public static class TranslateCommand {
        @Parameter(names = "--prettify", description = "Prettify serializer output")
        private boolean prettify = false;

        @Parameter(names = "--source-map", description = "Output source map instead code")
        private boolean outputSourceMap = false;

        @Parameter(names = "--mode", description = "Translator mode (expression, simple, full)")
        private TranslatorMode translatorMode = TranslatorMode.full;

        @Parameter(names = "--start-node-id", description = "Start id for nodes")
        private long startNodeId = 0;

        @Parameter(names = "--start-token-id", description = "Start id for tokens")
        private long startTokenId = 0;

        @Parameter(names = "--from", description = "Source language", required = true)
        private String fromLanguage;

        @Parameter(names = "--to", description = "Target language")
        private String toLanguage;

        @Parameter(names = "--tokenize", description = "Tokenize target source code / meaning tree")
        private boolean performTokenize = false;

        @Parameter(names = "--save-bytes", description = "Save byte positions in meaning tree")
        private boolean saveBytes = false;

        @Parameter(names = "--tokenize-noconvert", description = "Tokenize target source code / meaning tree without convertation to other language")
        private boolean performOriginTokenize = false;

        @Parameter(names = "--detailed-tokens", description = "Make tokens with additional information (for expressions)")
        private boolean detailedTokens = false;

        @Parameter(names = "--serialize", description = "Serialization format: json, rdf, rdf-turtle")
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

        public boolean performTokenize() {
            return performTokenize;
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
            new IOAlias<>("rdf", (node, pretty) -> serializeRdf(node, "RDF/XML")),
            new IOAlias<>("rdf-turtle", (node, pretty) -> serializeRdf(node, "TTL"))
    ));

    public static void main(String[] args) throws Exception {
        TranslateCommand translateCommand = new TranslateCommand();
        ListLangsCommand listLangsCommand = new ListLangsCommand();
        NodeHierarchyCommand nodeHierarchyCommand = new NodeHierarchyCommand();

        JCommander jc = JCommander.newBuilder()
                .addCommand("translate", translateCommand)
                .addCommand("list-langs", listLangsCommand)
                .addCommand("node-hierarchy", nodeHierarchyCommand)
                .build();

        jc.parse(args);

        String parsed = jc.getParsedCommand();
        if ("list-langs".equals(parsed)) {
            listSupportedLanguages();
        } else if ("translate".equals(parsed)) {
            runTranslation(translateCommand);
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
        Map<String, Object> config = Map.ofEntries(cmd.translatorMode.getConfigEntry(),
                Map.entry("bytePositionsAnnotate", cmd.saveBytes));
        LanguageTranslator fromTranslator =
                translators.get(fromLanguage).getDeclaredConstructor(Map.class).newInstance(config);
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
                    translators.get(toLanguage.toLowerCase()).getDeclaredConstructor(Map.class).newInstance(config);

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
