package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.configs.params.TranslationUnitMode;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.definitions.ObjectConstructorDefinition;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.*;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SelfReference;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.NewExpression;
import org.vstu.meaningtree.nodes.expressions.newexpr.ObjectNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.interfaces.HasInitialization;
import org.vstu.meaningtree.nodes.io.FormatInput;
import org.vstu.meaningtree.nodes.io.FormatPrint;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.EmptyStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.ReturnStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.*;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.GenericUserType;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.utils.Label;
import org.vstu.meaningtree.utils.scopes.SimpleTypeInferrer;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.*;
import java.util.stream.Collectors;

import static org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator.POW;

public class JavaViewer extends LanguageViewer {

    private final String _indentation;
    private int _indentLevel;
    private final boolean _openBracketOnSameLine;
    private final boolean _bracketsAroundCaseBranches;
    private final boolean _autoVariableDeclaration;

    private Type _methodReturnType = null;

    public JavaViewer(LanguageTranslator translator, int indentSpaceCount,
                      boolean openBracketOnSameLine,
                      boolean bracketsAroundCaseBranches,
                      boolean autoVariableDeclaration
    ) {
        super(translator);
        _indentation = " ".repeat(indentSpaceCount);
        _indentLevel = 0;
        _openBracketOnSameLine = openBracketOnSameLine;
        _bracketsAroundCaseBranches = bracketsAroundCaseBranches;
        _autoVariableDeclaration = autoVariableDeclaration;
    }

    public JavaViewer(LanguageTranslator translator) {
        this(translator, 4, true, true, false);
    }

    @Override
    public String formString(Node node) {
        if (node instanceof UnaryExpression expr) {
            node = parenFiller.process(expr);
        }

        Objects.requireNonNull(node);

        // Для dummy узлов ничего не выводим
        if (node.hasLabel(Label.DUMMY)) {
            return "";
        }

        return switch (node) {
            case ListLiteral listLiteral -> toStringListLiteral(listLiteral);
            case SetLiteral setLiteral -> toStringSetLiteral(setLiteral);
            case DictionaryLiteral dictionaryLiteral -> toStringDictionaryLiteral(dictionaryLiteral);
            case PlainCollectionLiteral unmodifiableListLiteral -> toStringPlainCollectionLiteral(unmodifiableListLiteral);
            case InterpolatedStringLiteral interpolatedStringLiteral -> toStringInterpolatedStringLiteral(interpolatedStringLiteral);
            case FloatLiteral l -> toStringFloatLiteral(l);
            case IntegerLiteral l -> toStringIntegerLiteral(l);
            case QualifiedIdentifier id -> toStringQualifiedIdentifier(id);
            case StringLiteral l -> toStringStringLiteral(l);
            case UserType userType -> toStringUserType(userType);
            case ReferenceType ref -> toString(ref.getTargetType());
            case PointerType ptr -> toString(ptr.getTargetType());
            case MemoryAllocationCall memoryAllocationCall -> toString(memoryAllocationCall.toNew());
            case MemoryFreeCall freeCall -> toString(freeCall.toDelete());
            case Type type -> toStringType(type);
            case SelfReference selfReference -> toStringSelfReference(selfReference);
            case UnaryMinusOp unaryMinusOp -> toStringUnaryMinusOp(unaryMinusOp);
            case UnaryPlusOp unaryPlusOp -> toStringUnaryPlusOp(unaryPlusOp);
            case AddOp op -> toStringAddOp(op);
            case SubOp op -> toStringSubOp(op);
            case MulOp op -> toStringMulOp(op);
            case DivOp op -> toStringDivOp(op);
            case ModOp op -> toStringModOp(op);
            case MatMulOp op -> toStringMatMulOp(op);
            case FloorDivOp op -> toStringFloorDivOp(op);
            case EqOp op -> toStringEqOp(op);
            case GeOp op -> toStringGeOp(op);
            case GtOp op -> toStringGtOp(op);
            case LeOp op -> toStringLeOp(op);
            case LtOp op -> toStringLtOp(op);
            case InstanceOfOp op -> toStringInstanceOfOp(op);
            case NotEqOp op -> toStringNotEqOp(op);
            case ShortCircuitAndOp op -> toStringShortCircuitAndOp(op);
            case ShortCircuitOrOp op -> toStringShortCircuitOrOp(op);
            case NotOp op -> toStringNotOp(op);
            case ParenthesizedExpression expr -> toStringParenthesizedExpression(expr);
            case AssignmentExpression expr -> toStringAssignmentExpression(expr);
            case AssignmentStatement stmt -> toStringAssignmentStatement(stmt);
            case FieldDeclaration decl -> toStringFieldDeclaration(decl);
            case VariableDeclaration stmt -> toStringVariableDeclaration(stmt);
            case CompoundStatement stmt -> toStringCompoundStatement(stmt);
            case ExpressionStatement stmt -> toStringExpressionStatement(stmt);
            case MethodDeclaration stmt -> toStringMethodDeclaration(stmt);
            case SimpleIdentifier expr -> toStringSimpleIdentifier(expr);
            case IfStatement stmt -> toStringIfStatement(stmt);
            case GeneralForLoop stmt -> toStringGeneralForLoop(stmt);
            case CompoundComparison cmp -> toStringCompoundComparison(cmp);
            case RangeForLoop rangeLoop -> toStringRangeForLoop(rangeLoop);
            case ProgramEntryPoint entryPoint -> toStringProgramEntryPoint(entryPoint);
            case MethodCall methodCall -> toStringMethodCall(methodCall);
            case FormatPrint fmt -> toStringFormatPrint(fmt);
            case PrintValues printValues -> toStringPrintValues(printValues);
            case FormatInput fmt -> toStringFormatInput(fmt);
            case InputCommand inputCommand -> toStringInputCommand(inputCommand);
            case FunctionCall funcCall -> toStringFunctionCall(funcCall);
            case WhileLoop whileLoop -> toStringWhileLoop(whileLoop);
            case ScopedIdentifier scopedIdent -> toStringScopedIdentifier(scopedIdent);
            case PostfixIncrementOp inc -> toStringPostfixIncrementOp(inc);
            case PostfixDecrementOp dec -> toStringPostfixDecrementOp(dec);
            case PrefixIncrementOp inc -> toStringPrefixIncrementOp(inc);
            case PrefixDecrementOp dec -> toStringPrefixDecrementOp(dec);
            case PowOp op -> toStringPowOp(op);
            case PackageDeclaration decl -> toStringPackageDeclaration(decl);
            case ClassDeclaration decl -> toStringClassDeclaration(decl);
            case ClassDefinition def -> toStringClassDefinition(def);
            case Comment comment -> toStringComment(comment);
            case BreakStatement stmt -> toStringBreakStatement(stmt);
            case ContinueStatement stmt -> toStringContinueStatement(stmt);
            case ObjectConstructorDefinition objectConstructor -> toStringObjectConstructorDefinition(objectConstructor);
            case MethodDefinition methodDefinition -> toStringMethodDefinition(methodDefinition);
            case SwitchStatement switchStatement -> toStringSwitchStatement(switchStatement);
            case NullLiteral nullLiteral -> toStringNullLiteral(nullLiteral);
            case StaticImportAll staticImportAll -> toStringStaticImportAll(staticImportAll);
            case StaticImportMembersFromModule staticImportMembers -> toStringStaticImportMembersFromModule(staticImportMembers);
            case ImportAllFromModule importAll -> toStringImportAllFromModule(importAll);
            case ImportMembersFromModule importMembers -> toStringImportMembersFromModule(importMembers);
            case ObjectNewExpression objectNewExpression -> toStringObjectNewExpression(objectNewExpression);
            case BoolLiteral boolLiteral -> toStringBoolLiteral(boolLiteral);
            case MemberAccess memberAccess -> toStringMemberAccess(memberAccess);
            case ArrayNewExpression arrayNewExpression -> toStringArrayNewExpression(arrayNewExpression);
            case ArrayInitializer arrayInitializer -> toStringArrayInitializer(arrayInitializer);
            case ReturnStatement returnStatement -> toStringReturnStatement(returnStatement);
            case CastTypeExpression castTypeExpression -> toStringCastTypeExpression(castTypeExpression);
            case IndexExpression indexExpression -> toStringIndexExpression(indexExpression);
            case TernaryOperator ternaryOperator -> toStringTernaryOperator(ternaryOperator);
            case BitwiseAndOp bitwiseAndOp -> toStringBitwiseAndOp(bitwiseAndOp);
            case BitwiseOrOp bitwiseOrOp -> toStringBitwiseOrOp(bitwiseOrOp);
            case XorOp xorOp -> toStringXorOp(xorOp);
            case CommaExpression ignored -> throw new UnsupportedViewingException("Comma is unsupported in this language");
            case SizeofExpression ignored -> throw new UnsupportedViewingException("Sizeof is disabled in this language");
            case InversionOp inversionOp -> toStringInversionOp(inversionOp);
            case LeftShiftOp leftShiftOp -> toStringLeftShiftOp(leftShiftOp);
            case RightShiftOp rightShiftOp -> toStringRightShiftOp(rightShiftOp);
            case MultipleAssignmentStatement multipleAssignmentStatement -> toStringMultipleAssignmentStatement(multipleAssignmentStatement);
            case InfiniteLoop infiniteLoop -> toStringInfiniteLoop(infiniteLoop);
            case ExpressionSequence expressionSequence -> toStringExpressionSequence(expressionSequence);
            case CharacterLiteral characterLiteral -> toStringCharacterLiteral(characterLiteral);
            case DoWhileLoop doWhileLoop -> toStringDoWhileLoop(doWhileLoop);
            case ForEachLoop forEachLoop -> toStringForEachLoop(forEachLoop);
            case PointerPackOp ptr -> toStringPointerPackOp(ptr);
            case DefinitionArgument defArg ->toString(defArg.getInitialExpression());
            case PointerUnpackOp ptr -> toStringPointerUnpackOp(ptr);
            case Annotation annotation -> toStringAnnotation(annotation);
            case ContainsOp op -> toStringContainsOp(op);
            case ReferenceEqOp op -> toStringReferenceEqOp(op);
            case FunctionDefinition functionDefinition -> toStringFunctionDefinition(functionDefinition);
            case EmptyStatement emptyStatement -> toStringEmptyStatement(emptyStatement);
            case ConditionBranch branch -> toStringConditionBranch(branch);
            case Shape shape -> toStringShape(shape);
            case FunctionDeclaration decl -> toStringFunctionDeclaration(decl);
            case DeclarationArgument declarationArgument -> toStringDeclarationArgument(declarationArgument);
            default -> throw new UnsupportedViewingException(String.format("Can't stringify node %s", node.getClass()));
        };
    }

    private String toStringEmptyStatement(EmptyStatement emptyStatement) {
        return "";
    }

    private String toStringFunctionDefinition(FunctionDefinition functionDefinition) {
        StringBuilder builder = new StringBuilder();

        // Преобразование типа нужно, чтобы избежать вызова toString(Node node)
        String methodDeclaration = toString((FunctionDeclaration) functionDefinition.getDeclaration());
        builder.append(methodDeclaration);

        String body = toString(functionDefinition.getBody());
        if (_openBracketOnSameLine)
        { builder.append(" ").append(body).append("\n"); }
        else
        { builder.append("\n").append(indent(body)).append("\n"); }

        return builder.toString();
    }

    private String toStringFunctionDeclaration(FunctionDeclaration functionDeclaration) {
        StringBuilder builder = new StringBuilder();
        builder.append(toStringAnnotations(functionDeclaration.getAnnotations()));

        // Считаем каждую функцию доступной извне
        builder.append("public static ");

        String returnType = toString(functionDeclaration.getReturnType());
        builder.append(returnType).append(" ");

        String name = toString(functionDeclaration.getName());
        builder.append(name);

        String parameters = toStringParameters(functionDeclaration.getArguments());
        builder.append(parameters);

        return builder.toString();
    }

    private String toStringInputCommand(InputCommand inputCommand) {
        var builder = new StringBuilder();

        int i = 0;
        for (Expression stringPart : inputCommand.getArguments()) {
            if (i > 0) {
                builder
                        .append(indent(toString(inputCommand.getArguments().getFirst())))
                        .append(" = ")
                        .append("new Scanner(System.in).");
            }
            else {
                builder
                        .append(toString(inputCommand.getArguments().getFirst()))
                        .append(" = ")
                        .append("new Scanner(System.in).");
            }

            Type exprType = ctx.inferType(stringPart);
            switch (exprType) {
                case StringType stringType -> {
                    builder.append("next()");
                }
                case IntType integerType -> {
                    builder.append("nextInt()");
                }
                case FloatType floatType -> {
                    builder.append("nextDouble()");
                }
                default -> {
                    throw new IllegalStateException("Unsupported type in format input in Java: " + exprType);
                }
            }

            builder.append("\n");
            i += 1;
        }

        builder.deleteCharAt(builder.length() - 1);
        return builder.toString();
    }

    private String toStringFormatInput(FormatInput formatInput) {
        var builder = new StringBuilder();

        builder.append("new Scanner(System.in).");
        if (formatInput.getArguments().size() > 1) {
            throw new IllegalStateException("Multiple input values are not supported in Java");
        }

        for (Expression stringPart : formatInput.getArguments()) {
            Type exprType = ctx.inferType(stringPart);
            switch (exprType) {
                case StringType stringType -> {
                    builder.append("next()");
                }
                case IntType integerType -> {
                    builder.append("nextInt()");
                }
                case FloatType floatType -> {
                    builder.append("nextDouble()");
                }
                default -> {
                    throw new IllegalStateException("Unsupported type in format input in Java: " + exprType);
                }
            }
        }

        return builder.toString();
    }

    private String toStringFormatPrint(FormatPrint formatPrint) {
        return String.format(
                "System.out.printf(%s, %s)",
                formatPrint.getFormatString(),
                toStringExprList(formatPrint.getArguments())
        );
    }

    private String toStringExprList(List<Expression> arguments) {
        return arguments.stream().map(this::toString).collect(Collectors.joining(", "));
    }

    public String toStringPointerPackOp(PointerPackOp ptr) {
        return toString(ptr.getArgument());
    }

    public String toStringAnnotation(Annotation annotation) {
        StringBuilder builder = new StringBuilder();
        builder.append("@");
        builder.append(toString(annotation.getName()));
        if (annotation.getArguments().length > 0) {
            builder.append("(");
            for (Expression arg : annotation.getArguments()) {
                builder.append(toString(arg));
                builder.append(", ");
            }
            if (builder.substring(builder.length() - 2, builder.length()) == ", ") {
                builder.replace(builder.length() - 2, builder.length(), "");
            }
            builder.append(")");
        }
        return builder.toString();
    }

    public String toStringPointerUnpackOp(PointerUnpackOp ptr) {
        if (ptr.getArgument() instanceof SubOp) {
            throw new UnsupportedViewingException("Subtraction of pointers cannot be converted to indexing");
        }
        return toString(ptr.getArgument());
    }

    public String toStringListLiteral(ListLiteral list) {
        var builder = new StringBuilder();
        String typeHint = "";
        builder.append(String.format("new java.util.ArrayList<%s>(java.util.List.of(", typeHint));
        for (Expression expression : list.getList()) {
            builder.append(String.format("%s, ", toString(expression)));
        }
        if (builder.toString().endsWith(", ")) {
            builder.delete(builder.length() - 2, builder.length());
        }
        builder.append("))");
        return builder.toString();
    }

    public String toStringSetLiteral(SetLiteral list) {
        var builder = new StringBuilder();
        String typeHint = list.getTypeHint() == null ? "" : toString(list.getTypeHint());
        builder.append(String.format("new java.util.HashSet<%s>() {{", typeHint));
        for (Expression expression : list.getList()) {
            builder.append(String.format("add(%s);", toString(expression)));
        }
        builder.append("}}");
        return builder.toString();
    }

    public String toStringDictionaryLiteral(DictionaryLiteral list) {
        var builder = new StringBuilder();
        String keyTypeHint = list.getKeyTypeHint() == null ? "" : toString(list.getKeyTypeHint());
        String valueTypeHint = list.getValueTypeHint() == null || keyTypeHint.isEmpty() ? "" : ", ".concat(toString(list.getValueTypeHint()));
        builder.append(String.format("new java.util.TreeMap<%s%s>() {{", keyTypeHint, valueTypeHint));
        for (Map.Entry<Expression, Expression> entry : list.getDictionary().entrySet()) {
            builder.append(String.format("put(%s, %s);", toString(entry.getKey()), toString(entry.getValue())));
        }
        builder.append("}}");
        return builder.toString();
    }

    public String toStringPlainCollectionLiteral(PlainCollectionLiteral unmodifiableListLiteral) {
        var builder = new StringBuilder();
        String typeHint = unmodifiableListLiteral.getTypeHint() == null ? "Object" : toString(unmodifiableListLiteral.getTypeHint());
        builder.append(String.format("new %s[] {", typeHint));

        for (Expression expression : unmodifiableListLiteral.getList()) {
            builder.append(toString(expression)).append(", ");
        }

        if (builder.length() > 2) {
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
        }

        builder.append("}");
        return builder.toString();
    }

    public String toStringInterpolatedStringLiteral(InterpolatedStringLiteral interpolatedStringLiteral) {
        var builder = new StringBuilder();
        var argumentsBuilder = new StringBuilder();

        builder.append("String.format(\"");
        for (Expression stringPart : interpolatedStringLiteral.components()) {
            Type exprType = ctx.inferType(stringPart);
            switch (exprType) {
                case StringType stringType -> {
                    var string = toString(stringPart);
                    builder.append(string, 1, string.length() - 1);
                }
                case IntType integerType -> {
                    builder.append("%d");
                    argumentsBuilder.append(toString(stringPart)).append(", ");
                }
                case FloatType floatType -> {
                    builder.append("%f");
                    argumentsBuilder.append(toString(stringPart)).append(", ");
                }
                default -> {
                    builder.append("%s");
                    argumentsBuilder.append(toString(stringPart)).append(", ");
                }
            }
        }
        builder.append("\"");

        if (argumentsBuilder.length() > 2) {
            argumentsBuilder.deleteCharAt(argumentsBuilder.length() - 1);
            argumentsBuilder.deleteCharAt(argumentsBuilder.length() - 1);

            builder
                    .append(", ")
                    .append(argumentsBuilder.toString());
        }

        builder.append(")");
        return builder.toString();
    }

    public String toStringPrintValues(PrintValues printValues) {
        StringBuilder builder = new StringBuilder();

        builder.append("System.out.");
        builder.append(printValues.addsNewLine() ? "println" : "print");
        builder.append("(");

        if (printValues.valuesCount() > 1) {
            builder.append("String.join(");

            if (printValues.separator != null) {
                builder
                        .append(toString(printValues.separator))
                        .append(", ");
            }

            for (Expression value : printValues.getArguments()) {
                builder
                        .append(toString(value))
                        .append(", ");
            }
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);

            if (!printValues.addsNewLine() && printValues.end != null && !printValues.end.getUnescapedValue().isEmpty()) {
                builder.append(", ");
                builder.append(toString(printValues.end));
            }

            builder.append(")");
        }
        else if (printValues.valuesCount() == 1) {
            builder.append(
                    toString(printValues.getArguments().getFirst())
            );
        }

        builder.append(")");

        return builder.toString();
    }

    public String toStringUnaryPlusOp(UnaryPlusOp unaryPlusOp) {
        return "+" + toString(unaryPlusOp.getArgument());
    }

    public String toStringUnaryMinusOp(UnaryMinusOp unaryMinusOp) {
        return "-" + toString(unaryMinusOp.getArgument());
    }

    private String toStringDoWhileLoop(DoWhileLoop doWhileLoop) {
        StringBuilder builder = new StringBuilder();

        builder.append("do");

        if (_openBracketOnSameLine) {
            builder.append(" {\n");
        }
        else {
            builder.append("\n").append(indent("{\n"));
        }

        List<Node> nodes = new ArrayList<>();
        Statement body = doWhileLoop.getBody();
        if (body instanceof CompoundStatement) {
            nodes.addAll(Arrays.asList(((CompoundStatement) body).getNodes()));
        }
        else {
            nodes.add(body);
        }

        increaseIndentLevel();
        for (Node node : nodes) {
            builder
                    .append(indent(toString(node)))
                    .append("\n");
        }
        decreaseIndentLevel();

        if (_openBracketOnSameLine) {
            builder
                    .append(indent("} "))
                    .append(
                            "while (%s);".formatted(
                                    toString(doWhileLoop.getCondition())
                            )
                    );
        }
        else {
            builder
                    .append(indent("}\n"))
                    .append(
                            indent("while (%s);".formatted(
                                        toString(doWhileLoop.getCondition())
                                    )
                            )
                    );;
        }

        return builder.toString();
    }

    private String toStringForEachLoop(ForEachLoop forEachLoop) {
        var type = toString(forEachLoop.getItem().getType());
        var iterVarId = toString(forEachLoop.getItem().getDeclarators()[0].getIdentifier());
        var iterable = toString(forEachLoop.getExpression());
        var body = toString(forEachLoop.getBody());

        StringBuilder builder = new StringBuilder();

        return builder
                .append("for (")
                .append(type)
                .append(" ")
                .append(iterVarId)
                .append(" : ")
                .append(iterable)
                .append(")")
                .append(_openBracketOnSameLine ? " " : "\n")
                .append(indent(body))
                .toString();
    }

    private String toStringCharacterLiteral(CharacterLiteral characterLiteral) {
        String symbol = characterLiteral.escapedString();
        return "'" + symbol + "'";
    }

    private String toStringExpressionSequence(ExpressionSequence expressionSequence) {
         StringBuilder builder = new StringBuilder();

         for (Expression expression : expressionSequence.getExpressions()) {
             builder.append(toString(expression)).append(", ");
         }

         // Удаляем лишние пробел и запятую
         if (builder.length() > 2) {
             builder.deleteCharAt(builder.length() - 1);
             builder.deleteCharAt(builder.length() - 1);
         }

         return builder.toString();
    }

    private String toStringInfiniteLoop(InfiniteLoop infiniteLoop) {
        StringBuilder builder = new StringBuilder();

        boolean trailingWhile = false;
        var loopHeader = switch (infiniteLoop.getLoopType()) {
            case FOR -> "for (;;)";
            case WHILE -> "while (true)";
            case DO_WHILE -> {
                trailingWhile = true;
                yield "do";
            }
        };

        builder.append(indent(loopHeader));
        Statement body = infiniteLoop.getBody();

        if (body instanceof CompoundStatement compoundStatement) {
            if (_openBracketOnSameLine) {
                builder
                        .append(" ")
                        .append(toString(compoundStatement));
            }
            else {
                builder.append("\n");
                builder.append(indent(toString(body)));
            }
        }
        else {
            builder.append("\n");
            increaseIndentLevel();
            builder.append(indent(toString(body)));
            decreaseIndentLevel();
        }

        if (trailingWhile) {
            builder.append("while (true);\n");
        }

        return builder.toString();
    }

    private String toStringSelfReference(SelfReference selfReference) {
        return "this";
    }

    private String toStringObjectConstructorDefinition(ObjectConstructorDefinition objectConstructor) {
        MethodDeclaration constructorDeclaration = objectConstructor.getDeclaration();

        StringBuilder builder = new StringBuilder();
        builder.append(toStringAnnotations(constructorDeclaration.getAnnotations()));

        String modifiers = toString(constructorDeclaration.getModifiers());
        if (!modifiers.isEmpty()) {
            builder.append(modifiers).append(" ");
        }

        String name = toString(objectConstructor.getName());
        builder.append(name);

        String parameters = toStringParameters(constructorDeclaration.getArguments());
        if (!parameters.isEmpty()) {
            builder.append(parameters);
        }

        String body = toString(objectConstructor.getBody());
        if (_openBracketOnSameLine)
            { builder.append(" ").append(body); }
        else
            { builder.append("\n").append(indent(body)); }

        return builder.toString();
    }

    private String toStringMultipleAssignmentStatement(MultipleAssignmentStatement multipleAssignmentStatement) {
        StringBuilder builder = new StringBuilder();

        for (AssignmentStatement stmt : multipleAssignmentStatement.getStatements()) {
            builder.append(toString(stmt)).append("\n");
        }

        // Удаляем последний перевод строки
        if (builder.length() > 1) {
            builder.deleteCharAt(builder.length() - 1);
        }

        return builder.toString();
    }

    private String toStringRightShiftOp(RightShiftOp rightShiftOp) {
        return toString(rightShiftOp, ">>");
    }

    private String toStringLeftShiftOp(LeftShiftOp leftShiftOp) {
        return toString(leftShiftOp, "<<");
    }

    private String toStringInversionOp(InversionOp inversionOp) {
        return "~" + toString(inversionOp.getArgument());
    }

    private String toStringXorOp(XorOp xorOp) {
        return toString(xorOp, "^");
    }

    private String toStringBitwiseOrOp(BitwiseOrOp bitwiseOrOp) {
        return toString(bitwiseOrOp, "|");
    }

    private String toStringBitwiseAndOp(BitwiseAndOp bitwiseAndOp) {
        return toString(bitwiseAndOp, "&");
    }

    private String toStringTernaryOperator(TernaryOperator ternaryOperator) {
        ternaryOperator = parenFiller.process(ternaryOperator);
        String condition = toString(ternaryOperator.getCondition());
        String consequence = toString(ternaryOperator.getThenExpr());
        String alternative = toString(ternaryOperator.getElseExpr());
        return "%s ? %s : %s".formatted(condition, consequence, alternative);
    }

    private String toStringIndexExpression(IndexExpression indexExpression) {
        indexExpression = parenFiller.process(indexExpression);
        Expression arrayName = indexExpression.getExpression();
        String name = toString(arrayName);
        String index = toString(indexExpression.getIndex());
        return "%s[%s]".formatted(name, index);
    }

    private String toStringCastTypeExpression(CastTypeExpression castTypeExpression) {
        castTypeExpression = parenFiller.process(castTypeExpression);
        String castType = toString(castTypeExpression.getCastType());
        String value = toString(castTypeExpression.getValue());
        return "(%s) %s".formatted(castType, value);
    }

    private String toStringReturnStatement(ReturnStatement returnStatement) {
        if (_methodReturnType instanceof NoReturn)
            return "return;";

        Expression expression = returnStatement.getExpression();
        return (expression != null) ? "return %s;".formatted(toString(expression)) : "return;";
    }

    private String toStringArrayInitializer(ArrayInitializer initializer) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");

        List<Expression> values = initializer.getValues();
        for (Expression value : values) {
            builder
                    .append(toString(value))
                    .append(", ");
        }

        if (builder.length() > 1) {
            // Удаляем лишние пробел и запятую
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
        }

        builder.append("}");
        return builder.toString();
    }

    private String toStringArrayNewExpression(ArrayNewExpression arrayNewExpression) {
        StringBuilder builder = new StringBuilder();
        builder.append("new ");

        String type = toString(arrayNewExpression.getType());
        builder.append(type);

        String dimensions = toString(arrayNewExpression.getShape());
        builder.append(dimensions);

        ArrayInitializer optionalInitializer = arrayNewExpression.getInitializer();
        if (optionalInitializer != null) {
            String initializer = toString(optionalInitializer);
            builder.append(" ").append(initializer);
        }

        return builder.toString();
    }

    private String toStringMemberAccess(MemberAccess memberAccess) {
        memberAccess = parenFiller.process(memberAccess);
        String object = toString(memberAccess.getExpression());
        String member = toString(memberAccess.getMember());
        return "%s.%s".formatted(object, member);
    }

    private String toStringBoolLiteral(BoolLiteral boolLiteral) {
        return boolLiteral.getValue() ? "true" : "false";
    }

    private String toStringObjectNewExpression(ObjectNewExpression objectNewExpression) {
        String typeName = toString(objectNewExpression.getType());

        String arguments = objectNewExpression
                .getConstructorArguments()
                .stream()
                .map(this::toString)
                .collect(Collectors.joining(", "));

        return "new %s(%s)".formatted(typeName, arguments);
    }

    private String toStringMethodCall(MethodCall methodCall) {
        methodCall = parenFiller.process(methodCall);
        String object = toString(methodCall.getObject());
        String methodName = toString(methodCall.getFunctionName());

        String arguments = methodCall
                .getArguments()
                .stream()
                .map(this::toString)
                .collect(Collectors.joining(", "));

        return "%s.%s(%s)".formatted(object, methodName, arguments);
    }

    private String toStringUserType(UserType userType) {
        if (userType instanceof GenericUserType generic) {
            String args = Arrays.stream(generic.getTypeParameters()).map(this::toString).collect(Collectors.joining(", "));
            return String.format("%s<%s>", toString(generic.getName()), args);
        }
        return toString(userType.getName());
    }

    private String toStringStaticImportAll(StaticImportAll staticImportAll) {
        String importTemplate = "import static %s.*;";
        return importTemplate.formatted(toString(staticImportAll.getModuleName()));
    }

    private String toStringStaticImportMembersFromModule(StaticImportMembersFromModule staticImportMembers) {
        StringBuilder builder = new StringBuilder();

        String importTemplate = "import static %s.%s;";
        for (Identifier member : staticImportMembers.getMembers()) {
            builder
                    .append(
                            importTemplate.formatted(
                                    toString(staticImportMembers.getModuleName()),
                                    toString(member)
                            )
                    )
                    .append("\n");
            ;
        }

        // Удаляем последний символ перевода строки
        builder.deleteCharAt(builder.length() - 1);

        return builder.toString();
    }

    private String toStringImportAllFromModule(ImportAllFromModule importAll) {
        String importTemplate = "import %s.*;";
        return importTemplate.formatted(toString(importAll.getModuleName()));
    }

    private String toStringImportMembersFromModule(ImportMembersFromModule importMembers) {
        StringBuilder builder = new StringBuilder();

        String importTemplate = "import %s.%s;";
        for (Identifier member : importMembers.getMembers()) {
            builder
                    .append(
                        importTemplate.formatted(
                            toString(importMembers.getModuleName()),
                            toString(member)
                        )
                    )
                    .append("\n");
            ;
        }

        // Удаляем последний символ перевода строки
        if (!importMembers.getMembers().isEmpty()) {
            builder.deleteCharAt(builder.length() - 1);
        }

        return builder.toString();
    }

    private String toStringNullLiteral(NullLiteral nullLiteral) {
        return "null";
    }

    private String toStringCaseBlock(CaseBlock caseBlock) {
        StringBuilder builder = new StringBuilder();

        Statement caseBlockBody;
        if (caseBlock instanceof MatchValueCaseBlock mvcb) {
            builder.append("case ");
            builder.append(toString(mvcb.getMatchValue()));
            builder.append(":");
            caseBlockBody = mvcb.getBody();
        }
        else if (caseBlock instanceof DefaultCaseBlock dcb) {
            builder.append("default:");
            caseBlockBody = dcb.getBody();
        }
        else {
            throw new IllegalStateException("Unsupported case block type: " + caseBlock.getClass());
        }

        List<Node> nodesList;
        if (caseBlockBody instanceof CompoundStatement compoundStatement) {
            nodesList = Arrays.asList(compoundStatement.getNodes());
        }
        else {
            nodesList = List.of(caseBlockBody);
        }

        // Внутри case веток нельзя объявлять переменные, нужно обернуть их скобками,
        // поэтому проверяем наличие деклараций переменных
        boolean hasDeclarationInside = false;
        for (Node node : nodesList) {
            if (node instanceof VariableDeclaration) {
                hasDeclarationInside = true;
                break;
            }
        }

        if (!nodesList.isEmpty()) {
            if (_bracketsAroundCaseBranches || hasDeclarationInside) {
                if (_openBracketOnSameLine) {
                    builder.append(" {\n");
                }
                else {
                    builder.append("\n").append(indent("{\n"));
                }
            }
            else {
                builder.append("\n");
            }

            increaseIndentLevel();

            for (Node node : nodesList) {
                builder
                        .append(indent(toString(node)))
                        .append("\n");
            }

            if (caseBlock instanceof BasicCaseBlock || caseBlock instanceof DefaultCaseBlock) {
                builder.append(indent("break;"));
            }
            else {
                builder.deleteCharAt(builder.length() - 1);
            }

            decreaseIndentLevel();

            if (_bracketsAroundCaseBranches || hasDeclarationInside) {
                builder
                        .append("\n")
                        .append(indent("}"));
            }
        }

        return builder.toString();
    }

    private String toStringSwitchStatement(SwitchStatement switchStatement) {
        StringBuilder builder = new StringBuilder();

        builder.append("switch (");
        builder.append(toString(switchStatement.getTargetExpression()));
        builder.append(") ");

        if (_openBracketOnSameLine) {
            builder.append("{\n");
        }
        else {
            builder.append("\n").append(indent("{\n"));
        }

        increaseIndentLevel();
        for (CaseBlock caseBlock : switchStatement.getCases()) {
            builder
                    .append(indent(toStringCaseBlock(caseBlock)))
                    .append("\n");
        }

        if (switchStatement.hasDefaultCase()) {
            builder
                    .append(indent(toStringCaseBlock(switchStatement.getDefaultCase())))
                    .append("\n");
        }

        decreaseIndentLevel();

        builder.append(indent("}"));
        return builder.toString();
    }

    private String toStringDeclarationArgument(DeclarationArgument parameter) {
        var builder = new StringBuilder();

        String type = toString(parameter.getElementType());
        builder.append(type);

        if (parameter.isListUnpacking()) {
            builder.append("...");
        }

        String name = toString(parameter.getName());
        builder.append(" ").append(name);

        return builder.toString();
    }

    // В отличие от всех остальных методов, данный называется так,
    // чтобы избежать конфликтов с другими методами:
    // toStringParameters(List<Modifier> modifiers)
    // и toStringParameters(List<DeclarationArgument> parameters)
    // с точки зрения Java один и тот же тип...
    private String toStringParameters(List<DeclarationArgument> parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("(");

        int i;
        for (i = 0; i < parameters.size(); i++) {
            DeclarationArgument parameter = parameters.get(i);
            builder.append("%s, ".formatted(toString(parameter)));
        }

        // Удаляем последний пробел и запятую, если был хотя бы один параметр
        if (i > 0) {
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
        }

        builder.append(")");
        return builder.toString();
    }

    private String toStringMethodDeclaration(MethodDeclaration methodDeclaration) {
        StringBuilder builder = new StringBuilder();
        builder.append(toStringAnnotations(methodDeclaration.getAnnotations()));

        String modifiersList = toString(methodDeclaration.getModifiers());
        if (!modifiersList.isEmpty()) {
            builder.append(modifiersList).append(" ");
        }

        String returnType = toString(methodDeclaration.getReturnType());
        builder.append(returnType).append(" ");

        String name = toString(methodDeclaration.getName());
        builder.append(name);

        String parameters = toStringParameters(methodDeclaration.getArguments());
        builder.append(parameters);

        return builder.toString();
    }

    private String toStringMethodDefinition(MethodDefinition methodDefinition) {
        StringBuilder builder = new StringBuilder();

        // Нужен для отслеживания необходимости в return
        _methodReturnType = methodDefinition.getDeclaration().getReturnType();

        // Преобразование типа нужно, чтобы избежать вызова toString(Node node)
        String methodDeclaration = toString(methodDefinition.getDeclaration());
        builder.append(methodDeclaration);

        String body = toString(methodDefinition.getBody());
        if (_openBracketOnSameLine)
            { builder.append(" ").append(body).append("\n"); }
        else
            { builder.append("\n").append(indent(body)).append("\n"); }

        _methodReturnType = null;

        return builder.toString();
    }

    private String toStringContinueStatement(ContinueStatement stmt) {
        return "continue;";
    }

    private String toStringBreakStatement(BreakStatement stmt) {
        return "break;";
    }

    private String toStringComment(Comment comment) {
        if (comment.isMultiline()) {
            return "/*" + comment.getUnescapedContent() + "*/";
        }

        return "//%s".formatted(comment.getUnescapedContent());
    }

    private String toStringFieldDeclaration(FieldDeclaration decl) {
        StringBuilder builder = new StringBuilder();

        String modifiers = toString(decl.getModifiers());
        builder.append(modifiers);
        // Добавляем пробел в конце, если есть хотя бы один модификатор
        if (!builder.isEmpty()) {
            builder.append(" ");
        }

        VariableDeclaration variableDeclaration = new VariableDeclaration(decl.getType(), decl.getDeclarators());
        builder.append(toString(variableDeclaration));

        return builder.toString();
    }

    private String toString(List<DeclarationModifier> modifiers) {
        StringBuilder builder = new StringBuilder();

        for (DeclarationModifier modifier : modifiers) {
            builder.append(
                    switch (modifier) {
                        case PUBLIC -> "public";
                        case PRIVATE -> "private";
                        case PROTECTED -> "protected";
                        case ABSTRACT -> "abstract";
                        case CONST -> "final";
                        case STATIC -> "static";
                        default -> throw new IllegalArgumentException();
                    }
            ).append(" ");
        }

        // Удаляем в конце ненужный пробел, если было более одного модификатора
        if (!builder.isEmpty()) {
            builder.deleteCharAt(builder.length() - 1);
        }

        return builder.toString();
    }

    private String toStringClassDeclaration(ClassDeclaration decl) {
        String modifiers = toString(decl.getModifiers());
        if (!modifiers.isEmpty()) {
            modifiers += " ";
        }

        return modifiers + "class " + toString(decl.getName());
    }

    private String toStringAnnotations(List<Annotation> annotations) {
        StringBuilder builder = new StringBuilder();
        for (Annotation annotation : annotations) {
            builder.append(toString(annotation));
            builder.append("\n");
        }
        return builder.toString();
    }

    private String toStringClassDefinition(ClassDefinition def) {
        StringBuilder builder = new StringBuilder();
        builder.append(toStringAnnotations(def.getDeclaration().getAnnotations()));

        String declaration = toString(def.getDeclaration());
        builder.append(declaration);

        String body = toString(def.getBody());
        if (_openBracketOnSameLine)
        { builder.append(" ").append(body); }
        else
        { builder.append("\n").append(indent(body)); }

        return builder.toString();
    }

    public String toStringFloatLiteral(FloatLiteral literal) {
        String s = Double.toString(literal.getDoubleValue());
        if (!literal.isDoublePrecision()) {
            s = s.concat("f");
        }
        return s;
    }

    public String toStringIntegerLiteral(IntegerLiteral literal) {
        String s = literal.getStringValue(false);
        if (literal.isLong()) {
            s = s.concat("L");
        }
        return s;
    }

    public String toStringStringLiteral(StringLiteral literal) {
        if (literal.isMultiline()) {
            return "\"\"\"%s\"\"\"".formatted(literal.getEscapedValue());
        }

        return "\"%s\"".formatted(literal.getEscapedValue());
    }

    private String toString(BinaryExpression expr, String sign) {
        expr = parenFiller.process(expr);
        Expression left = expr.getLeft();
        Expression right = expr.getRight();
        if (expr instanceof PowOp) {
            return toString(new MethodCall(new SimpleIdentifier("Math"),
                    new SimpleIdentifier("pow"), left, right));
        }

        return String.format("%s %s %s", toString(left), sign, toString(right));
    }

    @Override
    public OperatorToken mapToToken(Expression expr) {
        String tok = switch (expr) {
            case AddOp op -> "+";
            case SubOp op -> "-";
            case ScopedIdentifier op -> ".";
            case MulOp op -> "*";
            case DivOp op -> "/";
            case ModOp op -> "%";
            case PowOp op -> "CALL_(";
            case MatMulOp op -> "CALL_(";
            case ContainsOp op -> "CALL_(";
            case EqOp op -> "==";
            case CastTypeExpression op -> "CAST";
            case NotEqOp op -> "!=";
            case GeOp op -> ">=";
            case ReferenceEqOp op -> "==";
            case LeOp op -> "<=";
            case LtOp op -> "<";
            case GtOp op -> ">";
            case InstanceOfOp op -> "instanceof";
            case ShortCircuitAndOp op -> "&&";
            case ShortCircuitOrOp op -> "||";
            case BitwiseAndOp op -> "&";
            case BitwiseOrOp op -> "|";
            case LeftShiftOp op -> "<<";
            case RightShiftOp op -> ">>";
            case FunctionCall op -> "CALL_(";
            case TernaryOperator op -> "?";
            case NewExpression op -> "new";
            case QualifiedIdentifier op -> "::";
            case MemberAccess op -> ".";
            case XorOp op -> "^";
            case IndexExpression op -> "[";
            case ThreeWayComparisonOp op -> "<=>";
            case AssignmentExpression as -> {
                AugmentedAssignmentOperator op = as.getAugmentedOperator();
                yield switch (op) {
                    case NONE -> "=";
                    case ADD -> "+=";
                    case SUB -> "-=";
                    case MUL -> "*=";
                    // В Java тип деления определяется не видом операции, а типом операндов,
                    // поэтому один и тот же оператор
                    case DIV, FLOOR_DIV -> "/=";
                    case BITWISE_AND -> "&=";
                    case BITWISE_OR -> "|=";
                    case BITWISE_XOR -> "^=";
                    case BITWISE_SHIFT_LEFT -> "<<=";
                    case BITWISE_SHIFT_RIGHT -> ">>=";
                    case MOD -> "%=";
                    default -> throw new IllegalStateException("Unexpected type of augmented assignment operator: " + op);
                };
            }
            case FloorDivOp op -> "CALL_("; // чтобы взять токен такого же приоритета, решение не очень
            // unary section
            case NotOp op -> "!";
            case InversionOp op -> "~";
            case UnaryMinusOp op -> "UMINUS";
            case UnaryPlusOp op -> "UPLUS";
            case PostfixIncrementOp op -> "++";
            case PrefixIncrementOp op -> "++U";
            case PostfixDecrementOp op -> "--";
            case PrefixDecrementOp op -> "--U";
            default -> null;
        };
        return ctx.requireTokenizer().getOperatorByTokenName(tok);
    }

    public String toStringAddOp(AddOp op) {
        return toString(op, "+");
    }

    public String toStringSubOp(SubOp op) {
        return toString(op, "-");
    }

    public String toStringMulOp(MulOp op) {
        return toString(op, "*");
    }

    public String toStringDivOp(DivOp op) {
        return toString(op, "/");
    }

    public String toStringModOp(ModOp op) {
        return toString(op, "%");
    }

    public String toStringFloorDivOp(FloorDivOp op) {
        return String.format("(long) (%s)", toString(op, "/"));
    }

    public String toStringEqOp(EqOp op) {
        return toString(op, "==");
    }

    public String toStringGeOp(GeOp op) {
        return toString(op, ">=");
    }

    public String toStringGtOp(GtOp op) {
        return toString(op, ">");
    }

    public String toStringLeOp(LeOp op) {
        return toString(op, "<=");
    }

    public String toStringLtOp(LtOp op) {
        return toString(op, "<");
    }

    private String wrapperTypeName(Type possiblePrimitiveType) {
        return switch (possiblePrimitiveType) {
            case IntType t -> switch (t.size) {
                case 8 -> "Byte";
                case 16 -> "Short";
                case 32 -> "Integer";
                default -> "Long";
            };
            case CharacterType t -> "Character";
            case BooleanType t -> "Boolean";
            case FloatType t -> t.size == 32 ? "Float" : "Double";
            default -> toString(possiblePrimitiveType);
        };
    }

    public String toStringInstanceOfOp(InstanceOfOp op) {
        return toString(op.getLeft()) + " instanceof " +
                wrapperTypeName(op.getType());
    }

    public String toStringNotEqOp(NotEqOp op) {
        return toString(op, "!=");
    }

    public String toStringShortCircuitAndOp(ShortCircuitAndOp op) {
        return toString(op, "&&");
    }

    public String toStringShortCircuitOrOp(ShortCircuitOrOp op) {
        return toString(op, "||");
    }

    public String toStringNotOp(NotOp op) {
        var arg = op.getArgument();

        // These expressions don't need parentheses as they have higher precedence or are atomic
        if (arg instanceof ParenthesizedExpression ||
                arg instanceof Identifier ||  // All identifier types (SimpleIdentifier, ScopedIdentifier, QualifiedIdentifier, SelfReference, etc.)
                arg instanceof Literal ||     // All literal types
                arg instanceof FunctionCall ||
                arg instanceof MemberAccess ||
                arg instanceof IndexExpression ||
                arg instanceof CastTypeExpression ||
                arg instanceof UnaryExpression ||  // Other unary operators have same precedence level
                arg instanceof ObjectNewExpression ||
                arg instanceof ArrayNewExpression) {
            return String.format("!%s", toString(arg));
        }

        // These expressions need parentheses as they have lower precedence
        return String.format("!(%s)", toString(arg));
    }

    public String toStringMatMulOp(MatMulOp op) {
        return String.format("matmul(%s, %s)", toString(op.getLeft()), toString(op.getRight()));
    }

    public String toStringParenthesizedExpression(ParenthesizedExpression expr) {
        return String.format("(%s)", toString(expr.getExpression()));
    }

    private String toString(AugmentedAssignmentOperator op, Expression left, Expression right) {
        // В Java нет встроенного оператора возведения в степень, следовательно,
        // нет и соотвествующего оператора присванивания, поэтому этот случай обрабатываем по особому
        if (op == POW) {
            return "%s = Math.pow(%s, %s)".formatted(toString(left), toString(left), toString(right));
        }

        String o = switch (op) {
            case NONE -> "=";
            case ADD -> "+=";
            case SUB -> "-=";
            case MUL -> "*=";
            // В Java тип деления определяется не видом операции, а типом операндов,
            // поэтому один и тот же оператор
            case DIV, FLOOR_DIV -> "/=";
            case BITWISE_AND -> "&=";
            case BITWISE_OR -> "|=";
            case BITWISE_XOR -> "^=";
            case BITWISE_SHIFT_LEFT -> "<<=";
            case BITWISE_SHIFT_RIGHT -> ">>=";
            case MOD -> "%=";
            default -> throw new IllegalStateException("Unexpected type of augmented assignment operator: " + op);
        };

        if (right instanceof IntegerLiteral integerLiteral
                && (long) integerLiteral.getValue() == 1
                && (o.equals("+=") || o.equals("-="))) {
            o = switch (o) {
                case "+=" -> "++";
                case "-=" -> "--";
                default -> throw new IllegalArgumentException();
            };

            return toString(left) + o;
        }

        return "%s %s %s".formatted(toString(left), o, toString(right));
    }

    public String toStringAssignmentExpression(AssignmentExpression expr) {
        expr = (AssignmentExpression) parenFiller.process(expr);
        return toString(expr.getAugmentedOperator(), expr.getLValue(), expr.getRValue());
    }

    public String toStringAssignmentStatement(AssignmentStatement stmt) {
        AugmentedAssignmentOperator assignmentOperator = stmt.getAugmentedOperator();
        Expression leftValue = stmt.getLValue();
        Expression rightValue = stmt.getRValue();

        if (leftValue instanceof SimpleIdentifier identifier
                && assignmentOperator == AugmentedAssignmentOperator.NONE) {
            Type variableType = ctx.getVisibilityScope().scope().getVariableType(identifier);
            // Objects.requireNonNull(variableType);

            if (variableType == null && _autoVariableDeclaration) {
                variableType = ctx.getVisibilityScope().scope().findType(identifier).orElseThrow();

                String typeName = toString(variableType);
                String variableName = toString(identifier);
                return "%s %s = %s;".formatted(typeName, variableName, toString(rightValue));
            }
        }

        return "%s;".formatted(toString(assignmentOperator, leftValue, rightValue));
    }

    private String toStringType(Type type) {
        return switch (type) {
            case FloatType floatType -> toStringFloatType(floatType);
            case IntType intType -> toStringIntType(intType);
            case BooleanType booleanType -> toStringBooleanType(booleanType);
            case StringType stringType -> toStringStringType(stringType);
            case NoReturn voidType -> toStringNoReturn(voidType);
            case UnknownType unknownType -> toStringUnknownType(unknownType);
            case ArrayType arrayType -> toStringArrayType(arrayType);
            case UserType userType -> toStringUserType(userType);
            case CharacterType characterType -> toStringCharacterType(characterType);
            case SetType setType -> toStringSetType(setType);
            case DictionaryType dictType -> toStringDictionaryType(dictType);
            case PlainCollectionType plain -> toStringPlainCollectionType(plain);
            case PointerType ptr -> toString(ptr.getTargetType());
            default -> throw new IllegalStateException("Unexpected value: " + type.getClass());
        };
    }

    private String toStringCharacterType(CharacterType characterType) {
        return "char";
    }

    public String toStringFloatType(FloatType type) {
        return type.size == 32 ? "float" : "double";
    }

    public String toStringIntType(IntType type) {
        return switch (type.size) {
            case 8 -> "byte";
            case 16 -> "short";
            case 32 -> "int";
            default -> "long";
        };
    }

    public String toStringBooleanType(BooleanType type) {
        return "boolean";
    }

    public String toStringSetType(SetType type) {
        var typeName = wrapperTypeName(type.getItemType());
        return String.format("java.util.HashSet<%s>", typeName);
    }

    public String toStringPlainCollectionType(PlainCollectionType type) {
        var typeName = wrapperTypeName(type.getItemType());
        return String.format("java.util.ArrayList<%s>", typeName);
    }

    public String toStringDictionaryType(DictionaryType type) {
        var keyTypeName = wrapperTypeName(type.getKeyType());
        var valueTypeName = wrapperTypeName(type.getValueType());
        return String.format("java.util.TreeMap<%s, %s>", keyTypeName, valueTypeName);
    }

    private String toStringStringType(StringType type) {
        return "String";
    }

    private String toStringNoReturn(NoReturn type) {
        return "void";
    }

    private String toStringUnknownType(UnknownType type) {
        return "Object";
    }

    private String toStringShape(Shape shape) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < shape.getDimensionCount(); i++) {
            builder.append("[");

            Expression dimension = shape.getDimension(i);
            if (dimension != null) {
                builder.append(toString(dimension));
            }

            builder.append("]");
        }

        return builder.toString();
    }

    private String toStringArrayType(ArrayType type) {
        StringBuilder builder = new StringBuilder();

        String baseType = toString(type.getItemType());
        builder.append(baseType);
        builder.append(toString(type.getShape()));

        return builder.toString();
    }

    private String toString(VariableDeclarator varDecl, Type type) {
        StringBuilder builder = new StringBuilder();

        SimpleIdentifier identifier = varDecl.getIdentifier();
        Type variableType = new UnknownType();
        Expression rValue = varDecl.getRValue();
        if (rValue != null) {
            variableType = ctx.inferType(rValue);
        }

        if (variableType instanceof UnknownType)
            variableType = type;

        ctx.getVisibilityScope().scope().changeVariableType(
                identifier,
                SimpleTypeInferrer.chooseGeneralType(variableType, type)
        );

        String identifierName = toString(identifier);
        builder.append(identifierName);

        if (rValue instanceof ArrayLiteral arr && type instanceof ListType) {
            rValue = new ListLiteral(arr.getList());
            ((ListLiteral) rValue).setTypeHint(arr.getTypeHint());
        }

        if (rValue != null) {
            builder.append(" = ").append(toString(rValue));
        }

        return builder.toString();
    }

    public String toStringVariableDeclaration(VariableDeclaration stmt) {
        StringBuilder builder = new StringBuilder();

        Type declarationType = stmt.getType();
        String type = toString(declarationType);
        if (declarationType.isConst()) {
            builder.append("final ");
        }
        builder
                .append(type)
                .append(" ");

        for (VariableDeclarator varDecl : stmt.getDeclarators()) {
            builder.append(toString(varDecl, stmt.getType())).append(", ");


        }
        // Чтобы избежать лишней головной боли на проверки "а последняя ли это декларация",
        // я автоматически после каждой декларации добавляю запятую и пробел,
        // но для последней декларации они не нужны, поэтому эти два символа удаляются,
        // как сделать красивее - не знаю...
        builder.deleteCharAt(builder.length() - 1);
        builder.deleteCharAt(builder.length() - 1);

        builder.append(";");
        return builder.toString();
    }

    private void increaseIndentLevel() {
        _indentLevel++;
    }

    private void decreaseIndentLevel() {
        _indentLevel--;

        if (_indentLevel < 0) {
            throw new MeaningTreeException("Indentation level can't be less than zero");
        }
    }

    private String indent(String s) {
        if (_indentLevel == 0) {
            return s;
        }

        return _indentation.repeat(Math.max(0, _indentLevel)) + s;
    }

    public String toStringCompoundStatement(CompoundStatement stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        increaseIndentLevel();
        for (Node node : ctx.iterateBody(stmt)) {
            String s = toString(node);
            if (s.isEmpty()) {
                continue;
            }

            s = indent(String.format("%s\n", s));
            builder.append(s);
        }
        decreaseIndentLevel();
        builder.append(indent("}"));
        return builder.toString();
    }

    public String toStringExpressionStatement(ExpressionStatement stmt) {
        if (stmt.getExpression() == null) {
            return ";";
        }
        return String.format("%s;", toString(stmt.getExpression()));
    }

    public String toStringSimpleIdentifier(SimpleIdentifier identifier) {
        return identifier.getName();
    }

    private String toStringConditionBranch(ConditionBranch branch) {
        StringBuilder builder = new StringBuilder();

        String cond = toString(branch.getCondition());
        builder
                .append("(")
                .append(cond)
                .append(")");

        Statement body = branch.getBody();
        if (body instanceof CompoundStatement compStmt) {
            // Если телом ветки является блок кода, то необходимо определить
            // куда нужно добавить фигурные скобки и добавить само тело
            // Пример (для случая, когда скобка на той же строке):
            // if (a > b) {
            //     max = a;
            // }
            if (_openBracketOnSameLine) {
                builder
                        .append(" ")
                        .append(toString(compStmt));
            }
            else {
                builder
                        .append("\n")
                        .append(indent(toString(compStmt)));
            }
        }
        else {
            // В случае если тело ветки не блок кода, то добавляем отступ
            // и вставляем тело
            // Пример:
            // if (a > b)
            //     max = a;
            increaseIndentLevel();
            builder.append("\n").append(indent(toString(body)));
            decreaseIndentLevel();
        }

        return builder.toString();
    }

    private String toStringBinaryComparison(BinaryComparison binComp) {
        binComp = (BinaryComparison) parenFiller.process(binComp);
        return switch (binComp) {
            case EqOp op -> toStringEqOp(op);
            case GeOp op -> toStringGeOp(op);
            case GtOp op -> toStringGtOp(op);
            case LeOp op -> toStringLeOp(op);
            case LtOp op -> toStringLtOp(op);
            case NotEqOp op -> toStringNotEqOp(op);
            case ContainsOp op -> toStringContainsOp(op);
            case ReferenceEqOp op -> toStringReferenceEqOp(op);
            default -> throw new IllegalStateException("Unexpected value: " + binComp);
        };
    }

    private String toStringContainsOp(ContainsOp op) {
        String neg = op.isNegative() ? "!" : "";
        String left = toString(op.getRight());
        if (!(op.getRight() instanceof Identifier)) {
            left = "(".concat(left).concat(")");
        }
        return neg.concat(String.format("%s.contains(%s)", left, toString(op.getLeft())));
    }

    private String toStringReferenceEqOp(ReferenceEqOp op) {
        String neg = op.isNegative() ? "!=" : "==";
        return String.format("%s %s %s", toString(op.getLeft()), neg, toString(op.getRight()));
    }

    public String toStringCompoundComparison(CompoundComparison cmp) {
        StringBuilder builder = new StringBuilder();

        for (BinaryComparison binComp : cmp.getComparisons()) {
            builder.append(toString(binComp)).append(" && ");
        }

        builder.delete(builder.length() - 4, builder.length());

        return builder.toString();
    }

    public String toStringIfStatement(IfStatement stmt) {
        StringBuilder builder = new StringBuilder();

        builder.append("if ");
        List<ConditionBranch> branches = stmt.getBranches();
        builder
                .append(toString(branches.getFirst()))
                .append("\n");

        for (ConditionBranch branch : branches.subList(1, branches.size())) {
            builder
                    .append(indent("else if "))
                    .append(toString(branch))
                    .append("\n");
        }

        if (stmt.hasElseBranch()) {
            builder.append(indent("else"));

            Statement elseBranch = stmt.getElseBranch();
            if (elseBranch instanceof IfStatement innerIfStmt) {
                builder
                        .append(" ")
                        .append(toString(innerIfStmt));
            }
            else if (elseBranch instanceof CompoundStatement innerCompStmt) {
                if (_openBracketOnSameLine) {
                    builder
                            .append(" ")
                            .append(toString(innerCompStmt));
                }
                else {
                    builder
                            .append("\n")
                            .append(indent(toString(innerCompStmt)));
                }
            }
            else {
                increaseIndentLevel();
                builder
                        .append("\n")
                        .append(indent(toString(elseBranch)));
                decreaseIndentLevel();
            }
        }
        else {
            // Удаляем лишний перевод строки, если ветки else нет
            builder.deleteCharAt(builder.length() - 1);
        }

        return builder.toString();
    }

    private String toStringHasInitialization(HasInitialization init) {
        String result = switch (init) {
            case AssignmentExpression expr -> toStringAssignmentExpression(expr);
            case AssignmentStatement stmt -> toStringAssignmentStatement(stmt);
            case VariableDeclaration decl -> toStringVariableDeclaration(decl);
            case MultipleAssignmentStatement multipleAssignmentStatement -> {
                // Трансляция MultipleAssignmentStatement по умолчанию не подходит -
                // в результате будут получены присваивания, написанные через точку с запятой.
                // Поэтому вручную получаем список присваиваний и создаем правильное отображение.
                StringBuilder builder = new StringBuilder();

                for (AssignmentStatement assignmentStatement : multipleAssignmentStatement.getStatements()) {
                    AssignmentExpression assignmentExpression = new AssignmentExpression(
                            assignmentStatement.getLValue(),
                            assignmentStatement.getRValue()
                    );
                    builder
                            .append(toString(assignmentExpression))
                            .append(", ");
                }

                // Удаляем лишние пробел и запятую в конце последнего присвоения
                if (builder.length() > 2) {
                    builder.deleteCharAt(builder.length() - 1);
                    builder.deleteCharAt(builder.length() - 1);
                }

                yield builder.toString();
            }
            default -> throw new IllegalStateException("Unexpected value: " + init);
        };
        result = this.applyHooks((Node) init, result);
        return result;
    }

    public String toStringGeneralForLoop(GeneralForLoop generalForLoop) {
        StringBuilder builder = new StringBuilder();

        builder.append("for (");

        boolean addSemi = true;
        if (generalForLoop.hasInitializer()) {
            String init = toStringHasInitialization(generalForLoop.getInitializer());
            if (generalForLoop.getInitializer() instanceof VariableDeclaration) {
                addSemi = false;
            }
            builder.append(init);
        }
        if (addSemi) {
            builder.append("; ");
        }
        else {
            builder.append(" ");
        }

        if (generalForLoop.hasCondition()) {
            String condition = toString(generalForLoop.getCondition());
            builder.append(condition);
        }
        builder.append("; ");

        if (generalForLoop.hasUpdate()) {
            String update = toString(generalForLoop.getUpdate());
            builder.append(update);
        }

        Statement body = generalForLoop.getBody();
        if (body instanceof CompoundStatement compoundStatement) {
            builder.append(")");

            if (_openBracketOnSameLine) {
                builder
                        .append(" ")
                        .append(toString(compoundStatement));
            }
            else {
                builder.append("\n");
                builder.append(indent(toString(body)));
            }
        }
        else {
            builder.append(")\n");
            increaseIndentLevel();
            builder.append(indent(toString(body)));
            decreaseIndentLevel();
        }

        return builder.toString();
    }

    private String getForRangeUpdate(RangeForLoop forRangeLoop) {
        if (forRangeLoop.getRange().getType() == Range.Type.UP) {
            long stepValue;
            try {
                stepValue = forRangeLoop.getStepValueAsLong();
            } catch (IllegalStateException exception) {
                return String.format("%s += %s", toString(forRangeLoop.getIdentifier()), toString(forRangeLoop.getStep()));
            }

            if (stepValue == 1) {
                return String.format("%s++", toString(forRangeLoop.getIdentifier()));
            }
            else {
                return String.format("%s += %d", toString(forRangeLoop.getIdentifier()), stepValue);
            }
        }
        else if (forRangeLoop.getRange().getType() == Range.Type.DOWN) {
            long stepValue;
            try {
                stepValue = forRangeLoop.getStepValueAsLong();
            } catch (IllegalStateException exception) {
                return String.format("%s -= %s", toString(forRangeLoop.getIdentifier()), toString(forRangeLoop.getStep()));
            }

            if (stepValue == 1) {
                return String.format("%s--", toString(forRangeLoop.getIdentifier()));
            }
            else {
                return String.format("%s -= %d", toString(forRangeLoop.getIdentifier()), stepValue);
            }
        }

        throw new UnsupportedViewingException("Can't determine range type in for loop");
    }

    private String getForRangeHeader(RangeForLoop forRangeLoop) {
        if (forRangeLoop.getRange().getType() == Range.Type.UP) {
            String header = "int %s = %s; %s %s %s; %s";
            String compOperator = forRangeLoop.isExcludingStop() ? "<" : "<=";
            String result = header.formatted(
                    toString(forRangeLoop.getIdentifier()),
                    toString(forRangeLoop.getStart()),
                    toString(forRangeLoop.getIdentifier()),
                    compOperator,
                    toString(forRangeLoop.getStop()),
                    getForRangeUpdate(forRangeLoop)
            );
            result = this.applyHooks(forRangeLoop.getRange(), result);
            return result;
        }
        else if (forRangeLoop.getRange().getType() == Range.Type.DOWN) {
            String header = "int %s = %s; %s %s %s; %s";
            String compOperator = forRangeLoop.isExcludingStop() ? ">" : ">=";
            String result = header.formatted(
                    toString(forRangeLoop.getIdentifier()),
                    toString(forRangeLoop.getStart()),
                    toString(forRangeLoop.getIdentifier()),
                    compOperator,
                    toString(forRangeLoop.getStop()),
                    getForRangeUpdate(forRangeLoop)
            );
            result = this.applyHooks(forRangeLoop.getRange(), result);
            return result;
        }

        throw new UnsupportedViewingException("Can't determine range type in for loop");
    }

    public String toStringRangeForLoop(RangeForLoop forRangeLoop) {
        StringBuilder builder = new StringBuilder();

        String header = "for (" + getForRangeHeader(forRangeLoop) + ")";
        builder.append(header);

        Statement body = forRangeLoop.getBody();
        if (body instanceof CompoundStatement compoundStatement) {
            if (_openBracketOnSameLine) {
                builder
                        .append(" ")
                        .append(toString(compoundStatement));
            }
            else {
                builder.append("\n");
                builder.append(indent(toString(body)));
            }
        }
        else {
            builder.append("\n");
            increaseIndentLevel();
            builder.append(indent(toString(body)));
            decreaseIndentLevel();
        }

        return builder.toString();
    }

    private String makeSimpleProgram(List<Node> nodes) {
        StringBuilder builder = new StringBuilder();

        builder.append("package main;\n\n");

        builder.append("public class Main {\n\n");
        increaseIndentLevel();

        var mainMethod = getMainMethod(nodes);
        var otherMethods = getOtherMethods(nodes);
        var notMethods = getNotMethods(nodes);

        if (mainMethod != null) {
            // Добавляем все не-методы в body main
            var mainBody = mainMethod.getBody();
            for (Node node : notMethods) {
                mainBody.insert(mainBody.getLength(), node);
            }

            // Вставляем mainMethod (с уже добавленными не-методами)
            // Вставляем фиксированный main
            builder.append(indent("public static void main(String[] args) {\n"));
            _methodReturnType = new NoReturn();
            increaseIndentLevel();

            for (var node : mainBody.getNodes()) {
                builder.append(indent(toString(node))).append("\n");
            }

            _methodReturnType = null;

            decreaseIndentLevel();
            builder.append(indent("}\n"));
        }
        else {
            builder.append(indent("public static void main(String[] args) {\n"));
            increaseIndentLevel();

            for (var node : notMethods) {
                builder.append(indent(toString(node))).append("\n");
            }

            decreaseIndentLevel();
            builder.append(indent("}\n"));
        }

        // Вставляем все другие методы
        for (MethodDefinition method : otherMethods) {
            builder.append(indent(toString(method)));
            builder.append("\n");
        }

        decreaseIndentLevel();
        builder.append("}\n");

        return builder.toString();
    }

    @Nullable
    private MethodDefinition getMainMethod(List<Node> nodes) {
        for (var node : nodes) {
            if (node instanceof FunctionDefinition functionDefinition
                    && functionDefinition.getName().toString().equals("main")) {
                return functionDefinition.makeMethod(
                        null,
                        List.of(DeclarationModifier.PUBLIC, DeclarationModifier.STATIC)
                );
            }
        }

        return null;
    }

    private List<MethodDefinition> getOtherMethods(List<Node> nodes) {
        var methods = new ArrayList<MethodDefinition>();

        for (var node : nodes) {
            if (node instanceof FunctionDefinition functionDefinition
                    && !functionDefinition.getName().toString().equals("main")) {
                methods.add(
                        functionDefinition.makeMethod(
                                null,
                                List.of(DeclarationModifier.PUBLIC)
                        )
                );
            }
        }

        return methods;
    }

    private List<Node> getNotMethods(List<Node> nodes) {
        var notMethods = new ArrayList<Node>();
        for (var node : nodes) {
            if (!(node instanceof FunctionDefinition)) {
                notMethods.add(node);
            }
        }
        return notMethods;
    }

    public String toStringProgramEntryPoint(ProgramEntryPoint entryPoint) {
        List<Node> nodes = entryPoint.getBody();

        if (getConfigParameter(TranslationUnitMode.class).orElse(true) && !entryPoint.hasMainClass()) {
            return makeSimpleProgram(nodes);
        }

        StringBuilder builder = new StringBuilder();
        for (Node node : ctx.iterateBody(entryPoint)) {
            builder.append("%s\n".formatted(toString(node)));
        }

        return builder.toString();
    }

    public String toStringScopedIdentifier(ScopedIdentifier scopedIdent) {
        StringBuilder builder = new StringBuilder();

        for (var ident : scopedIdent.getScopeResolution()) {
            builder.append(toString(ident)).append(".");
        }
        builder.deleteCharAt(builder.length() - 1); // Удаляем последнюю точку

        return builder.toString();
    }

    public String toStringQualifiedIdentifier(QualifiedIdentifier qualIdent) {
        qualIdent = parenFiller.process(qualIdent);
        StringBuilder builder = new StringBuilder();
        builder.append(toString(qualIdent.getScope()));
        builder.append("::");
        builder.append(toString(qualIdent.getMember()));
        return builder.toString();
    }

    public String toStringFunctionCall(FunctionCall funcCall) {
        StringBuilder builder = new StringBuilder();

        builder.append(toString(funcCall.getFunction())).append("(");
        for (Expression expr : funcCall.getArguments()) {
            builder.append(toString(expr)).append(", ");
        }

        if (!funcCall.getArguments().isEmpty()) {
            // Удаляем два последних символа - запятую и пробел
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
        }
        builder.append(")");

        return builder.toString();
    }

    public String toStringWhileLoop(WhileLoop whileLoop) {
        String header = "while (" + toString(whileLoop.getCondition()) + ")";

        Statement body = whileLoop.getBody();
        if (body instanceof CompoundStatement compStmt) {
            return header + (_openBracketOnSameLine ? " " : "\n") + toString(compStmt);
        }
        else {
            increaseIndentLevel();
            String result = header + "\n" + indent(toString(body));
            decreaseIndentLevel();
            return result;
        }
    }

    private String toStringPostfixIncrementOp(PostfixIncrementOp inc) {
        return toString(inc.getArgument()) + "++";
    }

    private String toStringPostfixDecrementOp(PostfixDecrementOp dec) {
        return toString(dec.getArgument()) + "--";
    }

    private String toStringPrefixIncrementOp(PrefixIncrementOp inc) {
        return "++" + toString(inc.getArgument());
    }

    private String toStringPrefixDecrementOp(PrefixDecrementOp dec) {
        return "--" + toString(dec.getArgument());
    }

    private String toStringPowOp(PowOp op) {
        return "Math.pow(%s, %s)".formatted(toString(op.getLeft()), toString(op.getRight()));
    }

    private String toStringPackageDeclaration(PackageDeclaration decl) {
        return "package %s;".formatted(toString(decl.getPackageName()));
    }
}
