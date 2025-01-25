package org.vstu.meaningtree.serializers.model;

import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.expressions.*;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.CompoundComparison;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.NewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.PlacementNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.nodes.types.user.Class;
import org.vstu.meaningtree.nodes.types.user.Enum;
import org.vstu.meaningtree.nodes.types.user.*;

import java.util.*;

public class UniversalSerializer implements Serializer<AbstractSerializedNode> {
    @Override
    public SerializedNode serialize(Node node) {
        SerializedNode result =  switch (node) {
            case BinaryExpression expr -> serialize(expr);
            case UnaryExpression expr -> serialize(expr);
            case ParenthesizedExpression expr -> serialize(expr);
            case TernaryOperator ternary -> serialize(ternary);
            case Type type -> serialize(type);
            case Identifier ident -> serialize(ident);
            case MethodCall call -> serialize(call);
            case FunctionCall call -> serialize(call);
            case Literal literal -> serialize(literal);
            case IndexExpression expr -> serialize(expr);
            case CompoundComparison compound -> serialize(compound);
            case AssignmentStatement stmt -> serialize(stmt);
            case AssignmentExpression expr -> serialize(expr);
            case ExpressionSequence sequence -> serialize(sequence);
            case ExpressionStatement stmt -> serialize(stmt);
            case MemberAccess member -> serialize(member);
            case NewExpression newExpr -> serialize(newExpr);
            case SizeofExpression sizeOf -> serialize(sizeOf);
            case Shape shape -> serialize(shape);
            case ProgramEntryPoint entryPoint -> serialize(entryPoint);
            case DefinitionArgument defArg -> serialize(defArg);
            case CastTypeExpression castType -> serialize(castType);
            default -> serializeDefault(node);
        };
        if (node.getAssignedValueTag() != null) {
            result.values.put("assignedValueTag", node.getAssignedValueTag());
        }
        return result;
    }

    public SerializedNode serialize(DefinitionArgument defArg) {
        return new SerializedNode("DefinitionArgument", new HashMap<>() {{
            put("name", serialize(defArg.getName()));
            put("value", serialize(defArg.getInitialExpression()));
        }});
    }

    public SerializedNode serialize(SizeofExpression sizeOf) {
        return new SerializedNode("Sizeof", new HashMap<>() {{
            put("value", serialize(sizeOf.getExpression()));
        }});
    }

    public SerializedNode serialize(NewExpression newExpr) {
        return switch (newExpr) {
            case ArrayNewExpression arr -> new SerializedNode("ArrayNew", new HashMap<>() {{
                put("type", serialize(arr.getType()));
                put("shape", serialize(arr.getShape()));
                if (arr.getInitializer() != null) {
                    put("initializer", serialize(arr.getInitializer().getValues()));
                }
            }});
            case PlacementNewExpression objNew -> new SerializedNode("PlacementNew", new HashMap<>() {{
                put("type", serialize(objNew.getType()));
                put("arguments", serialize(objNew.getConstructorArguments()));
            }});
            case ObjectNewExpression objNew -> new SerializedNode("ObjectNew", new HashMap<>() {{
                put("type", serialize(objNew.getType()));
                put("arguments", serialize(objNew.getConstructorArguments()));
            }});
            default -> throw new MeaningTreeException("Invalid new expression for serializer");
        };
    }

    public SerializedNode serialize(CastTypeExpression castType) {
        return new SerializedNode("CastTypeExpression", new HashMap<>() {{
            put("type", serialize(castType.getCastType()));
            put("expression", serialize(castType.getValue()));
        }});
    }

    public SerializedNode serialize(Type type) {
        return switch (type) {
            case BooleanType bool -> new SerializedNode("BooleanType", new HashMap<>());
            case CharacterType chart -> new SerializedNode("CharacterType", new HashMap<>(), new HashMap<>() {{
                put("size", chart.size);
            }});
            case FloatType floatt -> new SerializedNode("FloatType", new HashMap<>(), new HashMap<>() {{
                put("size", floatt.size);
            }});
            case IntType intt -> new SerializedNode("IntType", new HashMap<>(), new HashMap<>() {{
                put("size", intt.size);
                put("isUnsigned", intt.isUnsigned);
            }});
            case PointerType ptr -> new SerializedNode("PointerType", new HashMap<>() {{
                put("type", serialize(ptr.getTargetType()));
            }});
            case ReferenceType ptr -> new SerializedNode("ReferenceType", new HashMap<>() {{
                put("type", serialize(ptr.getTargetType()));
            }});
            case StringType str -> new SerializedNode("StringType", new HashMap<>(), new HashMap<>() {{
                put("charSize", str.charSize);
            }});
            case ArrayType arr -> new SerializedNode("ArrayType", new HashMap<>() {{
                put("type", serialize(arr.getItemType()));
                put("shape", serialize(arr.getShape()));
            }});
            case DictionaryType dict -> new SerializedNode("DictionaryType", new HashMap<>() {{
                put("keyType", serialize(dict.getKeyType()));
                put("valueType", serialize(dict.getValueType()));
            }});
            case ListType arr -> new SerializedNode("ListType", new HashMap<>() {{
                put("type", serialize(arr.getItemType()));
            }});
            case SetType arr -> new SerializedNode("SetType", new HashMap<>() {{
                put("type", serialize(arr.getItemType()));
            }});
            case UnmodifiableListType arr -> new SerializedNode("UnmodifiableListType", new HashMap<>() {{
                put("type", serialize(arr.getItemType()));
            }});
            case UnknownType unknownType -> new SerializedNode("UnknownType", new HashMap<>(), new HashMap<>());
            case NoReturn noRet -> new SerializedNode("NoReturn", new HashMap<>(), new HashMap<>());
            case Class cls -> new SerializedNode("Class", new HashMap<>() {{
                put("name", serialize(cls.getQualifiedName()));
            }});
            case Enum cls -> new SerializedNode("Enum", new HashMap<>() {{
                put("name", serialize(cls.getQualifiedName()));
            }});
            case Structure cls -> new SerializedNode("Structure", new HashMap<>() {{
                put("name", serialize(cls.getQualifiedName()));
            }});
            case Interface cls -> new SerializedNode("Interface", new HashMap<>() {{
                put("name", serialize(cls.getQualifiedName()));
            }});
            case GenericClass generic -> new SerializedNode("GenericClass", new HashMap<>() {{
                put("name", serialize(generic.getQualifiedName()));
                put("templateParameters", serialize(generic.getTypeParameters()));
            }});
            default -> new SerializedNode(type.getClass().getSimpleName(), new HashMap<>(), new HashMap<>());
        };
    }

    public SerializedNode serialize(Shape shape) {
        return new SerializedNode("Shape", new HashMap<>() {{
            put("dimensions", serialize(shape.getDimensions()));
        }}, new HashMap<>() {{
            put("dimensionCount", shape.getDimensionCount());
        }});
    }

    public SerializedListNode serialize(List<? extends Node> nodes) {
        return new SerializedListNode(nodes.stream().map((Node node) -> serialize(node)).toList());
    }

    public SerializedListNode serialize(Node[] nodes) {
        return new SerializedListNode(Arrays.stream(nodes).map(this::serialize).toList());
    }

    public SerializedNode serialize(BinaryExpression binOp) {
        return new SerializedNode(binOp.getNodeUniqueName().replace("org.vstu.meaningtree.nodes.", ""),
                new HashMap<>() {{
                    put("left", serialize(binOp.getLeft()));
                    put("right", serialize(binOp.getRight()));
                }}, new HashMap<>() {{
                    if (binOp instanceof ReferenceEqOp eqOp) {
                        put("negative", eqOp.isNegative());
                    } else if (binOp instanceof ContainsOp cntOp) {
                        put("negative", cntOp.isNegative());
                    }
                }}
        );
    }

    public SerializedNode serialize(ProgramEntryPoint entryPoint) {
        return new SerializedNode("ProgramEntryPoint", new HashMap<>() {{
            put("body", serialize(entryPoint.getBody()));
        }});
    }

    public SerializedNode serialize(TernaryOperator ternaryOp) {
        return new SerializedNode("TernaryOperator",
                new HashMap<>() {{
                    put("condition", serialize(ternaryOp.getCondition()));
                    put("then", serialize(ternaryOp.getThenExpr()));
                    put("else", serialize(ternaryOp.getElseExpr()));
                }}
        );
    }

    public SerializedNode serialize(UnaryExpression unaryOp) {
        return new SerializedNode(unaryOp.getNodeUniqueName().replace("org.vstu.meaningtree.nodes.", ""),
                new HashMap<>() {{
                    put("arg", serialize(unaryOp.getArgument()));
                }}
        );
    }

    public SerializedNode serialize(FunctionCall call) {
        return new SerializedNode("FunctionCall", new HashMap<>() {{
            if (call.getFunction() != null) {
                put("name", serialize(call.getFunction()));
            }
            put("args", serialize(call.getArguments()));
            if (call instanceof PrintValues p) {
                if (p.separator != null) put("separator", serialize(p.separator));
                if (p.end != null) put("end", serialize(p.end));
            }
        }}, new HashMap<>() {{
            if (call instanceof PrintCommand || call instanceof InputCommand) {
                put("spec", call.getClass().getSimpleName());
            }
        }});
    }

    public SerializedNode serialize(MethodCall call) {
        return new SerializedNode("MethodCall", new HashMap<>() {{
            put("object", serialize(call.getObject()));
            put("name", serialize(call.getFunction()));
            put("args", serialize(call.getArguments()));
        }});
    }

    public SerializedNode serialize(Identifier ident) {
        return switch (ident) {
            case SimpleIdentifier simple -> new SerializedNode("SimpleIdentifier", new HashMap<>(), new HashMap<>() {{
                put("text", simple.toString());
            }});
            case QualifiedIdentifier qualified -> new SerializedNode("QualifiedIdentifier", new HashMap<>() {{
                put("scope", serialize(qualified.getScope()));
                put("member", serialize(qualified.getMember()));
            }});
            case ScopedIdentifier scoped -> new SerializedNode("ScopedIdentifier", new HashMap<>() {{
                put("members", serialize(scoped.getScopeResolution()));
            }});
            default -> throw new MeaningTreeException("Unknown identifier in universal serializer");
        };
    }

    public SerializedNode serialize(Literal literal) {
        return switch (literal) {
            case IntegerLiteral number -> new SerializedNode("Integer", new HashMap<>(), new HashMap<>() {{
                put("text", number.getStringValue(true));
                put("value", number.getLongValue());
            }});
            case FloatLiteral number -> new SerializedNode("Float", new HashMap<>(), new HashMap<>() {{
                put("text", number.getStringValue(true));
                put("value", number.getDoubleValue());
            }});
            case InterpolatedStringLiteral istr -> new SerializedNode("InterpolatedString", new HashMap<>() {{
                put("components", serialize(istr.components()));
            }}, new HashMap<>() {{
                put("type", istr.getStringType().name());
            }});
            case StringLiteral str -> new SerializedNode("String", new HashMap<>(), new HashMap<>() {{
                put("text", str.getEscapedValue());
            }});
            case BoolLiteral bool -> new SerializedNode("Boolean", new HashMap<>(), new HashMap<>() {{
                put("text", bool.getValue());
            }});
            case CharacterLiteral chr -> new SerializedNode("Char", new HashMap<>(), new HashMap<>() {{
                put("value", chr.getValue());
            }});
            case NullLiteral nl -> new SerializedNode("Null", new HashMap<>(), new HashMap<>());
            case ArrayLiteral arr -> new SerializedNode("Array", new HashMap<>() {{
                put("elements", serialize(arr.getList()));
                if (arr.getTypeHint() != null) put("type", serialize(arr.getTypeHint()));
            }}, new HashMap<>());
            case ListLiteral arr -> new SerializedNode("List", new HashMap<>() {{
                put("elements", serialize(arr.getList()));
                if (arr.getTypeHint() != null) put("type", serialize(arr.getTypeHint()));
            }}, new HashMap<>());
            case UnmodifiableListLiteral arr -> new  SerializedNode("UnmodifiableList", new HashMap<>() {{
                put("elements", serialize(arr.getList()));
                if (arr.getTypeHint() != null) put("type", serialize(arr.getTypeHint()));
            }}, new HashMap<>());
            default -> serializeDefault(literal);
        };
    }

    private SerializedNode serializeDefault(Node node) {
        HashMap<String, AbstractSerializedNode> nodes = new HashMap<>();
        for (Map.Entry<String, Object> child : node.getChildren().entrySet()) {
            if (child.getValue() instanceof List) {
                nodes.put(child.getKey(), serialize((List<Node>) child.getValue()));
            } else if (child.getValue() instanceof Node[]) {
                nodes.put(child.getKey(), serialize((Node[]) child.getValue()));
            } else if (child.getValue() instanceof Node) {
                nodes.put(child.getKey(), serialize((Node) child.getValue()));
            } else if (child.getValue() instanceof Optional opt && opt.isPresent()) {
                nodes.put(child.getKey(), serialize((Node)opt.get()));
            }
        }
        return new SerializedNode(node.getNodeUniqueName(), nodes);
    }

    public SerializedNode serialize(ParenthesizedExpression paren) {
        return new SerializedNode("ParenthesizedExpression", new HashMap<>() {{
            put("value", serialize(paren.getExpression()));
        }});
    }

    public SerializedNode serialize(IndexExpression index) {
        return new SerializedNode("IndexExpression", new HashMap<>() {{
            put("expr", serialize(index.getExpr()));
            put("index", serialize(index.getIndex()));
        }}, new HashMap<>() {{
            put("preferPointers", index.isPreferPointerRepresentation());
        }});
    }


    public SerializedNode serialize(CompoundComparison compound) {
        return new SerializedNode("CompoundComparison", new HashMap<>() {{
            put("members", serialize(compound.getComparisons()));
        }});
    }

    public SerializedNode serialize(AssignmentExpression expr) {
        return new SerializedNode("AssignmentExpression", new HashMap<>() {{
            put("left", serialize(expr.getLValue()));
            put("right", serialize(expr.getRValue()));
        }}, new HashMap<>() {{
            put("augmentedOp", expr.getAugmentedOperator().toString());
        }});
    }

    public SerializedNode serialize(AssignmentStatement stmt) {
        return new SerializedNode("AssignmentStatement", new HashMap<>() {{
            put("left", serialize(stmt.getLValue()));
            put("right", serialize(stmt.getRValue()));
        }}, new HashMap<>() {{
            put("augmentedOp", stmt.getAugmentedOperator().toString());
        }});
    }

    public SerializedNode serialize(ExpressionStatement stmt) {
        return new SerializedNode("ExpressionStatement", new HashMap<>() {{
            put("expr", serialize(stmt.getExpression()));
        }});
    }

    public SerializedNode serialize(ExpressionSequence seq) {
        return new SerializedNode(seq.getClass().getSimpleName(), new HashMap<>() {{
            put("exprs", serialize(seq.getExpressions()));
        }});
    }

    public SerializedNode serialize(MemberAccess access) {
        return new SerializedNode(access.getClass().getSimpleName(), new HashMap<>() {{
            put("owner", serialize(access.getExpression()));
            put("member", serialize(access.getMember()));
        }});
    }
}
