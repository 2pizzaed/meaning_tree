package org.vstu.meaningtree.languages;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.treesitter.TSException;
import org.treesitter.TSNode;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.utils.Hook;
import org.vstu.meaningtree.utils.ListModificationType;
import org.vstu.meaningtree.utils.TreeSitterUtils;
import org.vstu.meaningtree.utils.tokens.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class LanguageTokenizer {
    protected String code;
    protected LanguageTranslator translator;
    protected LanguageParser parser;
    protected LanguageViewer viewer;
    protected boolean navigablePseudoTokens = false;

    private List<Hook<Triple<Integer, Token, ListModificationType>>>
            tokenListModificationsHooks = new ArrayList<>();
    private List<Hook<Pair<TSNode, TokenList>>> tokenParsePositionHooks = new ArrayList<>();

    protected abstract Token recognizeToken(TSNode node);

    public TokenList tokenize(String code, boolean noPrepare) {
        this.code = noPrepare ? code : translator.prepareCode(code);
        parser.getMeaningTree(this.code);
        TokenList list = new TokenList();
        for (var hook : tokenListModificationsHooks) {
            list.registerHook(hook);
        }
        collectTokens(parser.getRootNode(), list, true, null);
        return list;
    }

    public boolean registerOnTokenListModificationHook(Hook<Triple<Integer, Token, ListModificationType>> hook) {
        return tokenListModificationsHooks.add(hook);
    }

    public boolean removeOnTokenListModificationHook(Hook<Triple<Integer, Token, ListModificationType>> hook) {
        return tokenListModificationsHooks.remove(hook);
    }

    public boolean registerOnTokenPositionHook(Hook<Pair<TSNode, TokenList>> hook) {
        return tokenParsePositionHooks.add(hook);
    }

    public boolean removeOnTokenPositionHook(Hook<Pair<TSNode, TokenList>> hook) {
        return tokenParsePositionHooks.remove(hook);
    }

    public Pair<Boolean, TokenList> tryTokenize(String code, boolean noPrepare) {
        try {
            return ImmutablePair.of(true, tokenize(code, noPrepare));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    /**
     * Токенизирует выражения из кода, переданного в токенайзер
     * Не учитывает режим выражения и прочие конфигурации
     * @return
     */
    public TokenList tokenize(String code) {
        return tokenize(code, false);
    }

    public Pair<Boolean, TokenList> tryTokenize(String code) {
        try {
            return ImmutablePair.of(true, tokenize(code));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    public abstract TokenList tokenizeExtended(Node node);

    /**
     * Токенизирует узлы из дерева MeaningTree, с возможностью выведения привязанных к узлам значений
     * На данный момент расширенные токены генерируются только для выражений (используйте в конфигурации языка expressionMode=true и skipErrors=true)
     * @param mt - общее дерево
     * @return
     */
    public TokenList tokenizeExtended(MeaningTree mt) {
        return tokenizeExtended(mt.getRootNode());
    }

    public Pair<Boolean, TokenList> tryTokenizeExtended(MeaningTree mt) {
        try {
            return ImmutablePair.of(true, tokenizeExtended(mt));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    public TokenList tokenizeExtended(String code) {
        return tokenizeExtended(parser.getMeaningTree(translator.prepareCode(code)));
    }

    public Pair<Boolean, TokenList> tryTokenizeExtended(String code) {
        try {
            return ImmutablePair.of(true, tokenizeExtended(code));
        } catch (TSException | MeaningTreeException | IllegalArgumentException | ClassCastException e) {
            return ImmutablePair.of(false, null);
        }
    }

    /**
     * Список узлов tree sitter дерева, внутрь которых при обходе заходить не нужно, но нужно их обработать целиком
     * @return
     */
    protected List<String> getStopNodes() {
        return List.of();
    }

    protected abstract List<String> getOperatorNodes(OperatorArity arity);

    protected abstract String getFieldNameByOperandPos(OperandPosition pos, String operatorNode);

    protected abstract OperatorToken getOperator(String tokenValue, TSNode node);
    public abstract OperatorToken getOperatorByTokenName(String tokenName);

    public LanguageTokenizer(LanguageTranslator translator) {
        this.translator = translator;
        this.parser = translator._language;
        this.viewer = translator._viewer;
    }

    public LanguageTokenizer setEnabledNavigablePseudoTokens(boolean enabled) {
        navigablePseudoTokens = enabled;
        return this;
    }

    protected TokenGroup collectTokens(TSNode node, TokenList tokens, boolean detectOperator, Map<OperandPosition, TokenGroup> parent) {
        for (var hook : tokenParsePositionHooks) {
            var pair = Pair.of(node, tokens);
            if (hook.isTriggered(pair)) {
                hook.accept(pair);
            }
        }
        int start = tokens.size();
        boolean skipChildren = false;
        if (node.getChildCount() == 0 || getStopNodes().contains(node.getType())) {
            String value = TreeSitterUtils.getCodePiece(code, node);
            if (value.trim().isEmpty()) {
                return new TokenGroup(0, 0, tokens);
            }
            tokens.add(recognizeToken(node));
            skipChildren = true;
        } else if (
                (getOperatorNodes(OperatorArity.BINARY).contains(node.getType())
                || getOperatorNodes(OperatorArity.TERNARY).contains(node.getType())) && detectOperator
        ) {
            OperatorToken token = null;
            Map<OperandPosition, TokenGroup> operands = new HashMap<>();
            for (int i = 0; i < node.getChildCount(); i++) {
                TokenGroup group = collectTokens(node.getChild(i), tokens, true, operands);

                String leftName = getFieldNameByOperandPos(OperandPosition.LEFT, node.getType());
                String rightName = getFieldNameByOperandPos(OperandPosition.RIGHT, node.getType());
                String centerName = getFieldNameByOperandPos(OperandPosition.CENTER, node.getType());

                if (!node.getParent().isNull() && parent != null) {
                    String leftParentName = getFieldNameByOperandPos(OperandPosition.LEFT, node.getParent().getType());
                    String rightParentName = getFieldNameByOperandPos(OperandPosition.RIGHT, node.getParent().getType());
                    String centerParentName = getFieldNameByOperandPos(OperandPosition.CENTER, node.getParent().getType());

                    if (leftParentName != null && leftParentName.contains(".")
                            && leftParentName.split("\\.").length == 2
                            && leftParentName.split("\\.")[1].equals(node.getType())
                    ) {
                        parent.put(OperandPosition.LEFT, group);
                    } else if (rightParentName != null && rightParentName.contains(".")
                            && rightParentName.split("\\.").length == 2
                            && rightParentName.split("\\.")[1].equals(node.getType())
                    ) {
                        parent.put(OperandPosition.RIGHT, group);
                    } else if (centerParentName != null && centerParentName.contains(".")
                            && centerParentName.split("\\.").length == 2
                            && centerParentName.split("\\.")[1].equals(node.getType())
                    ) {
                        parent.put(OperandPosition.CENTER, group);
                    }
                }

                int namedIndex = -1;
                for (int j = 0; j < node.getNamedChildCount(); j++) {
                    if (node.getChild(i).equals(node.getNamedChild(j))) {
                        namedIndex = j;
                        break;
                    }
                }
                String index = "_" + namedIndex;
                OperandPosition pos = null;

                if (index.equals(leftName)) {
                    pos = OperandPosition.LEFT;
                } else if (index.equals(centerName)) {
                    pos = OperandPosition.CENTER;
                } else if (index.equals(rightName)) {
                    pos = OperandPosition.RIGHT;
                }

                if (node.getFieldNameForChild(i) != null) {
                    if (node.getFieldNameForChild(i).equals(leftName)) {
                        pos = OperandPosition.LEFT;
                    } else if (node.getFieldNameForChild(i).equals(rightName)) {
                        pos = OperandPosition.RIGHT;
                    } else if (node.getFieldNameForChild(i).equals(centerName)) {
                        pos = OperandPosition.CENTER;
                    }
                }

                if (pos != null) {
                    operands.put(pos, group);
                }

                if (group.length() > 0 && group.length() == 1 && tokens.get(group.start) instanceof OperatorToken op && token == null) {
                    token = op;
                }
            }

            for (OperandPosition pos : operands.keySet()) {
                TokenGroup group = operands.get(pos);
                for (int i = group.start; i < group.stop; i++) {
                    if (!(tokens.get(i) instanceof OperandToken)) {
                        tokens.set(i, new OperandToken(tokens.get(i).value, tokens.get(i).type));
                    }
                    OperandToken opTok = (OperandToken)tokens.get(i);
                    if (opTok.operandOf() == null) {
                        opTok.setMetadata(token, pos);
                    }
                }
            }
            skipChildren = true;
        } else if (getOperatorNodes(OperatorArity.UNARY).contains(node.getType()) && detectOperator) {
            TokenGroup group = collectTokens(node, tokens, false, null);
            int unaryStart = group.start;
            int unaryStop = group.stop;
            OperatorToken token;
            OperandPosition pos = OperandPosition.RIGHT;
            if (tokens.get(unaryStart) instanceof OperatorToken op) {
                token = op;
                unaryStart++;
            } else {
                token = (OperatorToken) tokens.getLast();
                unaryStop--;
                pos = OperandPosition.LEFT;
            }
            for (int i = unaryStart; i < unaryStop; i++) {
                if (!(tokens.get(i) instanceof OperandToken)) {
                    tokens.set(i, new OperandToken(tokens.get(i).value, tokens.get(i).type));
                }
                ((OperandToken)tokens.get(i)).setMetadata(token, pos);
            }
            skipChildren = true;
        }
        TSNode prev = null;
        for (int i = 0; i < node.getChildCount() && !skipChildren; i++) {
            if (prev != null && navigablePseudoTokens) {
                int prevEndUtf16 = byteOffsetToCharIndex(code, prev.getEndByte());
                int currStartUtf16 = byteOffsetToCharIndex(code, node.getChild(i).getStartByte());
                String between = code.substring(prevEndUtf16, currStartUtf16);
                String whites = between.replaceAll("[^ \t\n]", "");
                StringBuilder buffer = new StringBuilder();
                for (int k = 0; k < whites.length(); k++) {
                    char white = whites.charAt(k);
                    if (white == '\n') {
                        if (buffer.length() > 0 && !buffer.toString().equals(" ")) {
                            tokens.add(new Whitespace(buffer.toString(), TokenType.UNKNOWN));
                            buffer.delete(0, buffer.length());
                        }
                        tokens.add(new Whitespace("\n", TokenType.SEPARATOR));
                    } else {
                        buffer.append(white);
                    }
                }
                if (buffer.length() > 0 && !buffer.toString().equals(" ")) {
                    tokens.add(new Whitespace(buffer.toString(), TokenType.UNKNOWN));
                }
            }
            collectTokens(node.getChild(i), tokens, true, null);
            if (node.getChild(i) != null) prev = node.getChild(i);
        }
        int stop = tokens.size();
        return new TokenGroup(start, stop, tokens);

    }

    private static int byteOffsetToCharIndex(String code, int byteOffset) {
        // кодируем в UTF-8, а потом обратно считаем длину
        byte[] utf8 = code.getBytes(StandardCharsets.UTF_8);
        // обрезаем до нужного места
        String sub = new String(utf8, 0, byteOffset, StandardCharsets.UTF_8);
        return sub.length(); // количество UTF-16 code units = индекс в Java String
    }
}
