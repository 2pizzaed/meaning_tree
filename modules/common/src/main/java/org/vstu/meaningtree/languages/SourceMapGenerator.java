package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.iterators.utils.NodeIterable;
import org.vstu.meaningtree.nodes.Declaration;
import org.vstu.meaningtree.nodes.Definition;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.declarations.ClassDeclaration;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.interfaces.NestedDeclaration;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.serializers.json.JsonNodeTypeClassMapper;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SourceMapGenerator {
    /***
     * Данный класс необходим для получения из Viewer не только строки исходного кода, но также разметки этого кода
     * Разметка включает в себя список айди узлов, а также их байтовая позиция в полученном коде
     */
    protected LanguageTranslator translator;
    protected ScopeTable globalScope;

    // Начальный и конечный маркеры для ID
    private static final String START_TAG = "\u2060AST_START_"; // \u2060 = word joiner (невидимый)
    private static final String END_TAG = "\u2060AST_END";

    private static final Set<Long> watermarked = new HashSet<>();

    private static final BiFunction<Node, String, String> watermarkingHook = (node, string) -> {
        long id = node.getId(); // допустим, у Node есть getId()
        if (watermarked.contains(id)) {
            return string;
        }
        var stringBuffer = new StringBuilder();
        stringBuffer.append(START_TAG);
        stringBuffer.append(id);
        stringBuffer.append(END_TAG);
        stringBuffer.append(string);
        stringBuffer.append(START_TAG);
        stringBuffer.append('/');
        stringBuffer.append(id);
        stringBuffer.append(END_TAG);
        watermarked.add(id);
        return stringBuffer.toString();
    };

    public SourceMapGenerator(LanguageTranslator translator) {
        this.translator = translator.clone();
        this.translator._viewer.registerPostprocessFunction(watermarkingHook);
    }

    public SourceMap process(MeaningTree meaningTree) {
        String code = translator.getCode(meaningTree);
        watermarked.clear();
        globalScope = translator.getLatestScopeTable();
        return buildSourceMap(meaningTree, code);
    }

    public SourceMap process(Node root) {
        String code = translator.getCode(root);
        watermarked.clear();
        globalScope = translator.getLatestScopeTable();
        return buildSourceMap(root, code);
    }

    /**
     * Убирает watermark-теги и строит карту позиций.
     * @param root узел или дерево, для которой строится карта
     * @param instrumentedCode текст с тегами
     * @return SourceMap с байтовыми смещениями
     */
    private SourceMap buildSourceMap(NodeIterable root, String instrumentedCode) {
        Map<Long, Pair<Integer, Integer>> result = new HashMap<>();

        Pattern tagPattern = Pattern.compile(START_TAG + "(/?)(\\d+)" + END_TAG);
        Matcher matcher = tagPattern.matcher(instrumentedCode);

        StringBuilder cleanCode = new StringBuilder();
        int lastEnd = 0;

        // стек для открытых узлов
        Deque<Long> stack = new ArrayDeque<>();
        // начало узла (в байтах)
        Map<Long, Integer> startOffsets = new HashMap<>();

        while (matcher.find()) {
            // добавляем кусок до тэга
            cleanCode.append(instrumentedCode, lastEnd, matcher.start());
            lastEnd = matcher.end();

            boolean isClose = matcher.group(1).equals("/");
            long nodeId = Long.parseLong(matcher.group(2));

            if (!isClose) {
                // начало узла в байтах
                int byteOffset = utf8Length(cleanCode);
                startOffsets.put(nodeId, byteOffset);
                stack.push(nodeId);
            } else {
                // конец узла в байтах
                Long openId = stack.pop();
                int start = startOffsets.get(openId);
                int end = utf8Length(cleanCode);
                result.put(openId, Pair.of(start, end - start));
            }
        }

        cleanCode.append(instrumentedCode.substring(lastEnd));

        List<SourceMap.DefinitionLink> definitionLinks = new ArrayList<>();
        HashMap<String, List<String>> hierarchy = new HashMap<>();
        for (var entry : globalScope.scope().allDeclarations().entrySet()) {
            Declaration decl = entry.getValue();
            Identifier id = entry.getKey();
            Definition def = globalScope.scope().findDefinition(decl).orElseGet(null);
            List<Long> relatedTypes = new ArrayList<>();
            for (var typeEntry : globalScope.scope().allTypeDeclarations().entrySet()) {
                if (typeEntry.getValue().equals(decl)) {
                    relatedTypes.add(typeEntry.getKey().getId());
                }
            }
            definitionLinks.add(new SourceMap.DefinitionLink(
                    id.internalRepresentation(), decl.getId(), def == null ? null : def.getId(),
                    JsonNodeTypeClassMapper.getTypeForNode(decl),
                    decl instanceof NestedDeclaration nested ? nested.getParentDeclaration().getId() : null,
                    relatedTypes.toArray(Long[]::new)
            ));

            if (entry.getValue() instanceof ClassDeclaration classDecl) {
                if (!hierarchy.containsKey(classDecl.getTypeNode().internalRepresentation())) {
                    hierarchy.put(
                            classDecl.getTypeNode().getName().internalRepresentation(),
                            new ArrayList<>()
                    );
                }
                hierarchy.get(classDecl.getTypeNode().getName().internalRepresentation()).addAll(classDecl.getParents().stream()
                        .map(Type::internalRepresentation).toList()
                );
            }
        }

        List<SourceMap.ImportLink> importLinks = new ArrayList<>();
        for (var importObj : globalScope.scope().allImports()) {
            switch (importObj) {
                case ImportMembersFromModule memberImp -> {
                    importLinks.add(new SourceMap.ImportLink(
                            memberImp.getModuleName().internalRepresentation(),
                            memberImp.getId(),
                            JsonNodeTypeClassMapper.getTypeForNode(memberImp),
                            memberImp.getMembers().stream().map(Identifier::internalRepresentation).toArray(String[]::new),
                            memberImp instanceof StaticImportMembersFromModule, false
                    ));
                }
                case ImportModule modImp -> {
                    importLinks.add(new SourceMap.ImportLink(
                            modImp.getModuleName().internalRepresentation(),
                            modImp.getId(),
                            JsonNodeTypeClassMapper.getTypeForNode(modImp),
                            new String[0],
                            modImp instanceof StaticImportAll, false
                    ));
                }
                case Include includeImp -> {
                    importLinks.add(new SourceMap.ImportLink(
                            includeImp.getFileName().getUnescapedValue(),
                            includeImp.getId(),
                            JsonNodeTypeClassMapper.getTypeForNode(includeImp),
                            new String[0],
                            false, true
                    ));
                }
                case ImportModules modules -> {
                    for (Identifier id : modules.getModulesNames()) {
                        importLinks.add(new SourceMap.ImportLink(
                                id.internalRepresentation(),
                                modules.getId(),
                                JsonNodeTypeClassMapper.getTypeForNode(modules),
                                new String[0],
                                false, false
                        ));
                    }
                }
                default -> throw new MeaningTreeException("Unknown import: " + importObj);
            }
        }

        return new SourceMap(cleanCode.toString(), root, result,
                definitionLinks, importLinks,
                hierarchy.entrySet().stream().map(entry -> {
                    List<String> list = new ArrayList<>();
                    list.add(entry.getKey());
                    list.addAll(entry.getValue());
                    return list;
                }).toList(),
                translator.getLanguageName()
        );
    }

    /**
     * Возвращает количество байтов в UTF-8 для текущего буфера
     */
    private static int utf8Length(CharSequence seq) {
        return seq.toString().getBytes(StandardCharsets.UTF_8).length;
    }
}
