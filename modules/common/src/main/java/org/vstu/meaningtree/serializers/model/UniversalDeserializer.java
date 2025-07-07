package org.vstu.meaningtree.serializers.model;

import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Node;
import org.vstu.meaningtree.nodes.ProgramEntryPoint;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.BinaryComparison;
import org.vstu.meaningtree.nodes.expressions.comparison.CompoundComparison;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.PlacementNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerMemberAccess;
import org.vstu.meaningtree.nodes.io.FormatInput;
import org.vstu.meaningtree.nodes.io.FormatPrint;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.nodes.types.user.GenericClass;
import org.vstu.meaningtree.nodes.types.user.Structure;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class UniversalDeserializer implements Deserializer<AbstractSerializedNode> {
    public Node deserialize(AbstractSerializedNode abstractSerialized) {
        SerializedNode serialized = (SerializedNode) abstractSerialized;
        Node node = switch (serialized.nodeName) {
            case "ProgramEntryPoint" -> deserializeEntryPoint(serialized);
            case "ParenthesizedExpression" -> deserializeParen(serialized);
            case "TernaryOperator" -> deserializeTernary(serialized);
            case "SimpleIdentifier", "QualifiedIdentifier", "ScopedIdentifier" -> deserializeIdentifier(serialized);
            case "MethodCall" -> deserializeMethodCall(serialized);
            case "CastTypeExpression" -> deserializeTypeCast(serialized);
            case "FunctionCall" -> deserializeFunctionCall(serialized);
            case "Integer", "Float", "String", "Boolean",
                 "Bool", "Null", "Array", "List", "InterpolatedString", "Char", "UnmodifiableList"
                    -> deserializeLiteral(serialized);
            case "IndexExpression" -> deserializeIndex(serialized);
            case "CompoundComparison" -> deserializeCompound(serialized);
            case "ObjectNew", "ArrayNew", "PlacementNew" -> deserializeNew(serialized);
            case "Sizeof" -> deserializeSizeof(serialized);
            case "AssignmentStatement" -> deserializeAssignmentStmt(serialized);
            case "AssignmentExpression" -> deserializeAssignmentExpr(serialized);
            case "ExpressionSequence", "CommaExpression" -> deserializeExprSequence(serialized);
            case "ExpressionStatement" -> deserializeExprStmt(serialized);
            case "PointerMemberAccess" -> deserializePointerMemberAccess(serialized);
            case "MemberAccess"-> deserializeMemberAccess(serialized);
            case "Shape" -> deserializeShape(serialized);
            case "Range" -> deserializeRange(serialized);
            case "DefinitionArgument" -> deserializeDefArg(serialized);
            case "BooleanType", "StringType", "CharacterType",
                 "FloatType", "IntType", "PointerType",
                 "ReferenceType", "ArrayType", "ListType",
                 "DictionaryType", "SetType", "UnmodifiableListType",
                 "Class", "Enum", "Structure", "Interface",
                 "GenericClass", "UnknownType", "NoReturn" -> deserializeType(serialized);
            default -> deserializeOther(serialized);
        };
        for (AbstractSerializedNode listItem : (SerializedListNode) serialized.fields.getOrDefault("labels", new SerializedListNode())) {
            SerializedLabel label = (SerializedLabel) listItem;
            node.setLabel(label.toObject());
        }
        // Backward compatibility
        if (abstractSerialized.values.containsKey("assignedValueTag")) {
            node.setAssignedValueTag(abstractSerialized.values.get("assignedValueTag"));
        }
        return node;
    }

    private Node deserializeRange(SerializedNode serialized) {
        Range.Type type = switch ((int) serialized.values.get("type")) {
            case 0 -> Range.Type.UP;
            case 1 -> Range.Type.DOWN;
            case 2 -> Range.Type.UNKNOWN;
            default -> Range.Type.UP;
        };
        var start = serialized.fields.getOrDefault("start", null);
        var stop = serialized.fields.getOrDefault("stop", null);
        var step = serialized.fields.getOrDefault("step", null);
        return new Range(
                start == null ? null : (Expression) deserialize(start),
                stop == null ? null : (Expression) deserialize(stop),
                step == null ? null : (Expression) deserialize(step),
                (boolean) serialized.values.get("isExcludingStart"),
                (boolean) serialized.values.get("isExcludingEnd"),
                type
        );
    }

    @Override
    public MeaningTree deserializeTree(AbstractSerializedNode serialized) {
        SerializedNode rawTree = (SerializedNode) serialized;
        if (rawTree.nodeName.equals("MeaningTree")) {
            MeaningTree mt = new MeaningTree(deserialize(rawTree.fields.get("rootNode")));
            for (AbstractSerializedNode serializedNode : (SerializedListNode) rawTree.fields.get("labels")) {
                SerializedLabel label = (SerializedLabel) serializedNode;
                mt.setLabel(label.toObject());
            }
            return mt;
        } else {
            return new MeaningTree(deserialize(serialized));
        }
    }

    private Node deserializeDefArg(SerializedNode serialized) {
        if ((boolean) serialized.values.get("isListUnpacking")) {
            return DefinitionArgument.listUnpacking((Expression) deserialize(serialized.fields.get("value")));
        } else if ((boolean) serialized.values.get("isDictUnpacking")) {
            return DefinitionArgument.dictUnpacking((Expression) deserialize(serialized.fields.get("value")));
        }
        SimpleIdentifier name = null;
        if (serialized.fields.containsKey("name") && serialized.fields.get("name") != null) {
            name = (SimpleIdentifier) deserialize(serialized.fields.get("name"));
        }
        return new DefinitionArgument(name,
                (Expression) deserialize(serialized.fields.get("value"))
                );
    }

    private Node deserializeSizeof(SerializedNode serialized) {
        return new SizeofExpression((Expression) deserialize(serialized.fields.get("value")));
    }

    private Node deserializeNew(SerializedNode serialized) {
        return switch (serialized.nodeName) {
            case "ArrayNew" -> new ArrayNewExpression(
                    (Type) deserialize(serialized.fields.get("type")),
                    (Shape) deserialize(serialized.fields.get("shape")),
                    serialized.fields.containsKey("initializer") ? null : new ArrayInitializer((List<Expression>)
                            deserializeList((SerializedListNode) serialized.fields.get("initializer")))
            );
            case "ObjectNew" -> new ObjectNewExpression(
                    (Type) deserialize(serialized.fields.get("type")),
                    (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("arguments"))
            );
            case "PlacementNew" -> new PlacementNewExpression(
                    (Type) deserialize(serialized.fields.get("type")),
                    (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("arguments"))
            );
            default -> throw new MeaningTreeException("Unsupported new expression for deserialize");
        };
    }

    private Node deserializeTypeCast(SerializedNode serialized) {
        return new CastTypeExpression((Type) deserialize(serialized.fields.get("type")),
                (Expression) deserialize(serialized.fields.get("expression")));
    }

    private Node deserializeType(SerializedNode serialized) {
        return switch (serialized.nodeName) {
            case "BooleanType" -> new BooleanType();
            case "StringType" -> new StringType((int) serialized.values.get("charSize"));
            case "CharacterType" -> new CharacterType((int) serialized.values.get("size"));
            case "FloatType" -> new FloatType((int) serialized.values.get("size"));
            case "IntType" -> new IntType((int) serialized.values.get("size"));
            case "PointerType" -> new PointerType((Type) deserialize(serialized.fields.get("type")));
            case "ReferenceType" -> new ReferenceType((Type) deserialize(serialized.fields.get("type")));
            case "ArrayType" -> {
                Shape shape = (Shape) deserialize(serialized.fields.get("shape"));
                yield new ArrayType((Type) deserialize(serialized.fields.get("type")), shape.getDimensionCount(), shape.getDimensions());
            }
            case "ListType" -> new ListType((Type) deserialize(serialized.fields.get("type")));
            case "SetType" -> new SetType((Type) deserialize(serialized.fields.get("type")));
            case "UnmodifiableListType" -> new UnmodifiableListType((Type) deserialize(serialized.fields.get("type")));
            case "DictionaryType" -> new DictionaryType((Type) deserialize(serialized.fields.get("keyType")),
                    (Type) deserialize(serialized.fields.get("valueType")));
            case "Class" -> new org.vstu.meaningtree.nodes.types.user.Class((Identifier) deserialize(serialized.fields.get("name")));
            case "Structure" -> new Structure((Identifier) deserialize(serialized.fields.get("name")));
            case "Enum" -> new org.vstu.meaningtree.nodes.types.user.Enum((Identifier) deserialize(serialized.fields.get("name")));
            case "Interface" -> new org.vstu.meaningtree.nodes.types.user.Interface((Identifier) deserialize(serialized.fields.get("name")));
            case "NoReturn" -> new NoReturn();
            case "UnknownType" -> new UnknownType();
            case "GenericClass" -> new GenericClass(
                    (Identifier) deserialize(serialized.fields.get("name")),
                    deserializeList((SerializedListNode) serialized.fields.get("templateParameters")).toArray(new Type[0])
            );
            default -> throw new MeaningTreeException("Unknown type in serializer");
        };
    }

    private Node deserializeShape(SerializedNode serialized) {
        return new Shape((int) serialized.values.get("dimensionCount"),
                (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("dimensions"))
        );
    }

    private Node deserializeEntryPoint(SerializedNode serialized) {
        return new ProgramEntryPoint((List<Node>) deserializeList(
                (SerializedListNode) serialized.fields.get("body"))
        );
    }

    private Node deserializeMemberAccess(SerializedNode serialized) {
        return new MemberAccess(
                (Expression) deserialize(serialized.fields.get("owner")),
                (SimpleIdentifier) deserialize(serialized.fields.get("member"))
        );
    }

    private Node deserializePointerMemberAccess(SerializedNode serialized) {
        return new PointerMemberAccess(
                (Expression) deserialize(serialized.fields.get("owner")),
                (SimpleIdentifier) deserialize(serialized.fields.get("member"))
        );
    }

    private Node deserializeExprStmt(SerializedNode serialized) {
        return new ExpressionStatement((Expression) deserialize(serialized.fields.get("expr")));
    }

    private Node deserializeExprSequence(SerializedNode serialized) {
        if (serialized.nodeName.equals("CommaExpression")) {
            return new CommaExpression((List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("exprs")));
        }
        return new ExpressionSequence((List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("exprs")));
    }

    private Node deserializeAssignmentExpr(SerializedNode serialized) {
        return new AssignmentExpression(
                (Expression) deserialize(serialized.fields.get("left")),
                (Expression) deserialize(serialized.fields.get("right")),
                AugmentedAssignmentOperator.valueOf((String) serialized.values.get("augmentedOp"))
        );
    }

    private Node deserializeAssignmentStmt(SerializedNode serialized) {
        return new AssignmentStatement(
                (Expression) deserialize(serialized.fields.get("left")),
                (Expression) deserialize(serialized.fields.get("right")),
                AugmentedAssignmentOperator.valueOf((String) serialized.values.get("augmentedOp"))
                );
    }

    private Node deserializeCompound(SerializedNode serialized) {
        return new CompoundComparison((List<BinaryComparison>) deserializeList((SerializedListNode) serialized.fields.get("members")));
    }

    private Node deserializeIndex(SerializedNode serialized) {
        return new IndexExpression((Expression) deserialize(serialized.fields.get("expr")),
                (Expression) deserialize(serialized.fields.get("index")),
                serialized.values.containsKey("preferPointers") && (boolean) serialized.values.get("preferPointers")
                );
    }

    private Node deserializeLiteral(SerializedNode serialized) {
        return switch (serialized.nodeName) {
            case "Integer" -> new IntegerLiteral((String) serialized.values.get("text"));
            case "Float" -> new FloatLiteral((String) serialized.values.get("text"));
            case "String" -> StringLiteral.fromEscaped((String) serialized.values.get("text"), StringLiteral.Type.NONE);
            case "Boolean" -> new BoolLiteral((boolean) serialized.values.get("text"));
            case "Char" -> new CharacterLiteral(serialized.values.get("value") instanceof String ? Integer.parseInt(serialized.values.get("value").toString()) : (int) serialized.values.get("value"));
            case "List" -> {
                PlainCollectionLiteral lit = new ListLiteral((List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("elements")));
                if (serialized.fields.containsKey("type")) lit.setTypeHint((Type) deserialize(serialized.fields.get("type")));
                yield lit;
            }
            case "Array" -> {
                PlainCollectionLiteral lit = new ArrayLiteral((List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("elements")));
                if (serialized.fields.containsKey("type")) lit.setTypeHint((Type) deserialize(serialized.fields.get("type")));
                yield lit;
            }
            case "UnmodifiableList" -> {
                PlainCollectionLiteral lit = new UnmodifiableListLiteral((List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("elements")));
                if (serialized.fields.containsKey("type")) lit.setTypeHint((Type) deserialize(serialized.fields.get("type")));
                yield lit;
            }
            case "InterpolatedString" -> new InterpolatedStringLiteral(
                    StringLiteral.Type.valueOf((String) serialized.values.get("type")),
                    (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("components"))
            );
            case "Null" -> new NullLiteral();
            default -> throw new MeaningTreeException("Unsupported literal in universal deserializer");
        };
    }

    private Node deserializeMethodCall(SerializedNode serialized) {
        return new MethodCall(
                (Expression) deserialize(serialized.fields.get("object")),
                (SimpleIdentifier) deserialize(serialized.fields.get("name")),
                (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args"))
                );
    }

    private Node deserializeFunctionCall(SerializedNode serialized) {
        if (serialized.values.containsKey("spec")) {
            String className = (String) serialized.values.get("spec");
            switch (className) {
                case "PrintValues" -> {
                    return new PrintValues(
                            (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args")),
                            serialized.fields.containsKey("separator") ? (StringLiteral) deserialize(serialized.fields.get("separator")) : null,
                            serialized.fields.containsKey("end") ? (StringLiteral) deserialize(serialized.fields.get("end")) : null
                    );
                }
                case "FormatPrint" -> {
                    List<Expression> exprs = (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args"));
                    return new FormatPrint((Expression) deserialize(serialized.fields.get("formatString")),
                            exprs);
                }
                case "FormatInput" -> {
                    List<Expression> exprs = (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args"));
                    return new FormatInput((Expression) deserialize(serialized.fields.get("formatString")),
                            exprs);
                }
                case "InputCommand" -> {
                    return new InputCommand((List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args")));
                }
                case "MemoryAllocationCall" -> {
                    List<Expression> args = (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args"));
                    return new MemoryAllocationCall((Type) args.getFirst(), args.get(1), (boolean) serialized.values.get("clearAlloc"));
                }
                case "MemoryFreeCall" -> {
                    List<Expression> args = (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args"));
                    return new MemoryFreeCall(args.getFirst());

                }
            }
        }
        return new FunctionCall(
                (Expression) deserialize(serialized.fields.get("name")),
                (List<Expression>) deserializeList((SerializedListNode) serialized.fields.get("args"))
        );
    }

    private Node deserializeIdentifier(SerializedNode serialized) {
        return switch(serialized.nodeName) {
            case "QualifiedIdentifier" -> new QualifiedIdentifier(
                    (Identifier) deserialize(serialized.fields.get("scope")),
                    (SimpleIdentifier) deserialize(serialized.fields.get("member"))
                    );
            case "SimpleIdentifier" -> new SimpleIdentifier((String) serialized.values.get("text"));
            case "ScopedIdentifier" -> new ScopedIdentifier((List<SimpleIdentifier>) deserializeList((SerializedListNode) serialized.fields.get("members")));
            default -> throw new MeaningTreeException("Unknown identifier in universal deserializer");
        };
    }

    private Node deserializeTernary(SerializedNode serialized) {
        return new TernaryOperator(
                (Expression) deserialize(serialized.fields.get("condition")),
                (Expression) deserialize(serialized.fields.get("then")),
                (Expression) deserialize(serialized.fields.get("else"))
        );
    }

    private Node deserializeParen(SerializedNode serialized) {
        return new ParenthesizedExpression((Expression) deserialize(serialized.fields.get("value")));
    }

    private Node deserializeOther(SerializedNode serialized) {
        Class clazz = null;
        try {
            clazz = Class.forName(
                    serialized.nodeName.startsWith("org.vstu.meaningtree.nodes.") ? serialized.nodeName :
                    "org.vstu.meaningtree.nodes.".concat(serialized.nodeName)
            );
        } catch (ClassNotFoundException e) {
            throw new MeaningTreeException("Unsupported serialized node in universal deserializer");
        }
        if (BinaryExpression.class.isAssignableFrom(clazz)) {
            try {
                if (clazz.equals(ReferenceEqOp.class) || clazz.equals(ContainsOp.class)) {
                    return (Node) clazz.getDeclaredConstructor(Expression.class, Expression.class, boolean.class).newInstance(
                            deserialize(serialized.fields.get("left")),
                            deserialize(serialized.fields.get("right")),
                            serialized.values.get("negative")
                    );
                } else if (clazz.equals(InstanceOfOp.class)) {
                    return (Node) clazz.getDeclaredConstructor(Expression.class, Type.class).newInstance(
                            deserialize(serialized.fields.get("left")),
                            deserialize(serialized.fields.get("right"))
                    );
                }
                return (Node) clazz.getDeclaredConstructor(Expression.class, Expression.class).newInstance(
                        deserialize(serialized.fields.get("left")),
                        deserialize(serialized.fields.get("right"))
                        );
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

        if (UnaryExpression.class.isAssignableFrom(clazz)) {
            try {
                return (Node) clazz.getDeclaredConstructor(Expression.class).newInstance(
                        deserialize(serialized.fields.get("arg"))
                );
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                     NoSuchMethodException e) {
            }
        }

        throw new MeaningTreeException("Unsupported serialized node " + serialized.nodeName + " in universal deserializer");
    }

    private List<? extends Node> deserializeList(SerializedListNode serializedListNode) {
        return serializedListNode.nodes.stream().map(this::deserialize).toList();
    }
}
