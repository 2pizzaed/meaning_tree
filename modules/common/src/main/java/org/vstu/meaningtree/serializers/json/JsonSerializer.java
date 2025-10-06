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
        root.add("rootNode", serialize(mt.getRootNode()));
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
            case AddOp op -> serialize(op);
            case SubOp op -> serialize(op);
            case MulOp op -> serialize(op);
            case DivOp op -> serialize(op);
            case ModOp op -> serialize(op);
            case MatMulOp op -> serialize(op);
            case FloorDivOp op -> serialize(op);
            case EqOp op -> serialize(op);
            case GeOp op -> serialize(op);
            case GtOp op -> serialize(op);
            case LeOp op -> serialize(op);
            case LtOp op -> serialize(op);
            case UnaryMinusOp unaryMinusOp -> serialize(unaryMinusOp);
            case UnaryPlusOp unaryPlusOp -> serialize(unaryPlusOp);
            case InstanceOfOp op -> serialize(op);
            case NotEqOp op -> serialize(op);
            case ShortCircuitAndOp op -> serialize(op);
            case ShortCircuitOrOp op -> serialize(op);

            case LongCircuitAndOp op -> serialize(op);
            case LongCircuitOrOp op -> serialize(op);

            case PowOp op -> serialize(op);
            case NotOp op -> serialize(op);
            case PostfixIncrementOp inc -> serialize(inc);
            case PostfixDecrementOp dec -> serialize(dec);
            case PrefixIncrementOp inc -> serialize(inc);
            case PrefixDecrementOp dec -> serialize(dec);
            case BitwiseAndOp bitwiseAndOp -> serialize(bitwiseAndOp);
            case BitwiseOrOp bitwiseOrOp -> serialize(bitwiseOrOp);
            case XorOp xorOp -> serialize(xorOp);
            case InversionOp inversionOp -> serialize(inversionOp);
            case LeftShiftOp leftShiftOp -> serialize(leftShiftOp);
            case RightShiftOp rightShiftOp -> serialize(rightShiftOp);
            case ContainsOp op -> serialize(op);
            case ReferenceEqOp op -> serialize(op);
            case TernaryOperator ternaryOperator -> serialize(ternaryOperator);

            // Literals
            case FloatLiteral l -> serialize(l);
            case IntegerLiteral l -> serialize(l);
            case StringLiteral l -> serialize(l);
            case NullLiteral l -> serialize(l);
            case BoolLiteral l -> serialize(l);
            case CharacterLiteral l -> serialize(l);

            case ArrayLiteral l -> serialize(l);
            case ListLiteral l -> serialize(l);
            case SetLiteral l -> serialize(l);
            case UnmodifiableListLiteral l -> serialize(l);
            case InterpolatedStringLiteral l -> serialize(l);

            case PointerMemberAccess memAcc -> serialize(memAcc);
            case PointerPackOp packOp -> serialize(packOp);
            case PointerUnpackOp unpackOp -> serialize(unpackOp);

            case ArrayInitializer arrInit -> serialize(arrInit);
            case CastTypeExpression castType -> serialize(castType);
            case CommaExpression comma -> serialize(comma);
            case DeleteExpression del -> serialize(del);
            case KeyValuePair kv -> serialize(kv);
            case MemoryAllocationCall memAlloc -> serialize(memAlloc);
            case MemoryFreeCall memFreeAlloc -> serialize(memFreeAlloc);
            case SizeofExpression sizeofExpr -> serialize(sizeofExpr);
            case MemberAccess memberAccess -> serialize(memberAccess);
            case ExpressionSequence exprSeq -> serialize(exprSeq);
            case ThreeWayComparisonOp threeWayComparisonOp -> serialize(threeWayComparisonOp);
            case PlacementNewExpression plNew -> serialize(plNew);
            case ObjectNewExpression newExpr -> serialize(newExpr);
            case QualifiedIdentifier ident -> serialize(ident);
            case ScopedIdentifier scopedIdentifier -> serialize(scopedIdentifier);
            case SelfReference selfRef -> serialize(selfRef);
            case SuperClassReference superClassRef -> serialize(superClassRef);
            case Alias alias -> serialize(alias);
            case SimpleIdentifier expr -> serialize(expr);
            case ContainerBasedComprehension compPh -> serialize(compPh);
            case RangeBasedComprehension compPh -> serialize(compPh);
            case StaticImportAll staticImportAll -> serialize(staticImportAll);
            case StaticImportMembersFromModule staticImportMembersFromModule -> serialize(staticImportMembersFromModule);
            case ImportAllFromModule importAllFromModule -> serialize(importAllFromModule);
            case ImportMembersFromModule importMembersFromModule -> serialize(importMembersFromModule);
            case ImportModule importModule -> serialize(importModule);
            case ImportModules importModules -> serialize(importModules);
            case PackageDeclaration packageDeclaration -> serialize(packageDeclaration);
            case ReturnStatement stmt -> serialize(stmt);
            case CompoundAssignmentStatement stmt -> serialize(stmt);
            case MultipleAssignmentStatement stmt -> serialize(stmt);

            case BasicCaseBlock caseBlock -> serialize(caseBlock);
            case DefaultCaseBlock defaultCaseBlock -> serialize(defaultCaseBlock);
            case FallthroughCaseBlock fallthroughCaseBlock -> serialize(fallthroughCaseBlock);

            // Expressions
            case ParenthesizedExpression expr -> serialize(expr);
            case AssignmentExpression expr -> serialize(expr);
            case CompoundComparison cmp -> serialize(cmp);
            case DeleteStatement del -> serialize(del);
            case FormatInput input -> serialize(input);
            case FormatPrint print -> serialize(print);
            case PointerInputCommand command -> serialize(command);
            case InputCommand command -> serialize(command);
            case PrintValues printValues -> serialize(printValues);
            case ConstructorCall call -> serialize(call);
            case MethodCall call -> serialize(call);
            case FunctionCall funcCall -> serialize(funcCall);
            case IndexExpression indexExpression -> serialize(indexExpression);
            case Range range -> serialize(range);

            case NumericType t -> serialize(t);
            case PointerType t -> serialize(t);
            case ArrayType t -> serialize(t);
            case BooleanType t -> serialize(t);
            case ReferenceType t -> serialize(t);
            case StringType t -> serialize(t);
            case Shape t -> serialize(t);
            case PlainCollectionType t -> serialize(t);
            case GenericInterface t -> serialize(t);
            case GenericUserType t -> serialize(t);
            case NoReturn t -> serialize(t);
            case UnknownType t -> serialize(t);
            case UserType t -> serialize(t);

            case ClassDefinition cd -> serialize(cd);
            case ObjectConstructorDefinition ocd -> serialize(ocd);
            case ObjectDestructorDefinition ocdef -> serialize(ocdef);
            case MethodDefinition md -> serialize(md);
            case FunctionDefinition fd -> serialize(fd);
            case DefinitionArgument defArg -> serialize(defArg);
            case DeclarationArgument declarationArgument -> serialize(declarationArgument);
            case Annotation anno -> serialize(anno);
            case ClassDeclaration classDeclaration -> serialize(classDeclaration);
            case ObjectConstructorDeclaration objectConstructorDefinition -> serialize(objectConstructorDefinition);
            case ObjectDestructorDeclaration objectDestructorDefinition -> serialize(objectDestructorDefinition);
            case SeparatedVariableDeclaration separatedVariableDeclaration -> serialize(separatedVariableDeclaration);
            case FieldDeclaration fieldDeclaration -> serialize(fieldDeclaration);
            case MethodDeclaration methodDeclaration -> serialize(methodDeclaration);
            case FunctionDeclaration functionDeclaration -> serialize(functionDeclaration);

            // Statements
            case AssignmentStatement stmt -> serialize(stmt);
            case VariableDeclaration stmt -> serialize(stmt);
            case EmptyStatement stmt -> serialize(stmt);
            case CompoundStatement stmt -> serialize(stmt);
            case ExpressionStatement stmt -> serialize(stmt);
            case IfStatement stmt -> serialize(stmt);
            case ConditionBranch stmt -> serialize(stmt);
            case InfiniteLoop infLoop -> serialize(infLoop);
            case GeneralForLoop stmt -> serialize(stmt);
            case RangeForLoop rangeLoop -> serialize(rangeLoop);
            case WhileLoop whileLoop -> serialize(whileLoop);
            case BreakStatement stmt -> serialize(stmt);
            case ContinueStatement stmt -> serialize(stmt);
            case SwitchStatement switchStatement -> serialize(switchStatement);
            case DoWhileLoop doWhileLoop -> serialize(doWhileLoop);

            case ProgramEntryPoint entryPoint -> serialize(entryPoint);
            case Comment comment -> serialize(comment);

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
    private JsonObject serialize(@NotNull AddOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SubOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MulOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull DivOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ModOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MatMulOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull FloorDivOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PowOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull EqOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull GeOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull GtOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull LeOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull LtOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull NotEqOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ReferenceEqOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ShortCircuitAndOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ShortCircuitOrOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull NotOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull UnaryMinusOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull UnaryPlusOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PostfixIncrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PostfixDecrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PrefixIncrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PrefixDecrementOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull BitwiseAndOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull BitwiseOrOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull XorOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull InversionOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("operand", serialize(op.getArgument()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull LeftShiftOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull RightShiftOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull InstanceOfOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("expression", serialize(op.getLeft()));
        json.add("type", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ContainsOp op) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("element", serialize(op.getLeft()));
        json.add("collection", serialize(op.getRight()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull TernaryOperator op) {
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
    private JsonObject serialize(@NotNull FloatLiteral floatLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(floatLiteral));
        json.addProperty("value", floatLiteral.getValue());
        json.addProperty("is_double", floatLiteral.isDoublePrecision());

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull IntegerLiteral integerLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(integerLiteral));
        json.addProperty("value", integerLiteral.getLongValue());
        json.addProperty("repr", integerLiteral.getIntegerRepresentation().toString());

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull StringLiteral stringLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stringLiteral));
        json.addProperty("value", stringLiteral.getUnescapedValue());

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull NullLiteral nullLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(nullLiteral));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull BoolLiteral boolLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(boolLiteral));
        json.addProperty("value", boolLiteral.getValue());

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull CharacterLiteral characterLiteral) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(characterLiteral));
        json.addProperty("value", characterLiteral.getValue());

        return json;
    }


    /* -----------------------------
    |         Expressions           |
    ------------------------------ */

    @NotNull
    private JsonObject serialize(@NotNull ParenthesizedExpression expr) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expression", serialize(expr.getExpression()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SimpleIdentifier expr) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.addProperty("name", expr.getName());

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull AssignmentExpression expr) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target", serialize(expr.getLValue()));
        json.add("value", serialize(expr.getRValue()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull CompoundComparison cmp) {
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
    private JsonObject serialize(@NotNull FunctionCall funcCall) {
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
    private JsonObject serialize(@NotNull IndexExpression indexExpression) {
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
    private JsonObject serialize(@NotNull AssignmentStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("target", serialize(stmt.getLValue()));
        json.add("value", serialize(stmt.getRValue()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull EmptyStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull VariableDeclaration stmt) {
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
        JsonArray anno = new JsonArray();
        for (var t : stmt.getAnnotations()) anno.add(serialize(t));
        json.add("annotations", anno);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull CompoundStatement stmt) {
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
    private JsonObject serialize(@NotNull ExpressionStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("expression", serialize(stmt.getExpression()));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull IfStatement stmt) {
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
    private JsonObject serialize(@NotNull ConditionBranch branch) {
        JsonObject branchJson = new JsonObject();

        if (branch.getCondition() != null) {
            branchJson.add("condition", serialize(branch.getCondition()));
        }

        branchJson.add("body", serialize(branch.getBody()));
        branchJson.addProperty("id", branch.getId());
        branchJson.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(branch));

        return branchJson;
    }


    // TODO: сделать это как таковое
    @NotNull
    private JsonObject serialize(@NotNull GeneralForLoop stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        if (stmt.getInitializer() != null) {
            json.add("initializer", serialize((Node) stmt.getInitializer()));
        }

        if (stmt.getCondition() != null) {
            json.add("condition", serialize(stmt.getCondition()));
        }

        if (stmt.getUpdate() != null) {
            json.add("update", serialize(stmt.getUpdate()));
        }

        json.add("body", serialize(stmt.getBody()));
        json.addProperty("id", stmt.getId());
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull Range range) {
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
    private JsonObject serialize(@NotNull RangeForLoop stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("identifier", serialize(stmt.getIdentifier()));
        json.add("range", serialize(stmt.getRange()));
        json.add("body", serialize(stmt.getBody()));

        json.addProperty("id", stmt.getId());
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull WhileLoop stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        json.add("condition", serialize(stmt.getCondition()));
        json.add("body", serialize(stmt.getBody()));

        json.addProperty("id", stmt.getId());
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull BreakStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ContinueStatement stmt) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SwitchStatement stmt) {
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
    private JsonObject serialize(@NotNull DoWhileLoop stmt) {
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
    private JsonObject serialize(@NotNull ProgramEntryPoint entryPoint) {
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
    private JsonObject serialize(@NotNull Comment comment) {
        JsonObject json = new JsonObject();

        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(comment));
        json.addProperty("content", comment.getUnescapedContent());
        json.addProperty("is_multiline", comment.isMultiline());

        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull LongCircuitAndOp op) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull LongCircuitOrOp op) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(op));
        json.add("left_operand", serialize(op.getLeft()));
        json.add("right_operand", serialize(op.getRight()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ArrayLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ListLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SetLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull UnmodifiableListLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray elements = new JsonArray();
        for (var el : l.allChildren()) elements.add(serialize(el));
        json.add("elements", elements);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull InterpolatedStringLiteral l) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(l));
        JsonArray parts = new JsonArray();
        for (var part : l.components()) parts.add(serialize(part));
        json.add("components", parts);
        json.addProperty("type", enumToValue(l.getStringType()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PointerMemberAccess expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expression", serialize(expr.getExpression()));
        json.add("member", serialize(expr.getMember()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PointerPackOp expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("operand", serialize(expr.getArgument()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PointerUnpackOp expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("operand", serialize(expr.getArgument()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ConstructorCall call) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(call));
        JsonArray args = new JsonArray();
        for (var arg : call.getArguments()) args.add(serialize(arg));
        json.add("arguments", args);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MethodCall call) {
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
    private JsonObject serialize(@NotNull ReturnStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        if (stmt.getExpression() != null) {
            json.add("expression", serialize(stmt.getExpression()));
        }
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull CompoundAssignmentStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        JsonArray assignments = new JsonArray();
        for (var t : stmt.getAssignments()) assignments.add(serialize(t));
        json.add("assignments", assignments);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MultipleAssignmentStatement stmt) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(stmt));
        JsonArray targets = new JsonArray();
        for (var t : stmt.getStatements()) targets.add(serialize(t));
        json.add("targets", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull BasicCaseBlock block) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(block));
        json.add("body", serialize(block.getBody()));
        json.add("match_value", serialize(block.getMatchValue()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull DefaultCaseBlock block) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(block));
        json.add("body", serialize(block.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull FallthroughCaseBlock block) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(block));
        json.add("match_value", serialize(block.getMatchValue()));
        json.add("body", serialize(block.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ArrayInitializer expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getValues()) targets.add(serialize(t));
        json.add("elements", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull CastTypeExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target_type", serialize(expr.getCastType()));
        json.add("value", serialize(expr.getValue()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull CommaExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getExpressions()) targets.add(serialize(t));
        json.add("expressions", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull DeleteExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expr", serialize(expr.getTarget()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull DeleteStatement expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expr", serialize(expr.getTarget()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull KeyValuePair expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("key", serialize(expr.key()));
        json.add("value", serialize(expr.value()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MemoryAllocationCall expr) {
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
    private JsonObject serialize(@NotNull MemoryFreeCall expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("value", serialize(expr.getArguments().getFirst()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SizeofExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("value", serialize(expr.getExpression()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MemberAccess expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("expression", serialize(expr.getExpression()));
        json.add("member", serialize(expr.getMember()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ExpressionSequence expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getExpressions()) targets.add(serialize(t));
        json.add("expressions", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ThreeWayComparisonOp expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("left_operand", serialize(expr.getLeft()));
        json.add("right_operand", serialize(expr.getRight()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PlacementNewExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target_type", serialize(expr.getType()));
        JsonArray targets = new JsonArray();
        for (var t : expr.getConstructorArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ObjectNewExpression expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("target_type", serialize(expr.getType()));
        JsonArray targets = new JsonArray();
        for (var t : expr.getConstructorArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull QualifiedIdentifier expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("scope", serialize(expr.getScope()));
        json.add("member", serialize(expr.getMember()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ScopedIdentifier expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getScopeResolution()) targets.add(serialize(t));
        json.add("identifiers", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SelfReference expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.addProperty("name", expr.getName());
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SuperClassReference expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.addProperty("name", expr.getName());
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull Alias expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("realName", serialize(expr.getRealName()));
        json.add("alias", serialize(expr.getAlias()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull Comprehension.ComprehensionItem expr) {
        if (expr instanceof KeyValuePair) return serialize((KeyValuePair) expr);

        JsonObject json = new JsonObject();
        json.addProperty("type", "%s_comprehension_item".formatted(switch (expr.getClass().getName()) {
            case "SetItem"-> "set";
            default -> "list";
        }));
        json.add("expression", serialize(expr));

        return json;
    }


    @NotNull
    private JsonObject serialize(@NotNull ContainerBasedComprehension expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("container_item", serialize(expr.getContainerItemDeclaration()));
        json.add("container", serialize(expr.getContainerExpression()));
        json.add("item", serialize(expr.getItem()));
        return json;
    }


    @NotNull
    private JsonObject serialize(@NotNull RangeBasedComprehension expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("item", serialize(expr.getItem()));
        json.add("range", serialize(expr.getRange()));
        json.add("identifier", serialize(expr.getRangeVariableIdentifier()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull FormatInput expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        json.add("format_string", serialize(expr.getFormatString()));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull FormatPrint expr) {
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
    private JsonObject serialize(@NotNull PointerInputCommand expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        json.add("target", serialize(expr.getTargetString()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull InputCommand expr) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(expr));
        JsonArray targets = new JsonArray();
        for (var t : expr.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PrintValues expr) {
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
    private JsonObject serialize(@NotNull NumericType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TransliterationUtils.camelToSnake(t.getClass().getSimpleName()));
        json.addProperty("size", t.size);
        if (t instanceof IntType intt) {
            json.addProperty("unsigned", intt.isUnsigned);
        }
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull BooleanType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PointerType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("target_type", serialize(t.getTargetType()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ReferenceType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("target_type", serialize(t.getTargetType()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull StringType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.addProperty("char_size", t.getCharSize());
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ArrayType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("shape", serialize(t.getShape()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PlainCollectionType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.add("target_type", serialize(t.getItemType()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull Shape t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        json.addProperty("dimension_count", t.getDimensionCount());
        JsonArray targets = new JsonArray();
        for (var v : t.getDimensions()) targets.add(serialize(v));
        json.add("dimensions", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull GenericUserType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TransliterationUtils.camelToSnake(t.getClass().getSimpleName()));
        json.add("name", serialize(t.getName()));
        JsonArray targets = new JsonArray();
        for (var v : t.getTypeParameters()) targets.add(serialize(v));
        json.add("templates", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull NoReturn t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull UnknownType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(t));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull UserType t) {
        JsonObject json = new JsonObject();
        json.addProperty("type", TransliterationUtils.camelToSnake(t.getClass().getSimpleName()));
        json.add("name", serialize(t.getName()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ClassDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ObjectConstructorDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ObjectDestructorDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MethodDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull FunctionDefinition def) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(def));
        json.add("declaration", serialize(def.getDeclaration()));
        json.add("body", serialize(def.getBody()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull DefinitionArgument arg) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(arg));
        json.addProperty("name", arg.getName().getName());
        json.add("initial", serialize(arg.getInitialExpression()));
        json.addProperty("is_dict_unpacking", arg.isDictUnpacking());
        json.addProperty("is_list_unpacking", arg.isListUnpacking());
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull DeclarationArgument arg) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(arg));
        json.add("target_type", serialize(arg.getElementType()));
        json.addProperty("is_dict_unpacking", arg.isDictUnpacking());
        json.addProperty("is_list_unpacking", arg.isListUnpacking());
        json.addProperty("name", arg.getName().getName());
        json.add("initial", serialize(arg.getInitialExpression()));
        JsonArray anno = new JsonArray();
        for (var t : arg.getAnnotations()) anno.add(serialize(t));
        json.add("annotations", anno);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull Annotation anno) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(anno));
        json.add("function", serialize(anno.getFunctionExpression()));
        JsonArray targets = new JsonArray();
        for (var t : anno.getArguments()) targets.add(serialize(t));
        json.add("arguments", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ClassDeclaration decl) {
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
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull SeparatedVariableDeclaration decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        JsonArray vars = new JsonArray();
        for (var t : decl.getDeclarations()) vars.add(serialize(t));
        json.add("declarations", vars);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull MethodDeclaration decl) {
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
    private JsonObject serialize(@NotNull FunctionDeclaration decl) {
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
    private JsonObject serialize(@NotNull StaticImportAll decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull StaticImportMembersFromModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        JsonArray targets = new JsonArray();
        for (var t : decl.getMembers()) targets.add(serialize(t));
        json.add("members", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ImportAllFromModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ImportMembersFromModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        JsonArray targets = new JsonArray();
        for (var t : decl.getMembers()) targets.add(serialize(t));
        json.add("members", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ImportModule decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("module_name", serialize(decl.getModuleName()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull ImportModules decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        JsonArray targets = new JsonArray();
        for (var t : decl.getModulesNames()) targets.add(serialize(t));
        json.add("modules", targets);
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull PackageDeclaration decl) {
        JsonObject json = new JsonObject();
        json.addProperty("type", JsonNodeTypeClassMapper.getTypeForNode(decl));
        json.add("name", serialize(decl.getPackageName()));
        return json;
    }

    @NotNull
    private JsonObject serialize(@NotNull InfiniteLoop loop) {
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
