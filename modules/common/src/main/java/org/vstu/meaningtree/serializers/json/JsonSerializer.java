package org.vstu.meaningtree.serializers.json;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.nodes.Comment;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.*;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
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
import org.vstu.meaningtree.nodes.statements.conditions.components.BasicCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.conditions.components.DefaultCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.FallthroughCaseBlock;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.*;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.ArrayType;
import org.vstu.meaningtree.nodes.types.containers.PlainCollectionType;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.serializers.model.Serializer;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.SourceMap;
import org.vstu.meaningtree.utils.TransliterationUtils;
import org.vstu.meaningtree.utils.tokens.*;

import java.util.Collection;
import java.util.Objects;

public class JsonSerializer implements Serializer<JsonObject> {
    @Override
    public JsonObject serialize(MeaningTree mt) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "meaning_tree");
        root.add("root_node", serialize(mt.getRootNode()));
        var allLabels = mt.getAllLabels();
        if (!allLabels.isEmpty()) {
            root.add("labels", serializeLabels(allLabels));
        }
        return root;
    }

    @Override
    public JsonObject serialize(SourceMap sourceMap) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "source_map");
        root.add("origin", serialize(sourceMap.root()));
        root.addProperty("source_code", sourceMap.code());
        root.addProperty("language", sourceMap.language());
        JsonObject map = new JsonObject();
        for (var entry : sourceMap.map().entrySet()) {
            var pair = new JsonArray();
            pair.add(entry.getValue().getLeft());
            pair.add(entry.getValue().getRight());
            map.add(entry.getKey().toString(), pair);
        }
        root.add("map", map);
        return root;
    }

    @Override
    public JsonObject serialize(TokenList tokenList) {
        JsonObject root = new JsonObject();
        root.addProperty("type", "tokens");
        JsonArray array = new JsonArray();
        for (Token t : tokenList) {
            array.add(serialize(t));
        }
        root.add("items", array);
        return root;
    }

    @Override
    public JsonObject serialize(Token token) {
        if (token == null) {
            return null;
        }
        JsonObject root = new JsonObject();
        root.addProperty("type", TransliterationUtils.camelToSnake(token.getClass().getSimpleName()));
        root.addProperty("is_pseudo", token instanceof PseudoToken);
        if (token instanceof PseudoToken p) {
            root.add("metadata", gson.toJsonTree(p.getAttribute()));
            if (token instanceof Whitespace wp) {
                root.addProperty("has_newlines", wp.hasNewLines());
            }
        }
        if (token instanceof ComplexOperatorToken complexOperatorToken) {
            root.addProperty("token_position", complexOperatorToken.positionOfToken);
            JsonArray tokenValues = new JsonArray();
            for (var t : complexOperatorToken.complexTokenValues) {tokenValues.add(t);}
            root.add("token_values",  tokenValues);
        }

        if (token instanceof OperatorToken operator) {
            root.addProperty("precedence", operator.precedence);
            root.addProperty("associativity", enumToValue(operator.assoc));
            root.addProperty("arity", enumToValue(operator.arity));
            root.addProperty("is_strict_order", operator.isStrictOrder);
            root.addProperty("first_evaluated_operand", enumToValue(operator.getFirstOperandToEvaluation()));
            root.addProperty("optype_metadata", enumToValue(operator.additionalOpType));
        }

        if (token instanceof OperandToken operand) {
            root.addProperty("operand_of", operand.operandOf() != null ? operand.operandOf().getId() : null);
            root.addProperty("operand_pos", enumToValue(operand.operandPosition()));
        }

        root.addProperty("token_type", enumToValue(token.type));
        root.addProperty("value", token.value);
        root.addProperty("id", token.getId());
        root.addProperty("assigned_label", gson.toJson(token.getAssignedValue()));
        root.addProperty("belongs_to", token.belongsTo() != null ? token.belongsTo.getId() : null);
        return root;
    }

    @Override
    public JsonObject serialize(Node node) {
        if (node == null) {
            return null;
        }
        var json = switch (node) {
            // Operators
            case AddOp op -> serializeAddOp(op);
            case SubOp op -> serializeSubOp(op);
            case MulOp op -> serializeMulOp(op);
            case DivOp op -> serializeDivOp(op);
            case ModOp op -> serializeModOp(op);
            case MatMulOp op -> serializeMatMulOp(op);
            case FloorDivOp op -> serializeFloorDivOp(op);
            case EqOp op -> serializeEqOp(op);
            case GeOp op -> serializeGeOp(op);
            case GtOp op -> serializeGtOp(op);
            case LeOp op -> serializeLeOp(op);
            case LtOp op -> serializeLtOp(op);
            case UnaryMinusOp unaryMinusOp -> serializeUnaryMinusOp(unaryMinusOp);
            case UnaryPlusOp unaryPlusOp -> serializeUnaryPlusOp(unaryPlusOp);
            case InstanceOfOp op -> serializeInstanceOfOp(op);
            case NotEqOp op -> serializeNotEqOp(op);
            case ShortCircuitAndOp op -> serializeShortCircuitAndOp(op);
            case ShortCircuitOrOp op -> serializeShortCircuitOrOp(op);

            case LongCircuitAndOp op -> serializeLongCircuitAndOp(op);
            case LongCircuitOrOp op -> serializeLongCircuitOrOp(op);

            case PowOp op -> serializePowOp(op);
            case NotOp op -> serializeNotOp(op);
            case PostfixIncrementOp inc -> serializePostfixIncrementOp(inc);
            case PostfixDecrementOp dec -> serializePostfixDecrementOp(dec);
            case PrefixIncrementOp inc -> serializePrefixIncrementOp(inc);
            case PrefixDecrementOp dec -> serializePrefixDecrementOp(dec);
            case BitwiseAndOp bitwiseAndOp -> serializeBitwiseAndOp(bitwiseAndOp);
            case BitwiseOrOp bitwiseOrOp -> serializeBitwiseOrOp(bitwiseOrOp);
            case XorOp xorOp -> serializeXorOp(xorOp);
            case InversionOp inversionOp -> serializeInversionOp(inversionOp);
            case LeftShiftOp leftShiftOp -> serializeLeftShiftOp(leftShiftOp);
            case RightShiftOp rightShiftOp -> serializeRightShiftOp(rightShiftOp);
            case ContainsOp op -> serializeContainsOp(op);
            case ReferenceEqOp op -> serializeReferenceEqOp(op);
            case TernaryOperator ternaryOperator -> serializeTernaryOperator(ternaryOperator);

            // Literals
            case FloatLiteral l -> serializeFloatLiteral(l);
            case IntegerLiteral l -> serializeIntegerLiteral(l);
            case StringLiteral l -> serializeStringLiteral(l);
            case NullLiteral l -> serializeNullLiteral(l);
            case BoolLiteral l -> serializeBoolLiteral(l);
            case CharacterLiteral l -> serializeCharacterLiteral(l);

            case ArrayLiteral l -> serializeArrayLiteral(l);
            case ListLiteral l -> serializeListLiteral(l);
            case SetLiteral l -> serializeSetLiteral(l);
            case UnmodifiableListLiteral l -> serializeUnmodifiableListLiteral(l);
            case InterpolatedStringLiteral l -> serializeInterpolatedStringLiteral(l);

            case PointerMemberAccess memAcc -> serializePointerMemberAccess(memAcc);
            case PointerPackOp packOp -> serializePointerPackOp(packOp);
            case PointerUnpackOp unpackOp -> serializePointerUnpackOp(unpackOp);

            case ArrayInitializer arrInit -> serializeArrayInitializer(arrInit);
            case CastTypeExpression castType -> serializeCastTypeExpression(castType);
            case CommaExpression comma -> serializeCommaExpression(comma);
            case DeleteExpression del -> serializeDeleteExpression(del);
            case KeyValuePair kv -> serializeKeyValuePair(kv);
            case MemoryAllocationCall memAlloc -> serializeMemoryAllocationCall(memAlloc);
            case MemoryFreeCall memFreeAlloc -> serializeMemoryFreeCall(memFreeAlloc);
            case SizeofExpression sizeofExpr -> serializeSizeofExpression(sizeofExpr);
            case MemberAccess memberAccess -> serializeMemberAccess(memberAccess);
            case ExpressionSequence exprSeq -> serializeExpressionSequence(exprSeq);
            case ThreeWayComparisonOp threeWayComparisonOp -> serializeThreeWayComparisonOp(threeWayComparisonOp);
            case PlacementNewExpression plNew -> serializePlacementNewExpression(plNew);
            case ObjectNewExpression newExpr -> serializeObjectNewExpression(newExpr);
            case QualifiedIdentifier ident -> serializeQualifiedIdentifier(ident);
            case ScopedIdentifier scopedIdentifier -> serializeScopedIdentifier(scopedIdentifier);
            case SelfReference selfRef -> serializeSelfReference(selfRef);
            case SuperClassReference superClassRef -> serializeSuperClassReference(superClassRef);
            case Alias alias -> serializeAlias(alias);
            case SimpleIdentifier expr -> serializeSimpleIdentifier(expr);
            case ContainerBasedComprehension compPh -> serializeContainerBasedComprehension(compPh);
            case RangeBasedComprehension compPh -> serializeRangeBasedComprehension(compPh);
            case StaticImportAll staticImportAll -> serializeStaticImportAll(staticImportAll);
            case StaticImportMembersFromModule staticImportMembersFromModule -> serializeStaticImportMembersFromModule(staticImportMembersFromModule);
            case ImportAllFromModule importAllFromModule -> serializeImportAllFromModule(importAllFromModule);
            case ImportMembersFromModule importMembersFromModule -> serializeImportMembersFromModule(importMembersFromModule);
            case ImportModule importModule -> serializeImportModule(importModule);
            case ImportModules importModules -> serializeImportModules(importModules);
            case PackageDeclaration packageDeclaration -> serializePackageDeclaration(packageDeclaration);
            case ReturnStatement stmt -> serializeReturnStatement(stmt);
            case CompoundAssignmentStatement stmt -> serializeCompoundAssignmentStatement(stmt);
            case MultipleAssignmentStatement stmt -> serializeMultipleAssignmentStatement(stmt);

            case BasicCaseBlock caseBlock -> serializeBasicCaseBlock(caseBlock);
            case DefaultCaseBlock defaultCaseBlock -> serializeDefaultCaseBlock(defaultCaseBlock);
            case FallthroughCaseBlock fallthroughCaseBlock -> serializeFallthroughCaseBlock(fallthroughCaseBlock);

            // Expressions
            case ParenthesizedExpression expr -> serializeParenthesizedExpression(expr);
            case AssignmentExpression expr -> serializeAssignmentExpression(expr);
            case CompoundComparison cmp -> serializeCompoundComparison(cmp);
            case DeleteStatement del -> serializeDeleteStatement(del);
            case FormatInput input -> serializeFormatInput(input);
            case FormatPrint print -> serializeFormatPrint(print);
            case PointerInputCommand command -> serializePointerInputCommand(command);
            case InputCommand command -> serializeInputCommand(command);
            case PrintValues printValues -> serializePrintValues(printValues);
            case ConstructorCall call -> serializeConstructorCall(call);
            case MethodCall call -> serializeMethodCall(call);
            case FunctionCall funcCall -> serializeFunctionCall(funcCall);
            case IndexExpression indexExpression -> serializeIndexExpression(indexExpression);
            case Range range -> serializeRange(range);

            case NumericType t -> serializeNumericType(t);
            case PointerType t -> serializePointerType(t);
            case ArrayType t -> serializeArrayType(t);
            case BooleanType t -> serializeBooleanType(t);
            case ReferenceType t -> serializeReferenceType(t);
            case StringType t -> serializeStringType(t);
            case Shape t -> serializeShape(t);
            case PlainCollectionType t -> serializePlainCollectionType(t);
            case GenericInterface t -> serializeGenericUserType(t);
            case GenericUserType t -> serializeGenericUserType(t);
            case NoReturn t -> serializeNoReturn(t);
            case UnknownType t -> serializeUnknownType(t);
            case UserType t -> serializeUserType(t);

            case ClassDefinition cd -> serializeClassDefinition(cd);
            case ObjectConstructorDefinition ocd -> serializeObjectConstructorDefinition(ocd);
            case ObjectDestructorDefinition ocdef -> serializeObjectDestructorDefinition(ocdef);
            case MethodDefinition md -> serializeMethodDefinition(md);
            case FunctionDefinition fd -> serializeFunctionDefinition(fd);
            case DefinitionArgument defArg -> serializeDefinitionArgument(defArg);
            case DeclarationArgument declarationArgument -> serializeDeclarationArgument(declarationArgument);
            case Annotation anno -> serializeAnnotation(anno);
            case ClassDeclaration classDeclaration -> serializeClassDeclaration(classDeclaration);
            case ObjectConstructorDeclaration objectConstructorDefinition -> serializeMethodDeclaration(objectConstructorDefinition);
            case ObjectDestructorDeclaration objectDestructorDefinition -> serializeMethodDeclaration(objectDestructorDefinition);
            case SeparatedVariableDeclaration separatedVariableDeclaration -> serializeSeparatedVariableDeclaration(separatedVariableDeclaration);
            case FieldDeclaration fieldDeclaration -> serializeVariableDeclaration(fieldDeclaration);
            case MethodDeclaration methodDeclaration -> serializeMethodDeclaration(methodDeclaration);
            case FunctionDeclaration functionDeclaration -> serializeFunctionDeclaration(functionDeclaration);

            // Statements
            case AssignmentStatement stmt -> serializeAssignmentStatement(stmt);
            case VariableDeclaration stmt -> serializeVariableDeclaration(stmt);
            case EmptyStatement stmt -> serializeEmptyStatement(stmt);
            case CompoundStatement stmt -> serializeCompoundStatement(stmt);
            case ExpressionStatement stmt -> serializeExpressionStatement(stmt);
            case IfStatement stmt -> serializeIfStatement(stmt);
            case ConditionBranch stmt -> serializeConditionBranch(stmt);
            case InfiniteLoop infLoop -> serializeInfiniteLoop(infLoop);
            case GeneralForLoop stmt -> serializeGeneralForLoop(stmt);
            case RangeForLoop rangeLoop -> serializeRangeForLoop(rangeLoop);
            case ForEachLoop forEachLoop -> serializeForEachLoop(forEachLoop);
            case WhileLoop whileLoop -> serializeWhileLoop(whileLoop);
            case BreakStatement stmt -> serializeBreakStatement(stmt);
            case ContinueStatement stmt -> serializeContinueStatement(stmt);
            case SwitchStatement switchStatement -> serializeSwitchStatement(switchStatement);
            case DoWhileLoop doWhileLoop -> serializeDoWhileLoop(doWhileLoop);

            case ProgramEntryPoint entryPoint -> serializeProgramEntryPoint(entryPoint);
            case Comment comment -> serializeComment(comment);

            default -> throw new IllegalStateException("Unexpected value: " + node);
        };

        json.addProperty("id", node.getId());
        var labels = node.getAllLabels();
        if (!labels.isEmpty()) {
            json.add("labels", serializeLabels(labels));
        }

        return json;
    }

    /* -----------------------------
    |        Labels helpers        |
    ------------------------------ */
    private final Gson gson = new Gson();

    private JsonArray serializeLabels(Collection<Label> labels) {
        JsonArray array = new JsonArray();
        for (Label label : labels) {
            array.add(serializeLabel(label));
        }
        return array;
    }

    private JsonObject serializeLabel(Label label) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", label.getId());
        if (label.hasAttribute()) {
            // Сериализуем attribute произвольного типа в Json
            JsonElement attrJson = gson.toJsonTree(label.getAttribute());
            obj.add("attr", attrJson);
        }
        return obj;
    }

    /* -----------------------------
    |          Operators            |
    ------------------------------ */

    @NotNull
    private JsonObject serializeAddOp(@NotNull AddOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeSubOp(@NotNull SubOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeMulOp(@NotNull MulOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeDivOp(@NotNull DivOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeModOp(@NotNull ModOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeMatMulOp(@NotNull MatMulOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeFloorDivOp(@NotNull FloorDivOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializePowOp(@NotNull PowOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeEqOp(@NotNull EqOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeGeOp(@NotNull GeOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeGtOp(@NotNull GtOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeLeOp(@NotNull LeOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeLtOp(@NotNull LtOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeNotEqOp(@NotNull NotEqOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeReferenceEqOp(@NotNull ReferenceEqOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeShortCircuitAndOp(@NotNull ShortCircuitAndOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeShortCircuitOrOp(@NotNull ShortCircuitOrOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeNotOp(@NotNull NotOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serializeUnaryMinusOp(@NotNull UnaryMinusOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));
        return json;
    }

    @NotNull
    private JsonObject serializeUnaryPlusOp(@NotNull UnaryPlusOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serializePostfixIncrementOp(@NotNull PostfixIncrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serializePostfixDecrementOp(@NotNull PostfixDecrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serializePrefixIncrementOp(@NotNull PrefixIncrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serializePrefixDecrementOp(@NotNull PrefixDecrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serializeBitwiseAndOp(@NotNull BitwiseAndOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeBitwiseOrOp(@NotNull BitwiseOrOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeXorOp(@NotNull XorOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeInversionOp(@NotNull InversionOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serializeLeftShiftOp(@NotNull LeftShiftOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeRightShiftOp(@NotNull RightShiftOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeInstanceOfOp(@NotNull InstanceOfOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("expression", serialize(op.getLeft()));
        json.add("type", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeContainsOp(@NotNull ContainsOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("element", serialize(op.getLeft()));
        json.add("collection", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serializeTernaryOperator(@NotNull TernaryOperator op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("condition", serialize(op.getCondition()));
        json.add("true_expression", serialize(op.getThenExpr()));
        json.add("false_expression", serialize(op.getElseExpr()));

        return json;
    }

    /* -----------------------------
    |           Literals            |
    ------------------------------ */

    @NotNull
    private JsonObject serializeFloatLiteral(@NotNull FloatLiteral floatLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(floatLiteral));
        json.addProperty("value", floatLiteral.getValue());
        json.addProperty("is_double", floatLiteral.isDoublePrecision());

        return json;
    }

    @NotNull
    private JsonObject serializeIntegerLiteral(@NotNull IntegerLiteral integerLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(integerLiteral));
        json.addProperty("value", integerLiteral.getLongValue());
        json.addProperty("repr", integerLiteral.getIntegerRepresentation().toString());

        return json;
    }

    @NotNull
    private JsonObject serializeStringLiteral(@NotNull StringLiteral stringLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stringLiteral));
        json.addProperty("value", stringLiteral.getUnescapedValue());

        return json;
    }

    @NotNull
    private JsonObject serializeNullLiteral(@NotNull NullLiteral nullLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(nullLiteral));

        return json;
    }

    @NotNull
    private JsonObject serializeBoolLiteral(@NotNull BoolLiteral boolLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(boolLiteral));
        json.addProperty("value", boolLiteral.getValue());

        return json;
    }

    @NotNull
    private JsonObject serializeCharacterLiteral(@NotNull CharacterLiteral characterLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(characterLiteral));
        json.addProperty("value", characterLiteral.getValue());

        return json;
    }


    /* -----------------------------
    |         Expressions           |
    ------------------------------ */

    @NotNull
    private JsonObject serializeParenthesizedExpression(@NotNull ParenthesizedExpression expr) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expression", serialize(expr.getExpression()));

        return json;
    }

    @NotNull
    private JsonObject serializeSimpleIdentifier(@NotNull SimpleIdentifier expr) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.addProperty("name", expr.getName());

        return json;
    }

    @NotNull
    private JsonObject serializeAssignmentExpression(@NotNull AssignmentExpression expr) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target", serialize(expr.getLValue()));
        json.add("value", serialize(expr.getRValue()));

        return json;
    }

    @NotNull
    private JsonObject serializeCompoundComparison(@NotNull CompoundComparison cmp) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(cmp));

        JsonArray comparisons = new JsonArray();
        for (var comparison : cmp.getComparisons()) {
            JsonObject jsonComparison = new JsonObject();
            jsonComparison.add("left", serialize(comparison.getLeft()));
            jsonComparison.addProperty("operator", JsonNodeTypeClassMapper.getTypeForNode(comparison));
            jsonComparison.add("right", serialize(comparison.getRight()));
        }

        json.add("comparisons", comparisons);
        return json;
    }

    @NotNull
    private JsonObject serializeFunctionCall(@NotNull FunctionCall funcCall) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(funcCall));
        json.add("function", serialize(funcCall.getFunction()));

        JsonArray args = new JsonArray();
        for (var arg : funcCall.getArguments()) {
            args.add(serialize(arg));
        }

        json.add("arguments", args);
        return json;
    }

    @NotNull
    private JsonObject serializeIndexExpression(@NotNull IndexExpression indexExpression) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(indexExpression));
        json.add("expr", serialize(indexExpression.getExpression()));
        json.add("index", serialize(indexExpression.getIndex()));

        return json;
    }


    /* -----------------------------
    |          Statements           |
    ------------------------------ */

    @NotNull
    private JsonObject serializeAssignmentStatement(@NotNull AssignmentStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("target", serialize(stmt.getLValue()));
        json.add("value", serialize(stmt.getRValue()));

        return json;
    }

    @NotNull
    private JsonObject serializeEmptyStatement(@NotNull EmptyStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        return json;
    }

    @NotNull
    private JsonObject serializeVariableDeclaration(@NotNull VariableDeclaration stmt) {
        JsonObject json = new JsonObject();

        if (stmt instanceof FieldDeclaration decl) {
            json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
            JsonArray modifiers = new JsonArray();
            for (var t : decl.getModifiers()) modifiers.add(enumToValue(t));
            json.add("modifiers", modifiers);
        } else {
            json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        }

        JsonArray declarators = new JsonArray();
        for (VariableDeclarator varDecl : stmt.getDeclarators()) {
            JsonObject jsonDeclaration = new JsonObject();
            jsonDeclaration.add("identifier", serialize(varDecl.getIdentifier()));
            if (varDecl.getRValue() != null) {
                jsonDeclaration.add("rvalue", serialize(varDecl.getRValue()));
            }
            declarators.add(jsonDeclaration);
        }

        json.add("declarators", declarators);
        json.add("var_type", serialize(stmt.getType()));
        JsonArray anno = new JsonArray();
        for (var t : stmt.getAnnotations()) anno.add(serialize(t));
        json.add("annotations", anno);
        return json;
    }

    @NotNull
    private JsonObject serializeCompoundStatement(@NotNull CompoundStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        JsonArray statements = new JsonArray();
        for (var statement : stmt.getNodes()) {
            statements.add(serialize(statement));
        }

        json.add("statements", statements);
        return json;
    }

    @NotNull
    private JsonObject serializeExpressionStatement(@NotNull ExpressionStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("expression", serialize(stmt.getExpression()));

        return json;
    }

    @NotNull
    private JsonObject serializeIfStatement(@NotNull IfStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        JsonArray branches = new JsonArray();
        for (var branch : stmt.getBranches()) {
            branches.add(serialize(branch));
        }

        json.add("branches", branches);
        if (stmt.hasElseBranch()) {
            json.add("elseBranch", serialize(stmt.getElseBranch()));
        }
        return json;
    }

    @NotNull
    private JsonObject serializeConditionBranch(@NotNull ConditionBranch branch) {
        JsonObject branchJson = new JsonObject();

        if (branch.getCondition() != null) {
            branchJson.add("condition", serialize(branch.getCondition()));
        }

        branchJson.add("body", serialize(branch.getBody()));
        branchJson.addProperty("id", branch.getId());
        branchJson.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(branch));

        return branchJson;
    }


    @NotNull
    private JsonObject serializeGeneralForLoop(@NotNull GeneralForLoop stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        if (stmt.hasInitializer()) {
            json.add("initializer", serialize((Node) stmt.getInitializer()));
        }

        if (stmt.hasCondition()) {
            json.add("condition", serialize(stmt.getCondition()));
        }

        if (stmt.hasUpdate()) {
            json.add("update", serialize(stmt.getUpdate()));
        }

        json.add("body", serialize(stmt.getBody()));
        json.addProperty("id", stmt.getId());
        return json;
    }

    @NotNull
    private JsonObject serializeRange(@NotNull Range range) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(range));
        json.add("start", serialize(range.getStart()));
        json.add("stop", serialize(range.getStop()));
        json.add("step", serialize(range.getStep()));

        json.addProperty("isExcludingStart", range.isExcludingStart());
        json.addProperty("isExcludingEnd", range.isExcludingEnd());

        json.addProperty("rangeType", range.getType().name().toLowerCase());

        json.addProperty("id", range.getId());
        return json;
    }

    @NotNull
    private JsonObject serializeRangeForLoop(@NotNull RangeForLoop stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("identifier", serialize(stmt.getIdentifier()));
        json.add("range", serialize(stmt.getRange()));
        json.add("body", serialize(stmt.getBody()));

        json.addProperty("id", stmt.getId());
        return json;
    }

    @NotNull
    private JsonObject serializeForEachLoop(@NotNull ForEachLoop stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("item", serialize(stmt.getItem()));
        json.add("container", serialize(stmt.getExpression()));
        json.add("body", serialize(stmt.getBody()));

        return json;
    }

    @NotNull
    private JsonObject serializeWhileLoop(@NotNull WhileLoop stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("condition", serialize(stmt.getCondition()));
        json.add("body", serialize(stmt.getBody()));

        json.addProperty("id", stmt.getId());
        return json;
    }

    @NotNull
    private JsonObject serializeBreakStatement(@NotNull BreakStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        return json;
    }

    @NotNull
    private JsonObject serializeContinueStatement(@NotNull ContinueStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        return json;
    }

    @NotNull
    private JsonObject serializeSwitchStatement(@NotNull SwitchStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("expression", serialize(stmt.getTargetExpression()));

        JsonArray cases = new JsonArray();
        for (var switchCase : stmt.getCases()) {
            cases.add(serialize(switchCase.getBody()));
        }

        if (stmt.hasDefaultCase()) {
            json.add("default", serialize(Objects.requireNonNull(stmt.getDefaultCase())));
        }

        json.add("cases", cases);
        return json;
    }

    @NotNull
    private JsonObject serializeDoWhileLoop(@NotNull DoWhileLoop stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("body", serialize(stmt.getBody()));
        json.add("condition", serialize(stmt.getCondition()));

        json.addProperty("id", stmt.getId());
        return json;
    }


    /* -----------------------------
    |            Other              |
    ------------------------------ */

    @NotNull
    private JsonObject serializeProgramEntryPoint(@NotNull ProgramEntryPoint entryPoint) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(entryPoint));

        JsonArray body = new JsonArray();
        for (var entry : entryPoint.getBody()) {
            body.add(serialize(entry));
        }

        json.add("body", body);
        json.addProperty("id", entryPoint.getId());
        return json;
    }

    @NotNull
    private JsonObject serializeComment(@NotNull Comment comment) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(comment));
        json.addProperty("content", comment.getUnescapedContent());
        json.addProperty("is_multiline", comment.isMultiline());

        return json;
    }

    @NotNull
    private JsonObject serializeLongCircuitAndOp(@NotNull LongCircuitAndOp op) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));
        return json;
    }

    @NotNull
    private JsonObject serializeLongCircuitOrOp(@NotNull LongCircuitOrOp op) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));
        return json;
    }

    @NotNull
    private JsonObject serializeArrayLiteral(@NotNull ArrayLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serializeListLiteral(@NotNull ListLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serializeSetLiteral(@NotNull SetLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serializeUnmodifiableListLiteral(@NotNull UnmodifiableListLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serializeInterpolatedStringLiteral(@NotNull InterpolatedStringLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray parts = new JsonArray();
        for (var part : l.components()) parts.add(serialize(part));
        json.add("components", parts);
        json.addProperty("type", enumToValue(l.getStringType()));
        return json;
    }

    @NotNull
    private JsonObject serializePointerMemberAccess(@NotNull PointerMemberAccess expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expression", serialize(expr.getExpression()));
        json.add("member", serialize(expr.getMember()));
        return json;
    }

    @NotNull
    private JsonObject serializePointerPackOp(@NotNull PointerPackOp expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("operand", serialize(expr.getArgument()));
        return json;
    }

    @NotNull
    private JsonObject serializePointerUnpackOp(@NotNull PointerUnpackOp expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("operand", serialize(expr.getArgument()));
        return json;
    }

    @NotNull
    private JsonObject serializeConstructorCall(@NotNull ConstructorCall call) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(call));
        JsonArray args = new JsonArray();
        for (var arg : call.getArguments()) args.add(serialize(arg));
        json.add("arguments", args);
        return json;
    }

    @NotNull
    private JsonObject serializeMethodCall(@NotNull MethodCall call) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(call));
        json.add("receiver", serialize(call.getObject()));
        json.add("method_name", serialize(call.getFunction()));
        JsonArray args = new JsonArray();
        for (var arg : call.getArguments()) args.add(serialize(arg));
        json.add("arguments", args);
        return json;
    }

    @NotNull
    private JsonObject serializeReturnStatement(@NotNull ReturnStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        if (stmt.getExpression() != null) {
            json.add("expression", serialize(stmt.getExpression()));
        }
        return json;
    }

    @NotNull
    private JsonObject serializeCompoundAssignmentStatement(@NotNull CompoundAssignmentStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        JsonArray assignments = new JsonArray();
        for (var t : stmt.getAssignments()) assignments.add(serialize(t));
        json.add("assignments", assignments);
        return json;
    }

    @NotNull
    private JsonObject serializeMultipleAssignmentStatement(@NotNull MultipleAssignmentStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        JsonArray targets = new JsonArray();
        for (var t : stmt.getStatements()) targets.add(serialize(t));
        json.add("targets", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeBasicCaseBlock(@NotNull BasicCaseBlock block) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(block));
        json.add("body", serialize(block.getBody()));
        json.add("match_value", serialize(block.getMatchValue()));
        return json;
    }

    @NotNull
    private JsonObject serializeDefaultCaseBlock(@NotNull DefaultCaseBlock block) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(block));
        json.add("body", serialize(block.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serializeFallthroughCaseBlock(@NotNull FallthroughCaseBlock block) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(block));
        json.add("match_value", serialize(block.getMatchValue()));
        json.add("body", serialize(block.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serializeArrayInitializer(@NotNull ArrayInitializer expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getValues()) targets.add(serialize(t));
        json.add("elements", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeCastTypeExpression(@NotNull CastTypeExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target_type", serialize(expr.getCastType()));
        json.add("value", serialize(expr.getValue()));
        return json;
    }

    @NotNull
    private JsonObject serializeCommaExpression(@NotNull CommaExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getExpressions()) targets.add(serialize(t));
        json.add("expressions", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeDeleteExpression(@NotNull DeleteExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expr", serialize(expr.getTarget()));
        return json;
    }

    @NotNull
    private JsonObject serializeDeleteStatement(@NotNull DeleteStatement expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expr", serialize(expr.getTarget()));
        return json;
    }

    @NotNull
    private JsonObject serializeKeyValuePair(@NotNull KeyValuePair expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("key", serialize(expr.key()));
        json.add("value", serialize(expr.value()));
        return json;
    }

    @NotNull
    private JsonObject serializeMemoryAllocationCall(@NotNull MemoryAllocationCall expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.addProperty("is_clear", expr.isClearAllocation());
        json.add("function", serialize(expr.getFunction()));

        JsonArray args = new JsonArray();
        for (var arg : expr.getArguments()) {
            args.add(serialize(arg));
        }

        json.add("arguments", args);
        return json;
    }

    @NotNull
    private JsonObject serializeMemoryFreeCall(@NotNull MemoryFreeCall expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("value", serialize(expr.getArguments().getFirst()));
        return json;
    }

    @NotNull
    private JsonObject serializeSizeofExpression(@NotNull SizeofExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("value", serialize(expr.getExpression()));
        return json;
    }

    @NotNull
    private JsonObject serializeMemberAccess(@NotNull MemberAccess expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expression", serialize(expr.getExpression()));
        json.add("member", serialize(expr.getMember()));
        return json;
    }

    @NotNull
    private JsonObject serializeExpressionSequence(@NotNull ExpressionSequence expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getExpressions()) targets.add(serialize(t));
        json.add("expressions", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeThreeWayComparisonOp(@NotNull ThreeWayComparisonOp expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("left_operand", serialize(expr.getLeft()));
        json.add("right_operand", serialize(expr.getRight()));
        return json;
    }

    @NotNull
    private JsonObject serializePlacementNewExpression(@NotNull PlacementNewExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target_type", serialize(expr.getType()));
        JsonArray targets = new JsonArray();
        for (var t : expr.getConstructorArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeObjectNewExpression(@NotNull ObjectNewExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target_type", serialize(expr.getType()));
        JsonArray targets = new JsonArray();
        for (var t : expr.getConstructorArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeQualifiedIdentifier(@NotNull QualifiedIdentifier expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("scope", serialize(expr.getScope()));
        json.add("member", serialize(expr.getMember()));
        return json;
    }

    @NotNull
    private JsonObject serializeScopedIdentifier(@NotNull ScopedIdentifier expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getScopeResolution()) targets.add(serialize(t));
        json.add("identifiers", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeSelfReference(@NotNull SelfReference expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.addProperty("name", expr.getName());
        return json;
    }

    @NotNull
    private JsonObject serializeSuperClassReference(@NotNull SuperClassReference expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.addProperty("name", expr.getName());
        return json;
    }

    @NotNull
    private JsonObject serializeAlias(@NotNull Alias expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("realName", serialize(expr.getRealName()));
        json.add("alias", serialize(expr.getAlias()));
        return json;
    }

    @NotNull
    private JsonObject serializeComprehensionItem(@NotNull Comprehension.ComprehensionItem expr) {
        if (expr instanceof KeyValuePair) return serialize((KeyValuePair) expr);

        JsonObject json = new JsonObject();
        json.addProperty("type", "%s_comprehension_item".formatted(switch (expr.getClass().getName()) {
            case "SetItem"-> "set";
            default -> "list";
        }));
        if (expr instanceof Comprehension.ListItem item) {
            json.add("expression", serialize(item.value()));
        } else if (expr instanceof Comprehension.SetItem item) {
            json.add("expression", serialize(item.value()));
        }

        return json;
    }


    @NotNull
    private JsonObject serializeContainerBasedComprehension(@NotNull ContainerBasedComprehension expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("container_item", serialize(expr.getContainerItemDeclaration()));
        json.add("container", serialize(expr.getContainerExpression()));
        json.add("item", serializeComprehensionItem(expr.getItem()));
        return json;
    }


    @NotNull
    private JsonObject serializeRangeBasedComprehension(@NotNull RangeBasedComprehension expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("item", serializeComprehensionItem(expr.getItem()));
        json.add("range", serialize(expr.getRange()));
        json.add("identifier", serialize(expr.getRangeVariableIdentifier()));
        return json;
    }

    @NotNull
    private JsonObject serializeFormatInput(@NotNull FormatInput expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("format_string", serialize(expr.getFormatString()));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeFormatPrint(@NotNull FormatPrint expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("format_string", serialize(expr.getFormatString()));
        json.add("separator", serialize(expr.separator));
        json.add("end", serialize(expr.end));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializePointerInputCommand(@NotNull PointerInputCommand expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        json.add("target", serialize(expr.getTargetString()));
        return json;
    }

    @NotNull
    private JsonObject serializeInputCommand(@NotNull InputCommand expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializePrintValues(@NotNull PrintValues expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("separator", serialize(expr.separator));
        json.add("end", serialize(expr.end));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeNumericType(@NotNull NumericType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TransliterationUtils.camelToSnake(t.getClass().getSimpleName()));
        json.addProperty("size", t.size);
        if (t instanceof IntType intt) {
            json.addProperty("unsigned", intt.isUnsigned);
        }
        return json;
    }

    @NotNull
    private JsonObject serializeBooleanType(@NotNull BooleanType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        return json;
    }

    @NotNull
    private JsonObject serializePointerType(@NotNull PointerType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("target_type", serialize(t.getTargetType()));
        return json;
    }

    @NotNull
    private JsonObject serializeReferenceType(@NotNull ReferenceType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("target_type", serialize(t.getTargetType()));
        return json;
    }

    @NotNull
    private JsonObject serializeStringType(@NotNull StringType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.addProperty("char_size", t.getCharSize());
        return json;
    }

    @NotNull
    private JsonObject serializeArrayType(@NotNull ArrayType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("shape", serialize(t.getShape()));
        return json;
    }

    @NotNull
    private JsonObject serializePlainCollectionType(@NotNull PlainCollectionType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("target_type", serialize(t.getItemType()));
        return json;
    }

    @NotNull
    private JsonObject serializeShape(@NotNull Shape t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.addProperty("dimension_count", t.getDimensionCount());
        JsonArray targets = new JsonArray();
        for (var v : t.getDimensions()) targets.add(serialize(v));
        json.add("dimensions", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeGenericUserType(@NotNull GenericUserType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TransliterationUtils.camelToSnake(t.getClass().getSimpleName()));
        json.add("name", serialize(t.getName()));
        JsonArray targets = new JsonArray();
        for (var v : t.getTypeParameters()) targets.add(serialize(v));
        json.add("templates", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeNoReturn(@NotNull NoReturn t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        return json;
    }

    @NotNull
    private JsonObject serializeUnknownType(@NotNull UnknownType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        return json;
    }

    @NotNull
    private JsonObject serializeUserType(@NotNull UserType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TransliterationUtils.camelToSnake(t.getClass().getSimpleName()));
        json.add("name", serialize(t.getName()));
        return json;
    }

    @NotNull
    private JsonObject serializeClassDefinition(@NotNull ClassDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serializeObjectConstructorDefinition(@NotNull ObjectConstructorDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serializeObjectDestructorDefinition(@NotNull ObjectDestructorDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serializeMethodDefinition(@NotNull MethodDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serializeFunctionDefinition(@NotNull FunctionDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serializeDefinitionArgument(@NotNull DefinitionArgument arg) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(arg));
        json.addProperty("name", arg.getName().getName());
        json.add("initial", serialize(arg.getInitialExpression()));
        json.addProperty("is_dict_unpacking", arg.isDictUnpacking());
        json.addProperty("is_list_unpacking", arg.isListUnpacking());
        return json;
    }

    @NotNull
    private JsonObject serializeDeclarationArgument(@NotNull DeclarationArgument arg) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(arg));
        json.add("target_type", serialize(arg.getElementType()));
        json.addProperty("is_dict_unpacking", arg.isDictUnpacking());
        json.addProperty("is_list_unpacking", arg.isListUnpacking());
        json.addProperty("name", arg.getName().getName());
        JsonArray anno = new JsonArray();
        for (var t : arg.getAnnotations()) anno.add(serialize(t));
        json.add("annotations", anno);
        return json;
    }

    @NotNull
    private JsonObject serializeAnnotation(@NotNull Annotation anno) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(anno));
        json.add("function", serialize(anno.getFunctionExpression()));
        JsonArray targets = new JsonArray();
        for (var t : anno.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeClassDeclaration(@NotNull ClassDeclaration decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        JsonArray targets = new JsonArray();
        for (var t : decl.getModifiers()) targets.add(enumToValue(t));
        json.add("modifiers", targets);
        json.add("name", serialize(decl.getName()));
        JsonArray parTypes = new JsonArray();
        for (var t : decl.getParents()) parTypes.add(serialize(t));
        json.add("parents", parTypes);
        JsonArray genTypes = new JsonArray();
        for (var t : decl.getTypeParameters()) genTypes.add(serialize(t));
        json.add("generic_type_params", genTypes);
        json.add("type_node", serialize(decl.getTypeNode()));
        JsonArray anno = new JsonArray();
        for (var t : decl.getAnnotations()) anno.add(serialize(t));
        json.add("annotations", anno);
        return json;
    }

    @NotNull
    private JsonObject serializeSeparatedVariableDeclaration(@NotNull SeparatedVariableDeclaration decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        JsonArray vars = new JsonArray();
        for (var t : decl.getDeclarations()) vars.add(serialize(t));
        json.add("declarations", vars);
        return json;
    }

    @NotNull
    private JsonObject serializeMethodDeclaration(@NotNull MethodDeclaration decl) {
        JsonObject json = new JsonObject();
        if (decl instanceof ObjectConstructorDeclaration) {
            json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        } else if (decl instanceof ObjectDestructorDeclaration) {
            json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        } else {
            json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
            json.add("return_type", serialize(decl.getReturnType()));
        }
        json.add("owner", serialize(decl.getOwner()));
        json.add("name", serialize(decl.getName()));
        JsonArray anno = new JsonArray();
        for (var t : decl.getAnnotations()) anno.add(serialize(t));
        json.add("annotations", anno);
        JsonArray modifiers = new JsonArray();
        for (var t : decl.getModifiers()) modifiers.add(enumToValue(t));
        json.add("modifiers", modifiers);
        JsonArray targets = new JsonArray();
        for (var t : decl.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeFunctionDeclaration(@NotNull FunctionDeclaration decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("return_type", serialize(decl.getReturnType()));
        json.add("name", serialize(decl.getName()));
        JsonArray anno = new JsonArray();
        for (var t : decl.getAnnotations()) anno.add(serialize(t));
        json.add("annotations", anno);
        JsonArray targets = new JsonArray();
        for (var t : decl.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeStaticImportAll(@NotNull StaticImportAll decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        return json;
    }

    @NotNull
    private JsonObject serializeStaticImportMembersFromModule(@NotNull StaticImportMembersFromModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        JsonArray targets = new JsonArray();
        for (var t : decl.getMembers()) targets.add(serialize(t));
        json.add("members", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeImportAllFromModule(@NotNull ImportAllFromModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        return json;
    }

    @NotNull
    private JsonObject serializeImportMembersFromModule(@NotNull ImportMembersFromModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        JsonArray targets = new JsonArray();
        for (var t : decl.getMembers()) targets.add(serialize(t));
        json.add("members", targets);
        return json;
    }

    @NotNull
    private JsonObject serializeImportModule(@NotNull ImportModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        return json;
    }

    @NotNull
    private JsonObject serializeImportModules(@NotNull ImportModules decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        JsonArray targets = new JsonArray();
        for (var t : decl.getModulesNames()) targets.add(serialize(t));
        json.add("modules", targets);
        return json;
    }

    @NotNull
    private JsonObject serializePackageDeclaration(@NotNull PackageDeclaration decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("name", serialize(decl.getPackageName()));
        return json;
    }

    @NotNull
    private JsonObject serializeInfiniteLoop(@NotNull InfiniteLoop loop) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(loop));
        json.add("body", serialize(loop.getBody()));
        return json;
    }

    public static String enumToValue(Enum<?> e) {
        if (e == null) {
            return null;
        }
        return TransliterationUtils.camelToSnake(e.name());
    }
}
