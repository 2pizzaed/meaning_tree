package org.vstu.meaningtree.serializers.json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeSerializationException;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.*;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.Literal;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.comprehensions.Comprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.ContainerBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.RangeBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.identifiers.*;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.*;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.PlacementNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerMemberAccess;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.io.*;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.statements.*;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.CompoundAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.*;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.*;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.serializers.model.Deserializer;
import org.vstu.meaningtree.utils.*;
import org.vstu.meaningtree.utils.tokens.*;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Experimental
public class JsonDeserializer implements Deserializer<JsonObject> {

    private final Map<Long, Node> nodeCache = new HashMap<>();
    private final Map<Long, Token> tokenCache = new HashMap<>();
    private final Field idField;

    public JsonDeserializer() {
        try {
            this.idField =  Node.class.getDeclaredField("_id");
        } catch (NoSuchFieldException e) {
            throw new MeaningTreeSerializationException(e);
        }
    }


    @Override
    public MeaningTree deserializeTree(JsonObject json) {
        String type = json.get("type").getAsString();
        if (!"meaning_tree".equals(type)) {
            throw new MeaningTreeSerializationException("Expected meaning_tree, got: " + type);
        }

        Node rootNode = deserialize(json.getAsJsonObject("root_node"));
        MeaningTree tree = new MeaningTree(rootNode);

        if (json.has("labels") && !json.get("labels").isJsonNull()) {
            JsonArray labelsArray = json.getAsJsonArray("labels");
            for (JsonElement labelElement : labelsArray) {
                Label label = deserializeLabel(labelElement.getAsJsonObject());
                tree.setLabel(label);
            }
        }

        return tree;
    }

    @Override
    public SourceMap deserializeSourceMap(JsonObject serialized) {
        if (!serialized.get("type").getAsString().equals("source_map")) {
            throw new MeaningTreeSerializationException("JSON is not a source_map");
        }

        // 1. Basic Fields
        String sourceCode = serialized.get("source_code").getAsString();
        String language = serialized.get("language").getAsString();

        // 2. Root Node
        Node rootNode = deserialize(serialized.getAsJsonObject("origin"));

        // 3. Byte Positions
        Map<Long, Pair<Integer, Integer>> bytePositions = new HashMap<>();
        if (serialized.has("byte_positions")) {
            JsonObject map = serialized.getAsJsonObject("byte_positions");
            for (String key : map.keySet()) {
                JsonArray posArr = map.getAsJsonArray(key);
                bytePositions.put(
                        Long.parseLong(key),
                        Pair.of(posArr.get(0).getAsInt(), posArr.get(1).getAsInt())
                );
            }
        }

        // 4. Definitions
        List<SourceMap.DefinitionLink> definitions = new ArrayList<>();
        if (serialized.has("declarations")) {
            JsonArray decls = serialized.getAsJsonArray("declarations");
            for (JsonElement el : decls) {
                JsonObject obj = el.getAsJsonObject();

                // Обработка массива related_types_id
                Long[] relatedTypes = new Long[0];
                if (obj.has("relatedTypesId") && !obj.get("relatedTypesId").isJsonNull()) {
                    JsonArray arr = obj.getAsJsonArray("relatedTypesId");
                    relatedTypes = new Long[arr.size()];
                    for (int i = 0; i < arr.size(); i++) relatedTypes[i] = arr.get(i).getAsLong();
                }

                definitions.add(new SourceMap.DefinitionLink(
                        obj.get("name").getAsString(),
                        obj.get("declarationNodeId").getAsLong(),
                        obj.has("definitionNodeId") && !obj.get("definitionNodeId").isJsonNull() ? obj.get("definitionNodeId").getAsLong() : null,
                        obj.get("type").getAsString(),
                        obj.has("parentDeclarationId") && !obj.get("parentDeclarationId").isJsonNull() ? obj.get("parentDeclarationId").getAsLong() : null,
                        relatedTypes
                ));
            }
        }

        // 5. Imports
        List<SourceMap.ImportLink> imports = new ArrayList<>();
        if (serialized.has("imports")) {
            JsonArray imps = serialized.getAsJsonArray("imports");
            for (JsonElement el : imps) {
                JsonObject obj = el.getAsJsonObject();

                String[] components = new String[0];
                if (obj.has("components")) {
                    JsonArray arr = obj.getAsJsonArray("components");
                    components = new String[arr.size()];
                    for (int i = 0; i < arr.size(); i++) components[i] = arr.get(i).getAsString();
                }

                imports.add(new SourceMap.ImportLink(
                        obj.get("libraryName").getAsString(),
                        obj.get("nodeId").getAsLong(),
                        obj.get("type").getAsString(),
                        components,
                        obj.get("isStatic").getAsBoolean(),
                        obj.get("allContentInclude").getAsBoolean()
                ));
            }
        }

        // 6. User Type Hierarchy
        List<List<String>> userTypeHierarchy = new ArrayList<>();
        if (serialized.has("user_type_hierarchy")) {
            JsonArray hierarchy = serialized.getAsJsonArray("user_type_hierarchy");
            for (JsonElement groupEl : hierarchy) {
                List<String> group = new ArrayList<>();
                for (JsonElement typeEl : groupEl.getAsJsonArray()) {
                    group.add(typeEl.getAsString());
                }
                userTypeHierarchy.add(group);
            }
        }

        return new SourceMap(sourceCode, rootNode, bytePositions, definitions, imports, userTypeHierarchy, language);
    }


    @Override
    public TokenList deserializeTokens(JsonObject json) {
        String type = json.get("type").getAsString();
        if (!"tokens".equals(type)) {
            throw new MeaningTreeSerializationException("Expected tokens, got: " + type);
        }

        TokenList tokenList = new TokenList();
        JsonArray itemsArray = json.getAsJsonArray("items");
        for (JsonElement element : itemsArray) {
            Token token = deserializeToken(element.getAsJsonObject());
            if (token != null) {
                tokenList.add(token);
            }
        }

        return tokenList;
    }

    @Override
    public Token deserializeToken(JsonObject json) {
        if (json == null) {
            return null;
        }

        String tokenType = json.get("token_type").getAsString();
        String value = json.get("value").getAsString();
        long id = json.get("id").getAsLong();

        TokenType type = parseEnum(TokenType.class, tokenType);
        Token token;

        boolean isPseudo = json.get("is_pseudo").getAsBoolean();

        if (json.has("precedence")) {
            // OperatorToken
            int precedence = json.get("precedence").getAsInt();
            OperatorAssociativity assoc = parseEnum(OperatorAssociativity.class, json.get("associativity").getAsString());
            OperatorArity arity = parseEnum(OperatorArity.class, json.get("arity").getAsString());
            boolean isStrictOrder = json.get("is_strict_order").getAsBoolean();

            OperatorTokenPosition tokenPos = null;
            if (json.has("token_position")) {
                tokenPos = parseEnum(OperatorTokenPosition.class, json.get("token_position").getAsString());
            }

            OperatorType additionalOpType = null;
            if (json.has("optype_metadata") && !json.get("optype_metadata").isJsonNull()) {
                additionalOpType = parseEnum(OperatorType.class, json.get("optype_metadata").getAsString());
            }

            if (json.has("token_values")) {
                // ComplexOperatorToken
                JsonArray tokenValues = json.getAsJsonArray("token_values");
                String[] complexTokenValues = new String[tokenValues.size()];
                for (int i = 0; i < tokenValues.size(); i++) {
                    complexTokenValues[i] = tokenValues.get(i).getAsString();
                }
                int positionOfToken = json.get("token_position").getAsInt();
                token = new ComplexOperatorToken(positionOfToken, value, type, tokenPos, precedence, assoc, arity, isStrictOrder, complexTokenValues);
            } else {
                token = new OperatorToken(value, type, precedence, assoc, arity, isStrictOrder, tokenPos, additionalOpType);
            }

            if (json.has("first_evaluated_operand") && !json.get("first_evaluated_operand").isJsonNull()) {
                OperandPosition firstOp = parseEnum(OperandPosition.class, json.get("first_evaluated_operand").getAsString());
                ((OperatorToken) token).setFirstOperandToEvaluation(firstOp);
            }
        } else if (json.has("operand_of")) {
            // OperandToken
            token = new OperandToken(value, type);
            // Metadata will be set after all tokens are deserialized
        } else if (isPseudo) {
            if (json.has("has_newlines")) {
                token = new Whitespace(value, type);
            } else {
                token = new PseudoToken(value, type);
                if (json.has("metadata") && !json.get("metadata").isJsonNull()) {
                    ((PseudoToken) token).setAttribute(json.get("metadata"));
                }
            }
        } else {
            token = new Token(value, type);
        }

        if (json.has("byte_pos") && !json.get("byte_pos").isJsonNull()) {
            JsonArray bytePos = json.getAsJsonArray("byte_pos");
            token.setBytePosition(new BytePosition(bytePos.get(0).getAsInt(), bytePos.get(1).getAsInt()));
        }

        tokenCache.put(id, token);
        return token;
    }

    @Override
    public Node deserialize(JsonObject json) {
        if (json == null) {
            return null;
        }

        String type = json.get("type").getAsString();
        long id = json.get("id").getAsLong();

        Node node = deserializeNodeByType(type, json);

        try {
            idField.setAccessible(true);
            idField.set(node, id);
            idField.setAccessible(false);
        } catch (IllegalAccessException e) { }

        if (node != null) {
            nodeCache.put(id, node);

            if (json.has("labels") && !json.get("labels").isJsonNull()) {
                JsonArray labelsArray = json.getAsJsonArray("labels");
                for (JsonElement labelElement : labelsArray) {
                    Label label = deserializeLabel(labelElement.getAsJsonObject());
                    node.setLabel(label);
                }
            }
        }

        return node;
    }

    private Node deserializeNodeByType(String type, JsonObject json) {
        return switch (type) {
            // Math operators
            case "add_operator" -> new AddOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "sub_operator" -> new SubOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "mul_operator" -> new MulOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "div_operator" -> new DivOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "mod_operator" -> new ModOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "mat_mul_operator" -> new MatMulOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "floor_div_operator" -> new FloorDivOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "pow_operator" -> new PowOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );

            // Comparison operators
            case "eq_operator" -> new EqOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "not_eq_operator" -> new NotEqOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "ge_operator" -> new GeOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "gt_operator" -> new GtOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "le_operator" -> new LeOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "lt_operator" -> new LtOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "reference_eq_operator" -> new ReferenceEqOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand")),
                    json.get("is_negative").getAsBoolean() // Default to non-negative
            );
            case "three_way_comparison_operator" -> new ThreeWayComparisonOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );

            // Logical operators
            case "short_circuit_and_operator" -> new ShortCircuitAndOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "short_circuit_or_operator" -> new ShortCircuitOrOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "long_circuit_and_operator" -> new LongCircuitAndOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "long_circuit_or_operator" -> new LongCircuitOrOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "not_operator" -> new NotOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );

            // Bitwise operators
            case "bitwise_and_operator" -> new BitwiseAndOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "bitwise_or_operator" -> new BitwiseOrOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "xor_operator" -> new XorOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "inversion_operator" -> new InversionOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "left_shift_operator" -> new LeftShiftOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );
            case "right_shift_operator" -> new RightShiftOp(
                    deserializeExpression(json.getAsJsonObject("left_operand")),
                    deserializeExpression(json.getAsJsonObject("right_operand"))
            );

            // Unary operators
            case "unary_minus_operator" -> new UnaryMinusOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "unary_plus_operator" -> new UnaryPlusOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "postfix_increment_operator" -> new PostfixIncrementOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "postfix_decrement_operator" -> new PostfixDecrementOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "prefix_increment_operator" -> new PrefixIncrementOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "prefix_decrement_operator" -> new PrefixDecrementOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );

            // Literals
            case "float_literal" -> new FloatLiteral(
                    json.get("value").getAsString(),
                    json.get("is_double").getAsBoolean()
            );
            case "int_literal" -> new IntegerLiteral(
                    json.get("value").getAsLong()
            );
            case "string_literal" -> StringLiteral.fromUnescaped(
                    json.get("value").getAsString(),
                    StringLiteral.Type.NONE
            );
            case "bool_literal" -> new BoolLiteral(
                    json.get("value").getAsBoolean()
            );
            case "character_literal" -> new CharacterLiteral(
                    json.get("value").getAsInt()
            );
            case "null_literal" -> new NullLiteral();
            case "array_literal" -> new ArrayLiteral(
                    deserializeExpressionList(json.getAsJsonArray("elements"))
            );
            case "list_literal" -> new ListLiteral(
                    deserializeExpressionList(json.getAsJsonArray("elements"))
            );
            case "set_literal" -> new SetLiteral(
                    deserializeExpressionList(json.getAsJsonArray("elements"))
            );
            case "unmodifiable_list_literal" -> new UnmodifiableListLiteral(
                    deserializeExpressionList(json.getAsJsonArray("elements"))
            );
            case "interpolated_string_literal" -> {
                StringLiteral.Type stringType = parseEnum(StringLiteral.Type.class, json.get("type").getAsString());
                List<Expression> components = deserializeExpressionList(json.getAsJsonArray("components"));
                yield new InterpolatedStringLiteral(stringType, components);
            }

            // Identifiers
            case "identifier" -> new SimpleIdentifier(
                    json.get("name").getAsString()
            );
            case "qualified_identifier" -> new QualifiedIdentifier(
                    (Identifier) deserialize(json.getAsJsonObject("scope")),
                    (SimpleIdentifier) deserialize(json.getAsJsonObject("member"))
            );
            case "scoped_identifier" -> {
                List<SimpleIdentifier> identifiers = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("identifiers");
                for (JsonElement elem : array) {
                    identifiers.add((SimpleIdentifier) deserialize(elem.getAsJsonObject()));
                }
                yield new ScopedIdentifier(identifiers);
            }
            case "self_reference" -> new SelfReference(
                    json.get("name").getAsString()
            );
            case "super_class_reference" -> new SuperClassReference();

            // Expressions
            case "parenthesized_expression" -> new ParenthesizedExpression(
                    deserializeExpression(json.getAsJsonObject("expression"))
            );
            case "assignment_expression" -> new AssignmentExpression(
                    deserializeExpression(json.getAsJsonObject("target")),
                    deserializeExpression(json.getAsJsonObject("value"))
            );
            case "member_access" -> new MemberAccess(
                    deserializeExpression(json.getAsJsonObject("expression")),
                    (SimpleIdentifier) deserialize(json.getAsJsonObject("member"))
            );
            case "index_expression" -> new IndexExpression(
                    deserializeExpression(json.getAsJsonObject("expr")),
                    deserializeExpression(json.getAsJsonObject("index"))
            );
            case "function_call" -> new FunctionCall(
                    deserializeExpression(json.getAsJsonObject("function")),
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "method_call" -> new MethodCall(
                    deserializeExpression(json.getAsJsonObject("receiver")),
                    (SimpleIdentifier) deserialize(json.getAsJsonObject("method_name")),
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "constructor_call" -> new ConstructorCall(
                    (Type) deserialize(json.getAsJsonObject("type")),
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "ternary_operator" -> new TernaryOperator(
                    deserializeExpression(json.getAsJsonObject("condition")),
                    deserializeExpression(json.getAsJsonObject("true_expression")),
                    deserializeExpression(json.getAsJsonObject("false_expression"))
            );
            case "instance_of_operator" -> new InstanceOfOp(
                    deserializeExpression(json.getAsJsonObject("expression")),
                    (Type) deserialize(json.getAsJsonObject("type"))
            );
            case "contains_operator" -> new ContainsOp(
                    deserializeExpression(json.getAsJsonObject("element")),
                    deserializeExpression(json.getAsJsonObject("collection")),
                    json.get("is_negative").getAsBoolean()
            );
            case "cast_type_expression" -> new CastTypeExpression(
                    (Type) deserialize(json.getAsJsonObject("target_type")),
                    deserializeExpression(json.getAsJsonObject("value"))
            );
            case "sizeof_expression" -> new SizeofExpression(
                    deserializeExpression(json.getAsJsonObject("value"))
            );
            case "range" -> {
                Expression start = json.has("start") && !json.get("start").isJsonNull()
                        ? deserializeExpression(json.getAsJsonObject("start")) : null;
                Expression stop = json.has("stop") && !json.get("stop").isJsonNull()
                        ? deserializeExpression(json.getAsJsonObject("stop")) : null;
                Expression step = json.has("step") && !json.get("step").isJsonNull()
                        ? deserializeExpression(json.getAsJsonObject("step")) : null;
                boolean isExcludingStart = json.get("isExcludingStart").getAsBoolean();
                boolean isExcludingEnd = json.get("isExcludingEnd").getAsBoolean();
                Range.Type rangeType = parseEnum(Range.Type.class, json.get("rangeType").getAsString());
                yield new Range(start, stop, step, isExcludingStart, isExcludingEnd, rangeType);
            }
            case "key_value_pair" -> new KeyValuePair(
                    deserializeExpression(json.getAsJsonObject("key")),
                    deserializeExpression(json.getAsJsonObject("value"))
            );
            case "array_initializer" -> new ArrayInitializer(
                    deserializeExpressionList(json.getAsJsonArray("elements"))
            );
            case "comma_expression" -> new CommaExpression(
                    deserializeExpressionList(json.getAsJsonArray("expressions"))
            );
            case "expression_sequence" -> new ExpressionSequence(
                    deserializeExpressionList(json.getAsJsonArray("expressions"))
            );
            case "delete_expression" -> new DeleteExpression(
                    deserializeExpression(json.getAsJsonObject("expr"))
            );
            case "object_new_expression" -> new ObjectNewExpression(
                    (Type) deserialize(json.getAsJsonObject("target_type")),
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "placement_new_expression" -> new PlacementNewExpression(
                    (Type) deserialize(json.getAsJsonObject("target_type")),
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "pointer_member_access" -> new PointerMemberAccess(
                    deserializeExpression(json.getAsJsonObject("expression")),
                    (SimpleIdentifier) deserialize(json.getAsJsonObject("member"))
            );
            case "pointer_pack_operator" -> new PointerPackOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "pointer_unpack_operator" -> new PointerUnpackOp(
                    deserializeExpression(json.getAsJsonObject("operand"))
            );
            case "compound_comparison" -> {
                JsonArray comparisonsArray = json.getAsJsonArray("comparisons");
                List<BinaryComparison> comparisons = new ArrayList<>();
                for (JsonElement elem : comparisonsArray) {
                    JsonObject compJson = elem.getAsJsonObject();
                    Expression left = deserializeExpression(compJson.getAsJsonObject("left"));
                    Expression right = deserializeExpression(compJson.getAsJsonObject("right"));
                    String operator = compJson.get("operator").getAsString();

                    BinaryComparison comp = switch (operator) {
                        case "eq_operator" -> new EqOp(left, right);
                        case "not_eq_operator" -> new NotEqOp(left, right);
                        case "ge_operator" -> new GeOp(left, right);
                        case "gt_operator" -> new GtOp(left, right);
                        case "le_operator" -> new LeOp(left, right);
                        case "lt_operator" -> new LtOp(left, right);
                        default -> throw new MeaningTreeSerializationException("Unknown comparison operator: " + operator);
                    };
                    comparisons.add(comp);
                }
                yield new CompoundComparison(comparisons);
            }

            // Comprehensions
            case "container_based_comprehension" -> {
                Comprehension.ComprehensionItem item = deserializeComprehensionItem(json.getAsJsonObject("item"));
                VariableDeclaration containerItem = (VariableDeclaration) deserialize(json.getAsJsonObject("container_item"));
                Expression container = deserializeExpression(json.getAsJsonObject("container"));
                Expression condition = deserializeExpression(json.getAsJsonObject("condition")); // Not in serializer
                yield new ContainerBasedComprehension(item, containerItem, container, condition);
            }
            case "range_based_comprehension" -> {
                Comprehension.ComprehensionItem item = deserializeComprehensionItem(json.getAsJsonObject("item"));
                SimpleIdentifier identifier = (SimpleIdentifier) deserialize(json.getAsJsonObject("identifier"));
                Range range = (Range) deserialize(json.getAsJsonObject("range"));
                Expression condition = deserializeExpression(json.getAsJsonObject("condition")); // Not in serializer
                yield new RangeBasedComprehension(item, identifier, range, condition);
            }

            // IO
            case "format_input" -> new FormatInput(
                    deserializeExpression(json.getAsJsonObject("format_string")),
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "format_print" -> {
                Expression formatString = deserializeExpression(json.getAsJsonObject("format_string"));
                List<Expression> args = deserializeExpressionList(json.getAsJsonArray("arguments"));
                yield new FormatPrint(formatString, args);
            }
            case "input_command" -> new InputCommand(
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "pointer_input_command" -> new PointerInputCommand(
                    deserializeExpression(json.getAsJsonObject("target")),
                    deserializeExpressionList(json.getAsJsonArray("arguments"))
            );
            case "print_values" -> {
                List<Expression> values = deserializeExpressionList(json.getAsJsonArray("arguments"));
                StringLiteral separator = json.has("separator") && !json.get("separator").isJsonNull()
                        ? (StringLiteral) deserialize(json.getAsJsonObject("separator")) : null;
                StringLiteral end = json.has("end") && !json.get("end").isJsonNull()
                        ? (StringLiteral) deserialize(json.getAsJsonObject("end")) : null;
                yield new PrintValues(values, separator, end);
            }

            // Memory
            case "memory_allocation_call" -> {
                Expression function = deserializeExpression(json.getAsJsonObject("function"));
                List<Expression> args = deserializeExpressionList(json.getAsJsonArray("arguments"));
                boolean isClear = json.get("is_clear").getAsBoolean();
                // Simplified - need to extract type and objectCount from args
                Type placeholder = new IntType(); // Placeholder
                Expression objectCount = args.isEmpty() ? new IntegerLiteral(1) : args.get(0);
                yield new MemoryAllocationCall(placeholder, objectCount, isClear);
            }
            case "memory_free_call" -> new MemoryFreeCall(
                    deserializeExpression(json.getAsJsonObject("value"))
            );

            // Modules
            case "alias" -> new Alias(
                    (Identifier) deserialize(json.getAsJsonObject("realName")),
                    (SimpleIdentifier) deserialize(json.getAsJsonObject("alias"))
            );
            case "import_module" -> new ImportModule(
                    (Identifier) deserialize(json.getAsJsonObject("module_name"))
            );
            case "import_modules" -> {
                List<Identifier> modules = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("modules");
                for (JsonElement elem : array) {
                    modules.add((Identifier) deserialize(elem.getAsJsonObject()));
                }
                yield new ImportModules(modules);
            }
            case "import_all_from_module" -> new ImportAllFromModule(
                    (Identifier) deserialize(json.getAsJsonObject("module_name"))
            );
            case "import_members_from_module" -> {
                Identifier moduleName = (Identifier) deserialize(json.getAsJsonObject("module_name"));
                List<Identifier> members = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("members");
                for (JsonElement elem : array) {
                    members.add((Identifier) deserialize(elem.getAsJsonObject()));
                }
                yield new ImportMembersFromModule(moduleName, members);
            }
            case "static_import_all" ->
                    new StaticImportAll((Identifier) deserialize(json.getAsJsonObject("module_name")));
            case "static_import_members_from_module" -> {
                Identifier moduleName = (Identifier) deserialize(json.getAsJsonObject("module_name"));
                List<Identifier> members = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("members");
                for (JsonElement elem : array) {
                    members.add((Identifier) deserialize(elem.getAsJsonObject()));
                }
                yield new StaticImportMembersFromModule(moduleName, members);
            }
            case "package_declaration" ->
                    new PackageDeclaration((Identifier) deserialize(json.getAsJsonObject("name")));
            // Statements
            case "assignment_statement" -> new AssignmentStatement(
                    deserializeExpression(json.getAsJsonObject("target")),
                    deserializeExpression(json.getAsJsonObject("value"))
            );
            case "compound_assignment_statement" -> {
                List<AssignmentStatement> assignments = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("assignments");
                for (JsonElement elem : array) {
                    assignments.add((AssignmentStatement) deserialize(elem.getAsJsonObject()));
                }
                yield new CompoundAssignmentStatement(assignments.toArray(new AssignmentStatement[0]));
            }
            case "multiple_assignment_statement" -> {
                List<AssignmentStatement> statements = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("targets");
                for (JsonElement elem : array) {
                    statements.add((AssignmentStatement) deserialize(elem.getAsJsonObject()));
                }
                yield new MultipleAssignmentStatement(statements);
            }
            case "variable_declaration" -> deserializeVariableDeclaration(json);
            case "field_declaration" -> {
                Type varType = (Type) deserialize(json.getAsJsonObject("var_type"));
                List<DeclarationModifier> modifiers = deserializeModifiers(json.getAsJsonArray("modifiers"));
                List<VariableDeclarator> declarators = deserializeVariableDeclarators(json.getAsJsonArray("declarators"));
                yield new FieldDeclaration(varType, modifiers, declarators);
            }
            case "separated_variable_declaration" -> {
                List<VariableDeclaration> declarations = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("declarations");
                for (JsonElement elem : array) {
                    declarations.add((VariableDeclaration) deserialize(elem.getAsJsonObject()));
                }
                yield new SeparatedVariableDeclaration(declarations);
            }
            case "empty_statement" -> new EmptyStatement();
            case "compound_statement" -> {
                List<Node> statements = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("statements");
                for (JsonElement elem : array) {
                    statements.add(deserialize(elem.getAsJsonObject()));
                }
                yield new CompoundStatement(statements);
            }
            case "expression_statement" -> new ExpressionStatement(
                    deserializeExpression(json.getAsJsonObject("expression"))
            );
            case "return_statement" -> {
                Expression expr = json.has("expression") && !json.get("expression").isJsonNull()
                        ? deserializeExpression(json.getAsJsonObject("expression")) : null;
                yield expr != null ? new ReturnStatement(expr) : new ReturnStatement();
            }
            case "delete_statement" -> new DeleteStatement(
                    deserializeExpression(json.getAsJsonObject("expr"))
            );

            // Control flow
            case "if_statement" -> {
                List<ConditionBranch> branches = new ArrayList<>();
                JsonArray branchesArray = json.getAsJsonArray("branches");
                for (JsonElement elem : branchesArray) {
                    branches.add((ConditionBranch) deserialize(elem.getAsJsonObject()));
                }
                Statement elseBranch = json.has("elseBranch") && !json.get("elseBranch").isJsonNull()
                        ? (Statement) deserialize(json.getAsJsonObject("elseBranch")) : null;
                yield new IfStatement(branches, elseBranch);
            }
            case "condition_branch" -> {
                Expression condition = json.has("condition") && !json.get("condition").isJsonNull()
                        ? deserializeExpression(json.getAsJsonObject("condition")) : null;
                Statement body = (Statement) deserialize(json.getAsJsonObject("body"));
                yield new ConditionBranch(condition, body);
            }
            case "switch_statement" -> {
                Expression targetExpr = deserializeExpression(json.getAsJsonObject("expression"));
                List<CaseBlock> cases = new ArrayList<>();
                JsonArray casesArray = json.getAsJsonArray("cases");
                for (JsonElement elem : casesArray) {
                    cases.add((CaseBlock) deserialize(elem.getAsJsonObject()));
                }
                DefaultCaseBlock defaultCase = json.has("default") && !json.get("default").isJsonNull()
                        ? (DefaultCaseBlock) deserialize(json.getAsJsonObject("default")) : null;
                yield new SwitchStatement(targetExpr, cases, defaultCase);
            }
            case "basic_case_block" -> new BasicCaseBlock(
                    deserializeExpression(json.getAsJsonObject("match_value")),
                    (Statement) deserialize(json.getAsJsonObject("body"))
            );
            case "default_case_block" -> new DefaultCaseBlock(
                    (Statement) deserialize(json.getAsJsonObject("body"))
            );
            case "fallthrough_case_block" -> new FallthroughCaseBlock(
                    deserializeExpression(json.getAsJsonObject("match_value")),
                    (Statement) deserialize(json.getAsJsonObject("body"))
            );

            // Loops
            case "while_loop" -> {
                var loop = new WhileLoop(
                    deserializeExpression(json.getAsJsonObject("condition")),
                    (Statement) deserialize(json.getAsJsonObject("body"))
                );
                if (json.has("jump_label")) {
                    loop.setJumpLabel((JumpLabel) deserialize(json.getAsJsonObject("jump_label")));
                }
                yield loop;
            }
            case "do_while_loop" -> {
                var loop = new DoWhileLoop(
                        deserializeExpression(json.getAsJsonObject("condition")),
                        (Statement) deserialize(json.getAsJsonObject("body"))
                );
                if (json.has("jump_label")) {
                    loop.setJumpLabel((JumpLabel) deserialize(json.getAsJsonObject("jump_label")));
                }
                yield loop;
            }
            case "general_for_loop" -> {
                Node initializer = json.has("initializer") && !json.get("initializer").isJsonNull()
                        ? deserialize(json.getAsJsonObject("initializer")) : null;
                Expression condition = json.has("condition") && !json.get("condition").isJsonNull()
                        ? deserializeExpression(json.getAsJsonObject("condition")) : null;
                Expression update = json.has("update") && !json.get("update").isJsonNull()
                        ? deserializeExpression(json.getAsJsonObject("update")) : null;
                Statement body = (Statement) deserialize(json.getAsJsonObject("body"));
                var loop = new GeneralForLoop((HasInitialization) initializer, condition, update, body);
                if (json.has("jump_label")) {
                    loop.setJumpLabel((JumpLabel) deserialize(json.getAsJsonObject("jump_label")));
                }
                yield loop;
            }
            case "range_for_loop" -> {
                var loop = new RangeForLoop(
                    (Range) deserialize(json.getAsJsonObject("range")),
                    (SimpleIdentifier) deserialize(json.getAsJsonObject("identifier")),
                    (Statement) deserialize(json.getAsJsonObject("body"))
                );
                if (json.has("jump_label")) {
                    loop.setJumpLabel((JumpLabel) deserialize(json.getAsJsonObject("jump_label")));
                }
                yield loop;
            }
            case "for_each_loop" -> {
                var loop = new ForEachLoop(
                    (VariableDeclaration) deserialize(json.getAsJsonObject("item")),
                    deserializeExpression(json.getAsJsonObject("container")),
                    (Statement) deserialize(json.getAsJsonObject("body"))
                );
                if (json.has("jump_label")) {
                    loop.setJumpLabel((JumpLabel) deserialize(json.getAsJsonObject("jump_label")));
                }
                yield loop;
            }
            case "infinite_loop" -> {
                var loop = new InfiniteLoop(
                    (Statement) deserialize(json.getAsJsonObject("body")),
                    LoopType.valueOf(json.get("original_loop_type").getAsString())
                );
                if (json.has("jump_label")) {
                    loop.setJumpLabel((JumpLabel) deserialize(json.getAsJsonObject("jump_label")));
                }
                yield loop;
            }
            case "break_statement" -> new BreakStatement();
            case "continue_statement" -> new ContinueStatement();

            // Types
            case "int_type" -> {
                int size = json.has("size") ? json.get("size").getAsInt() : 32;
                boolean unsigned = json.has("unsigned") && json.get("unsigned").getAsBoolean();
                yield new IntType(size, unsigned);
            }
            case "float_type" -> {
                int size = json.has("size") ? json.get("size").getAsInt() : 32;
                yield new FloatType(size);
            }
            case "character_type" -> {
                int size = json.has("size") ? json.get("size").getAsInt() : 8;
                yield new CharacterType(size);
            }
            case "boolean_type" -> new BooleanType();
            case "string_type" -> {
                int charSize = json.has("char_size") ? json.get("char_size").getAsInt() : 8;
                yield new StringType(charSize);
            }
            case "pointer_type" -> new PointerType(
                    (Type) deserialize(json.getAsJsonObject("target_type"))
            );
            case "reference_type" -> new ReferenceType(
                    (Type) deserialize(json.getAsJsonObject("target_type"))
            );
            case "array_type" -> {
                Shape shape = (Shape) deserialize(json.getAsJsonObject("shape"));
                yield new ArrayType(new IntType(), shape.getDimensionCount(), shape.getDimensions());
            }
            case "list_type" -> new ListType(
                    (Type) deserialize(json.getAsJsonObject("target_type"))
            );
            case "set_type" -> new SetType(
                    (Type) deserialize(json.getAsJsonObject("target_type"))
            );
            case "plain_collection_type" -> new PlainCollectionType(
                    (Type) deserialize(json.getAsJsonObject("target_type"))
            );
            case "dictionary_type", "ordered_dictionary_type" -> new OrderedDictionaryType(
                    (Type) deserialize(json.getAsJsonObject("key_type")),
                    (Type) deserialize(json.getAsJsonObject("value_type"))
            );
            case "unordered_dictionary_type" -> new UnorderedDictionaryType(
                    (Type) deserialize(json.getAsJsonObject("key_type")),
                    (Type) deserialize(json.getAsJsonObject("value_type"))
            );
            case "user_type", "class", "interface", "structure", "enum" -> new org.vstu.meaningtree.nodes.types.user.Class(
                    (Identifier) deserialize(json.getAsJsonObject("name"))
            );
            case "generic_user_type", "generic_class", "generic_structure", "generic_interface" -> {
                Identifier name = (Identifier) deserialize(json.getAsJsonObject("name"));
                List<Type> templates = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("templates");
                for (JsonElement elem : array) {
                    templates.add((Type) deserialize(elem.getAsJsonObject()));
                }
                yield new GenericUserType(name, templates.toArray(new Type[0]));
            }
            case "optional_type" -> new OptionalType(
                    (Type) deserialize(json.getAsJsonObject("target"))
            );
            case "literal_type" -> new LiteralType(
                    (Literal) deserialize(json.getAsJsonObject("literal"))
            );
            case "type_alternatives" -> {
                List<Type> alternatives = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("alternatives");
                for (JsonElement elem : array) {
                    alternatives.add((Type) deserialize(elem.getAsJsonObject()));
                }
                yield new TypeAlternatives(alternatives);
            }
            case "tuple_type" -> {
                List<Type> elements = new ArrayList<>();
                JsonArray array = json.getAsJsonArray("elements");
                for (JsonElement elem : array) {
                    elements.add((Type) deserialize(elem.getAsJsonObject()));
                }
                yield new TupleType(elements);
            }
            case "shape" -> {
                int dimensionCount = json.get("dimension_count").getAsInt();
                List<Expression> dimensions = deserializeExpressionList(json.getAsJsonArray("dimensions"));
                yield new Shape(dimensionCount, dimensions);
            }
            case "no_return" -> new NoReturn();
            case "unknown_type" -> new UnknownType();
            case "type" -> new Type() {
            }; // Anonymous type

            // Declarations
            case "annotation" -> {
                Expression function = deserializeExpression(json.getAsJsonObject("function"));
                List<Expression> args = deserializeExpressionList(json.getAsJsonArray("arguments"));
                yield new Annotation(function, args.toArray(new Expression[0]));
            }
            case "class_declaration" -> {
                List<DeclarationModifier> modifiers = deserializeModifiers(json.getAsJsonArray("modifiers"));
                Identifier name = (Identifier) deserialize(json.getAsJsonObject("name"));
                List<Type> parents = new ArrayList<>();
                if (json.has("parents")) {
                    JsonArray array = json.getAsJsonArray("parents");
                    for (JsonElement elem : array) {
                        parents.add((Type) deserialize(elem.getAsJsonObject()));
                    }
                }
                List<Type> typeParams = new ArrayList<>();
                if (json.has("generic_type_params")) {
                    JsonArray array = json.getAsJsonArray("generic_type_params");
                    for (JsonElement elem : array) {
                        typeParams.add((Type) deserialize(elem.getAsJsonObject()));
                    }
                }
                yield new ClassDeclaration(modifiers, name, typeParams, parents.toArray(new Type[0]));
            }
            case "function_declaration" -> {
                Identifier name = (Identifier) deserialize(json.getAsJsonObject("name"));
                Type returnType = (Type) deserialize(json.getAsJsonObject("return_type"));
                List<Annotation> annotations = deserializeAnnotations(json.getAsJsonArray("annotations"));
                List<DeclarationArgument> arguments = deserializeDeclarationArguments(json.getAsJsonArray("arguments"));
                yield new FunctionDeclaration(name, returnType, annotations, arguments);
            }
            case "method_declaration", "object_constructor_declaration", "object_destructor_declaration" -> {
                UserType owner = (UserType) deserialize(json.getAsJsonObject("owner"));
                Identifier name = (Identifier) deserialize(json.getAsJsonObject("name"));
                List<Annotation> annotations = deserializeAnnotations(json.getAsJsonArray("annotations"));
                List<DeclarationModifier> modifiers = deserializeModifiers(json.getAsJsonArray("modifiers"));
                List<DeclarationArgument> arguments = deserializeDeclarationArguments(json.getAsJsonArray("arguments"));

                if ("object_constructor_declaration".equals(type)) {
                    yield new ObjectConstructorDeclaration(owner, name, annotations, modifiers, arguments);
                } else if ("object_destructor_declaration".equals(type)) {
                    yield new ObjectDestructorDeclaration(owner, name, annotations, modifiers);
                } else {
                    Type returnType = (Type) deserialize(json.getAsJsonObject("return_type"));
                    yield new MethodDeclaration(owner, name, returnType, annotations, modifiers, arguments);
                }
            }

            // Definitions
            case "class_definition" -> new ClassDefinition(
                    (ClassDeclaration) deserialize(json.getAsJsonObject("declaration")),
                    (CompoundStatement) deserialize(json.getAsJsonObject("body"))
            );
            case "function_definition" -> new FunctionDefinition(
                    (FunctionDeclaration) deserialize(json.getAsJsonObject("declaration")),
                    (CompoundStatement) deserialize(json.getAsJsonObject("body"))
            );
            case "method_definition" -> new MethodDefinition(
                    (MethodDeclaration) deserialize(json.getAsJsonObject("declaration")),
                    (CompoundStatement) deserialize(json.getAsJsonObject("body"))
            );
            case "object_constructor_definition" -> {
                ObjectConstructorDeclaration decl = (ObjectConstructorDeclaration) deserialize(json.getAsJsonObject("declaration"));
                CompoundStatement body = (CompoundStatement) deserialize(json.getAsJsonObject("body"));
                yield new ObjectConstructorDefinition(
                        decl.getOwner(), decl.getName(), decl.getAnnotations(),
                        decl.getModifiers(), decl.getArguments(), body
                );
            }
            case "object_destructor_definition" -> {
                ObjectDestructorDeclaration decl = (ObjectDestructorDeclaration) deserialize(json.getAsJsonObject("declaration"));
                CompoundStatement body = (CompoundStatement) deserialize(json.getAsJsonObject("body"));
                yield new ObjectDestructorDefinition(
                        decl.getOwner(), decl.getName(), decl.getAnnotations(),
                        decl.getModifiers(), body
                );
            }
            case "structure_definition" -> new StructureDefinition(
                    (ClassDeclaration) deserialize(json.getAsJsonObject("declaration")),
                    (CompoundStatement) deserialize(json.getAsJsonObject("body"))
            );

            // Other
            case "program_entry_point" -> {
                List<Node> body = new ArrayList<>();
                JsonArray bodyArray = json.getAsJsonArray("body");
                for (JsonElement elem : bodyArray) {
                    body.add(deserialize(elem.getAsJsonObject()));
                }
                yield new ProgramEntryPoint(body);
            }
            case "comment" -> Comment.fromUnescaped(
                    json.get("content").getAsString()
            );

            default -> throw new MeaningTreeSerializationException("Unknown node type: " + type);
        };
    }

// Helper methods

    private Expression deserializeExpression(JsonObject json) {
        return (Expression) deserialize(json);
    }

    private List<Expression> deserializeExpressionList(JsonArray array) {
        List<Expression> list = new ArrayList<>();
        for (JsonElement elem : array) {
            list.add(deserializeExpression(elem.getAsJsonObject()));
        }
        return list;
    }

    private List<DeclarationModifier> deserializeModifiers(JsonArray array) {
        List<DeclarationModifier> modifiers = new ArrayList<>();
        for (JsonElement elem : array) {
            modifiers.add(parseEnum(DeclarationModifier.class, elem.getAsString()));
        }
        return modifiers;
    }

    private List<Annotation> deserializeAnnotations(JsonArray array) {
        List<Annotation> annotations = new ArrayList<>();
        for (JsonElement elem : array) {
            annotations.add((Annotation) deserialize(elem.getAsJsonObject()));
        }
        return annotations;
    }

    private List<DeclarationArgument> deserializeDeclarationArguments(JsonArray array) {
        List<DeclarationArgument> arguments = new ArrayList<>();
        for (JsonElement elem : array) {
            JsonObject argJson = elem.getAsJsonObject();
            Type type = (Type) deserialize(argJson.getAsJsonObject("target_type"));
            SimpleIdentifier name = new SimpleIdentifier(argJson.get("name").getAsString());
            Expression initial = argJson.has("initial") && !argJson.get("initial").isJsonNull()
                    ? deserializeExpression(argJson.getAsJsonObject("initial")) : null;
            arguments.add(new DeclarationArgument(type, name, initial));
        }
        return arguments;
    }

    private List<VariableDeclarator> deserializeVariableDeclarators(JsonArray array) {
        List<VariableDeclarator> declarators = new ArrayList<>();
        for (JsonElement elem : array) {
            JsonObject declJson = elem.getAsJsonObject();
            SimpleIdentifier identifier = (SimpleIdentifier) deserialize(declJson.getAsJsonObject("identifier"));
            Expression rvalue = declJson.has("rvalue") && !declJson.get("rvalue").isJsonNull()
                    ? deserializeExpression(declJson.getAsJsonObject("rvalue")) : null;
            declarators.add(new VariableDeclarator(identifier, rvalue));
        }
        return declarators;
    }

    private VariableDeclaration deserializeVariableDeclaration(JsonObject json) {
        Type varType = (Type) deserialize(json.getAsJsonObject("var_type"));
        List<VariableDeclarator> declarators = deserializeVariableDeclarators(json.getAsJsonArray("declarators"));
        return new VariableDeclaration(varType, declarators);
    }

    private Comprehension.ComprehensionItem deserializeComprehensionItem(JsonObject json) {
        String type = json.get("type").getAsString();
        if ("key_value_pair".equals(type)) {
            return (KeyValuePair) deserialize(json);
        } else if ("list_comprehension_item".equals(type)) {
            Expression expr = deserializeExpression(json.getAsJsonObject("expression"));
            return new Comprehension.ListItem(expr);
        } else if ("set_comprehension_item".equals(type)) {
            Expression expr = deserializeExpression(json.getAsJsonObject("expression"));
            return new Comprehension.SetItem(expr);
        }
        throw new MeaningTreeSerializationException("Unknown comprehension item type: " + type);
    }

    private Label deserializeLabel(JsonObject json) {
        short id = json.get("id").getAsShort();

        if (!json.has("attr") || json.get("attr").isJsonNull()) {
            return new Label(id);
        }

        JsonElement el = json.get("attr");
        Object attr;

        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();

            if (p.isBoolean()) {
                attr = p.getAsBoolean();
            } else if (p.isNumber()) {
                // стратегия: всегда Number
                attr = p.getAsNumber();
            } else if (p.isString()) {
                attr = p.getAsString();
            } else {
                throw new MeaningTreeSerializationException("Unsupported primitive type");
            }

        } else {
            // JsonObject / JsonArray
            attr = el;
        }

        return new Label(id, attr);
    }
    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null) {
            return null;
        }
        String enumName = TransliterationUtils.snakeToCamel(value).toUpperCase();
        try {
            return Enum.valueOf(enumClass, enumName);
        } catch (IllegalArgumentException e) {
            // Try direct match
            for (E enumConstant : enumClass.getEnumConstants()) {
                if (enumConstant.name().equalsIgnoreCase(enumName) ||
                        enumConstant.name().equalsIgnoreCase(value)) {
                    return enumConstant;
                }
            }
            throw new MeaningTreeSerializationException("Cannot parse enum " + enumClass.getSimpleName() + " from: " + value);
        }
    }
}