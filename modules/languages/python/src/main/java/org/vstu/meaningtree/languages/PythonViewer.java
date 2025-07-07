package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.configs.params.DisableCompoundComparisonConversion;
import org.vstu.meaningtree.languages.configs.params.EnforceEntryPoint;
import org.vstu.meaningtree.languages.configs.params.ExpressionMode;
import org.vstu.meaningtree.languages.utils.PythonSpecificFeatures;
import org.vstu.meaningtree.languages.utils.Tab;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.ClassDefinition;
import org.vstu.meaningtree.nodes.definitions.FunctionDefinition;
import org.vstu.meaningtree.nodes.definitions.MethodDefinition;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.*;
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
import org.vstu.meaningtree.nodes.io.FormatInput;
import org.vstu.meaningtree.nodes.io.FormatPrint;
import org.vstu.meaningtree.nodes.memory.MemoryAllocationCall;
import org.vstu.meaningtree.nodes.memory.MemoryFreeCall;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.statements.*;
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
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class PythonViewer extends LanguageViewer {
    public PythonViewer(LanguageTokenizer tokenizer) {
        super(tokenizer);
    }

    @Override
    public String toString(Node node) {
        // Для dummy узлов ничего не выводим
        if (node.hasLabel(Label.DUMMY)) {
            return "";
        }

        Tab tab = new Tab();
        return toString(node, tab);
    }

    public String toString(Tab tab, Node ... nodes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < nodes.length; i++) {
            builder.append(toString(nodes[i], tab));
            if (i != nodes.length - 1) {
                builder.append("\n");
                builder.append(tab);
            }
        }
        return builder.toString();
    }

    public String toString(Node node, Tab tab) {
        return switch (node) {
            case ProgramEntryPoint programEntryPoint -> entryPointToString(programEntryPoint, tab);
            case AssignmentExpression assignmentExpr -> assignmentExpressionToString(assignmentExpr);
            case BinaryComparison cmpNode -> comparisonToString(cmpNode);
            case BinaryExpression binaryExpression -> binaryOpToString(binaryExpression);
            case IfStatement ifStatement -> conditionToString(ifStatement, tab);
            case PointerPackOp ptr -> toString(ptr);
            case PointerUnpackOp ptr -> toString(ptr);
            case UnaryExpression exprNode -> unaryToString(exprNode);
            case CompoundStatement exprNode -> blockToString(exprNode, tab);
            case CompoundComparison compound -> compoundComparisonToString(compound);
            case Type type -> typeToString(type);
            case FormatPrint fmt -> throw new UnsupportedViewingException("Format print is not supported in Python");
            case FormatInput fmt -> throw new UnsupportedViewingException("Format input is not supported in Python");
            case Identifier identifier -> identifierToString(identifier);
            case IndexExpression indexExpr -> {
                indexExpr = parenFiller.process(indexExpr);
                yield String.format("%s[%s]", toString(indexExpr.getExpression()), toString(indexExpr.getIndex()));
            }
            case MemberAccess memAccess -> {
                memAccess = parenFiller.process(memAccess);
                yield String.format("%s.%s", toString(memAccess.getExpression()), toString(memAccess.getMember()));
            }
            case TernaryOperator ternary -> {
                ternary = parenFiller.process(ternary);
                yield String.format("%s if %s else %s", toString(ternary.getThenExpr()), toString(ternary.getCondition()), toString(ternary.getElseExpr()));
            }
            case ParenthesizedExpression paren -> String.format("(%s)", toString(paren.getExpression()));
            case ObjectNewExpression newExpr -> callsToString(newExpr);
            case ArrayNewExpression newExpr -> callsToString(newExpr);
            case MemoryAllocationCall memoryAllocationCall -> toString(memoryAllocationCall.toNew());
            case MemoryFreeCall freeCall -> toString(freeCall.toDelete());
            case FunctionCall funcCall -> callsToString(funcCall);
            case BreakStatement ignored2 -> "break";
            case DeleteStatement delStmt -> String.format("del %s", toString(delStmt.getTarget()));
            case DeleteExpression ignored2 -> throw new UnsupportedViewingException("Delete expressions is unsupported on Python");
            case Range range -> rangeToString(range);
            case ContinueStatement ignored1 -> "continue";
            case ConstructorCall call -> String.format("%s(%s)", toString(call.getOwner()), argumentsToString(call.getArguments()));
            case Comment comment -> commentToString(comment);
            case Literal literal -> literalToString(literal);
            case SizeofExpression ignored -> throw new UnsupportedViewingException("Sizeof is disabled in this language");
            case AssignmentStatement assignmentStatement -> assignmentToString(assignmentStatement);
            case VariableDeclaration varDecl -> variableDeclarationToString(varDecl);
            case ForLoop forLoop -> loopToString(forLoop, tab);
            case InfiniteLoop infLoop -> loopToString(infLoop, tab);
            case WhileLoop whileLoop -> loopToString(whileLoop, tab);
            case DoWhileLoop doWhileLoop -> loopToString(doWhileLoop, tab);
            case SwitchStatement switchStmt -> loopToString(switchStmt, tab);
            case MethodDefinition methodDef -> functionToString(methodDef, tab);
            case FunctionDefinition funcDef -> functionToString(funcDef, tab);
            case ClassDeclaration classDecl -> classDeclToString(classDecl, tab);
            case ClassDefinition classDef -> classToString(classDef, tab);
            case FunctionDeclaration funcDecl -> functionDeclarationToString(funcDecl, tab);
            case Import importStmt -> importToString(importStmt);
            case ExpressionStatement exprStmt -> toString(exprStmt);
            case ReturnStatement returnStmt -> returnToString(returnStmt);
            case ArrayInitializer arrayInit -> arrayInitializerToString(arrayInit);
            case DefinitionArgument arg -> definitionArgumentToString(arg);
            case Include incl -> String.format("import %s", toString(incl.getFileName()));
            case PackageDeclaration packageDecl -> String.format("import %s", toString(packageDecl.getPackageName()));
            case CommaExpression ignored -> throw new UnsupportedViewingException("Comma is unsupported in this language");
            case ExpressionSequence exprSeq -> String.join(", ", exprSeq.getExpressions().stream().map((Expression nd) -> toString(nd, tab)).toList().toArray(new String[0]));
            case MultipleAssignmentStatement stmtSequence -> assignmentToString(stmtSequence);
            case CastTypeExpression cast -> callsToString(cast);
            case Comprehension compr -> comprehensionToString(compr);
            case EmptyStatement emptyStatement -> emptyStatementToString(emptyStatement);
            case null -> throw new MeaningTreeException("Null node detected");
            default -> throw new UnsupportedViewingException("Unsupported tree element: " + node.getClass().getName());
        };
    }

    private String emptyStatementToString(EmptyStatement emptyStatement) {
        return "pass";
    }

    private String definitionArgumentToString(DefinitionArgument arg) {
        if (arg.isListUnpacking()) {
            return "*%s".formatted(toString(arg.getInitialExpression()));
        } else if (arg.isDictUnpacking()) {
            return "**%s".formatted(toString(arg.getInitialExpression()));
        } else if (arg.hasVisibleName()) {
            return "%s=%s".formatted(toString(arg.getName()), toString(arg.getInitialExpression()));
        } else {
            return toString(arg.getInitialExpression());
        }
    }

    public String toString(PointerPackOp ptr) {
        return toString(ptr.getArgument());
    }

    public String toString(PointerUnpackOp ptr) {
        if (ptr.getArgument() instanceof SubOp) {
            throw new UnsupportedViewingException("Subtraction of pointers cannot be converted to indexing");
        }
        return toString(ptr.getArgument());
    }

    private String toString(ExpressionStatement stmt) {
        if (stmt.getExpression() == null) {
            return "\n";
        }
        if (stmt.getExpression() instanceof AssignmentExpression assignment) {
            return toString(assignment.toStatement());
        }
        return toString(stmt.getExpression());
    }

    private String comprehensionToString(Comprehension compr) {
        char startBracket = '[';
        char endBracket = ']';
        if (compr.getItem() instanceof KeyValuePair || compr.getItem() instanceof Comprehension.SetItem) {
            startBracket = '{';
            endBracket = '}';
        }

        StringBuilder comprehension = new StringBuilder();
        comprehension.append(startBracket);
        if (compr.getItem() instanceof KeyValuePair pair) {
            comprehension.append(String.format("%s: %s", toString(pair.key()), toString(pair.value())));
        } else if (compr.getItem() instanceof Comprehension.SetItem item) {
            comprehension.append(toString(item.value()));
        } else if (compr.getItem() instanceof Comprehension.ListItem item) {
            comprehension.append(toString(item.value()));
        } else {
            throw new UnsupportedViewingException("Unknown comprehension");
        }
        comprehension.append(' ');
        if (compr instanceof RangeBasedComprehension rangeBased) {
            Range range = rangeBased.getRange();

            comprehension.append(
                    String.format(
                            "for %s in %s",
                            toString(rangeBased.getRangeVariableIdentifier()),
                            rangeFunctionToString(range)
                    )
            );
        } else if (compr instanceof ContainerBasedComprehension containered) {
            comprehension.append(String.format("for %s in %s", toString(containered.getContainerItemDeclaration()), toString(containered.getContainerExpression())));
        }
        if (compr.hasCondition()) {
            comprehension.append(' ');
            comprehension.append(String.format("if %s", toString(compr.getCondition())));
        }
        comprehension.append(endBracket);
        return comprehension.toString();
    }

    private String returnToString(ReturnStatement returnStmt) {
        Expression expression = returnStmt.getExpression();
        return (expression != null) ? "return %s".formatted(toString(expression)) : "return";
    }

    private String identifierToString(Identifier identifier) {
        if (identifier instanceof Alias alias) {
            return String.format("%s as %s", toString(alias.getRealName()), toString(alias.getAlias()));
        } else if (identifier instanceof SelfReference) {
            return "self";
        } else if (identifier instanceof SuperClassReference) {
            return "super()";
        } else if (identifier instanceof SimpleIdentifier ident) {
            return ident.getName();
        } else if (identifier instanceof ScopedIdentifier scopedIdent) {
            return String.join(".", scopedIdent.getScopeResolution().stream().map(this::identifierToString).toList().toArray(new String[0]));
        } else if (identifier instanceof QualifiedIdentifier qualifiedIdent) {
            return String.format("%s.%s", identifierToString(qualifiedIdent.getScope()), identifierToString(qualifiedIdent.getMember()));
        }
        return identifier.toString();
    }

    private String importToString(Import importStmt) {
        return switch (importStmt) {
            case ImportMembersFromModule importMembersFromModule ->
                    String.format(
                            "from %s import %s",
                            toString(importMembersFromModule.getModuleName()),
                            importMembersFromModule
                                    .getMembers()
                                    .stream()
                                    .map(this::toString)
                                    .collect(Collectors.joining(", "))
                    );
            case ImportAllFromModule importAllFromModule ->
                    String.format(
                            "from %s import *",
                            toString(importAllFromModule.getModuleName())
                    );
            case ImportModule importModule ->
                    String.format("import %s", toString(importModule.getModuleName()));
            default -> throw new IllegalStateException("Unexpected import type: " + importStmt);
        };
    }

    private String functionDeclarationToString(FunctionDeclaration decl, Tab tab) {
        if (decl instanceof MethodDeclaration method) {
            return functionToString(new MethodDefinition(method, new CompoundStatement()), tab);
        }
        return functionToString(new FunctionDefinition(decl, new CompoundStatement()), tab);
    }

    private String classToString(ClassDefinition def, Tab tab) {
        StringBuilder builder = new StringBuilder();
        ClassDeclaration decl = (ClassDeclaration) def.getDeclaration();
        if (decl.getParents().isEmpty()) {
            builder.append(String.format("class %s:\n", toString(decl.getName())));
        } else {
            builder.append(String.format("class %s(%s):\n", toString(decl.getName()), String.join(", ", decl.getParents().stream().map(this::typeToString).toList().toArray(new String[0]))));
        }
        builder.append(toString(def.getBody(), tab));
        return builder.toString();
    }

    private String classDeclToString(ClassDeclaration decl, Tab tab) {
        return toString(new ClassDefinition(decl, new CompoundStatement()), tab);
    }

    private String functionToString(Definition func, Tab tab) {
        StringBuilder function = new StringBuilder();
        FunctionDeclaration decl = (FunctionDeclaration) func.getDeclaration();
        for (Annotation anno : decl.getAnnotations()) {
            if (anno.getArguments().length != 0) {
                function.append(String.format("@%s(%s)\n%s", toString(anno.getFunctionExpression()), argumentsToString(Arrays.asList(anno.getArguments())), tab));
            } else {
                function.append(String.format("@%s\n%s", toString(anno.getFunctionExpression()), tab));
            }
        }
        function.append("def ");
        function.append(toString(decl.getName()));
        function.append("(");
        if (decl instanceof MethodDeclaration methodDecl && !methodDecl.getModifiers().contains(DeclarationModifier.STATIC)) {
            function.append("self");
        }
        List<DeclarationArgument> declArgs = decl.getArguments();
        for (int i = 0; i < declArgs.size(); i++) {
            if (function.charAt(function.length() - 1) != '(') {
                function.append(", ");
            }
            DeclarationArgument arg = declArgs.get(i);
            if (arg.isListUnpacking()) {
                function.append('*');
            } else if (arg.isDictUnpacking()) {
                function.append("**");
            }
            function.append(toString(arg.getName()));
            if (!(arg.getType() instanceof UnknownType) && arg.getType() != null
                    && !arg.isListUnpacking()
                    && !arg.isDictUnpacking()) {
                function.append(": ");
                function.append(typeToString(arg.getType()));
            }
            if (!(arg.getElementType() instanceof UnknownType) && (arg.isListUnpacking() || arg.isDictUnpacking())) {
                function.append(": ");
                function.append(typeToString(arg.getElementType()));
            }
        }
        function.append(")");
        if (decl.getReturnType() != null && !(decl.getReturnType() instanceof UnknownType)
                && !(decl instanceof ObjectConstructorDeclaration || decl instanceof ObjectDestructorDeclaration)) {
            function.append(" -> ");
            function.append(typeToString(decl.getReturnType()));
        }
        function.append(":\n");
        if (func instanceof MethodDefinition methodDef) {
            function.append(toString(methodDef.getBody(), tab));
        } else if (func instanceof FunctionDefinition funcDef) {
            function.append(toString(funcDef.getBody(), tab));
        }
        return function.toString();
    }

    private String assignmentToString(MultipleAssignmentStatement stmtSequence) {
        AugmentedAssignmentOperator augOp = ((AssignmentStatement) stmtSequence.getStatements().getFirst()).getAugmentedOperator();
        String operator = switch (augOp) {
            case ADD -> "+=";
            case SUB -> "-=";
            case MUL -> "*=";
            case DIV -> "/=";
            case FLOOR_DIV -> "//=";
            case BITWISE_AND -> "&=";
            case BITWISE_OR -> "|=";
            case BITWISE_XOR -> "^=";
            case BITWISE_SHIFT_LEFT -> "<<=";
            case BITWISE_SHIFT_RIGHT -> ">>=";
            case MOD -> "%=";
            case POW -> "**=";
            default -> "=";
        };
        List<Expression> lvalues = new ArrayList<>();
        List<Expression> rvalues = new ArrayList<>();
        for (Statement stmt : stmtSequence.getStatements()) {
            AssignmentStatement assignment = (AssignmentStatement) stmt;
            lvalues.add(assignment.getLValue());
            rvalues.add(assignment.getRValue());
        }
        StringBuilder builder = new StringBuilder();
        builder.append(lvalues.stream().map(this::toString).collect(Collectors.joining(", ")));
        builder.append(' ');
        builder.append(operator);
        builder.append(' ');
        builder.append(rvalues.stream().map(this::toString).collect(Collectors.joining(", ")));
        return builder.toString();
    }

    private String entryPointToString(ProgramEntryPoint programEntryPoint, Tab tab) {
        IfStatement entryPointIf = null;
        if (getConfigParameter(EnforceEntryPoint.class).orElse(false) && programEntryPoint.hasEntryPoint()) {
            Node entryPointNode = programEntryPoint.getEntryPoint();
            if (entryPointNode instanceof FunctionDefinition func) {
                Identifier ident;
                FunctionDeclaration funcDecl = (FunctionDeclaration) func.getDeclaration();
                if (funcDecl instanceof MethodDeclaration method) {
                    ident = new ScopedIdentifier(method.getOwner().getName(), method.getName());
                } else {
                    ident = func.getName();
                }
                //NOTE: default behaviour - ignore arguments in call main function
                List<Expression> nulls = new ArrayList<>();
                for (DeclarationArgument arg : funcDecl.getArguments()) {
                    if (!arg.isListUnpacking()) {
                        nulls.add(new NullLiteral());
                    }
                }
                FunctionCall funcCall = new FunctionCall(ident, nulls.toArray(new Expression[0]));
                entryPointIf = new IfStatement(new EqOp(new SimpleIdentifier("__name__"), StringLiteral.fromUnescaped("__main__", StringLiteral.Type.NONE)), new CompoundStatement(funcCall),null);
            } else if (entryPointNode instanceof CompoundStatement compound) {
                entryPointIf = new IfStatement(new EqOp(new SimpleIdentifier("__name__"), StringLiteral.fromUnescaped("__main__", StringLiteral.Type.NONE)), compound,null);
            }
        }
        List<Node> nodes = new ArrayList<>(programEntryPoint.getBody());
        if (entryPointIf != null) {
            nodes.add(entryPointIf);
        }
        return nodeListToString(nodes, tab);
    }

    private String loopToString(Statement stmt, Tab tab) {
        StringBuilder builder = new StringBuilder();
        if (stmt instanceof RangeForLoop rangeFor) {
            builder.append(
                    String.format(
                            "for %s in %s:\n",
                            toString(rangeFor.getIdentifier()),
                            rangeFunctionToString(rangeFor.getRange())
                    )
            );
            builder.append(toString(rangeFor.getBody(), tab));
        } else if (stmt instanceof GeneralForLoop generalFor) {
            return toString(tab, PythonSpecialNodeTransformations.representGeneralFor(generalFor));
        } else if (stmt instanceof DoWhileLoop doWhile) {
            return toString(PythonSpecialNodeTransformations.representDoWhile(doWhile));
        } else if (stmt instanceof WhileLoop whileLoop) {
            builder.append(String.format("while %s:\n", toString(whileLoop.getCondition())));
            builder.append(toString(whileLoop.getBody(), tab));
        } else if (stmt instanceof ForEachLoop forEachLoop) {
            List<Expression> identifiers = new ArrayList<>();
            for (VariableDeclarator decl : forEachLoop.getItem().getDeclarators()) {
                identifiers.add(decl.getIdentifier());
            }
            builder.append(String.format("for %s in %s:\n", argumentsToString(identifiers), toString(forEachLoop.getExpression())));
            builder.append(toString(forEachLoop.getBody(), tab));
        } else if (stmt instanceof SwitchStatement switchStmt) {
            tab = tab.up();
            builder.append(String.format("match %s:\n", toString(switchStmt.getTargetExpression())));
            for (CaseBlock caseBranch : switchStmt.getCases()) {
                if (caseBranch == null) {
                    continue;
                }
                switch (caseBranch) {
                    case BasicCaseBlock basicCaseBlock -> {
                        builder.append(
                                String.format(
                                        "%scase %s:\n%s\n",
                                        tab,
                                        toString(basicCaseBlock.getMatchValue()),
                                        toString(basicCaseBlock.getBody(), tab)
                                )
                        );
                    }
                    case FallthroughCaseBlock fallthroughCaseBlock -> {
                        throw new UnsupportedOperationException("Cannot translate fallthrough case branches");
                    }
                    default -> throw new IllegalStateException("Unexpected case block: " + caseBranch.getClass());
                }
            }

            if (switchStmt.hasDefaultCase()) {
                builder.append(
                        String.format(
                                "%scase _:\n%s\n",
                                tab,
                                toString(switchStmt.getDefaultCase().getBody(), tab)
                        )
                );
            }
        } else if (stmt instanceof InfiniteLoop infLoop) {
            builder.append("while True:\n");
            builder.append(toString(infLoop.getBody(), tab));
        }
        return builder.toString();
    }

    private String variableDeclarationToString(VariableDeclaration varDecl) {
        StringBuilder lValues = new StringBuilder();
        StringBuilder rValues = new StringBuilder();
        VariableDeclarator[] decls = varDecl.getDeclarators();

        long rValuesCount = Arrays.stream(decls).filter((VariableDeclarator decl) -> decl.hasInitialization() && decl.getRValue() != null).count();

        for (int i = 0; i < decls.length; i++) {
            lValues.append(toString(decls[i].getIdentifier()));
            // NEED DISCUSSION, see typeToString notes
            // UPDATE: хинты о типах не добавляются в случае, если много переменных,
            // т.к. это синтаксическая ошибка
            if (decls.length == 1 && varDecl.getType() != null && !(varDecl.getType() instanceof UnknownType)) {
                 lValues.append(String.format(": %s", typeToString(varDecl.getType())));
            }
            if (decls[i].hasInitialization() && decls[i].getRValue() != null) {
                rValues.append(toString(decls[i].getRValue()));
            } else if (rValuesCount > 0) {
                rValues.append("None");
            }
            if (i != decls.length - 1) {
                lValues.append(", ");
                if (rValuesCount > 0) {
                    rValues.append(", ");
                }
            }
        }
        if (rValuesCount == 0) {
            return lValues.toString();
        }
        return String.format("%s = %s", lValues, rValues);
    }

    private String typeToString(Type type) {
        //NOTE: python 3.9+ typing support, without using typing library
        if (type instanceof IntType) {
            return "int";
        } else if (type instanceof FloatType) {
            return "float";
        } else if (type instanceof DictionaryType dictType) {
            if (dictType.getKeyType() != null && dictType.getValueType() != null) {
                return String.format("dict[%s, %s]", typeToString(dictType.getKeyType()), typeToString(dictType.getValueType()));
            }
            return "dict";
        } else if (type instanceof StringType) {
            return "str";
        } else if (type instanceof BooleanType) {
            return "bool";
        } else if (type instanceof ListType listType) {
            if (listType.getItemType() != null) {
                return String.format("list[%s]",  typeToString(listType.getItemType()));
            }
            return "list";
        } else if (type instanceof ArrayType listType) {
            if (listType.getItemType() != null) {
                return String.format("list[%s]",  typeToString(listType.getItemType()));
            }
            return "list";
        } else if (type instanceof SetType setType) {
            if (setType.getItemType() != null) {
                return String.format("set[%s]",  typeToString(setType.getItemType()));
            }
            return "set";
        } else if (type instanceof UnmodifiableListType tupleType) {
            if (tupleType.getItemType() != null) {
                return String.format("tuple[%s]",  typeToString(tupleType.getItemType()));
            }
            return "tuple";
        } else if (type instanceof GenericUserType generic) {
            return String.format("%s[%s]", generic.getName().toString(), String.join(", ", Arrays.stream(generic.getTypeParameters()).map(this::typeToString).toList().toArray(new String[0])));
        } else if (type instanceof UserType userType) {
            return userType.getName().toString();
        } else if (type instanceof NoReturn) {
            return "None";
        } else if (type instanceof PointerType ptr) {
            return typeToString(ptr.getTargetType());
        } else if (type instanceof ReferenceType ref) {
            return typeToString(ref.getTargetType());
        }
        return "object";
    }

    private String assignmentExpressionToString(AssignmentExpression expr) {
        expr = (AssignmentExpression) parenFiller.process(expr);
        if (!(expr.getLValue() instanceof SimpleIdentifier) || (expr.getRValue() instanceof AssignmentExpression)) {
            if (getConfigParameter(ExpressionMode.class).orElse(false)) {
                return String.format("%s = %s", toString(expr.getLValue()), toString(expr.getRValue()));
            } else {
                throw new UnsupportedViewingException("Assignment expressions in Python supports only simple identifiers");
            }
        }
        AugmentedAssignmentOperator augOp = expr.getAugmentedOperator();
        String operator = switch (augOp) {
            case ADD -> "+";
            case SUB -> "-";
            case MUL -> "*";
            case DIV -> "/";
            case FLOOR_DIV -> "//";
            case BITWISE_AND -> "&";
            case BITWISE_OR -> "|";
            case BITWISE_XOR -> "^";
            case BITWISE_SHIFT_LEFT -> "<<";
            case BITWISE_SHIFT_RIGHT -> ">>";
            case MOD -> "%";
            case POW -> "**";
            default -> "=";
        };
        String prefix = "";
        if (!operator.equals("=")) {
            prefix = toString(expr.getLValue()) + " " + operator + " ";
        }
        return String.format("%s := %s%s", toString(expr.getLValue()), prefix, toString(expr.getRValue()));
    }

    private String assignmentToString(AssignmentStatement stmt) {
        AugmentedAssignmentOperator augOp = stmt.getAugmentedOperator();
        String operator = switch (augOp) {
            case ADD -> "+=";
            case SUB -> "-=";
            case MUL -> "*=";
            case DIV -> "/=";
            case FLOOR_DIV -> "//=";
            case BITWISE_AND -> "&=";
            case BITWISE_OR -> "|=";
            case BITWISE_XOR -> "^=";
            case BITWISE_SHIFT_LEFT -> "<<=";
            case BITWISE_SHIFT_RIGHT -> ">>=";
            case MOD -> "%=";
            case POW -> "**=";
            default -> "=";
        };
        if (stmt.getRValue() instanceof AssignmentExpression assign) {
            return String.format("%s %s %s", toString(stmt.getLValue()), operator, toString(assign.toStatement()));
        } else {
            return String.format("%s %s %s", toString(stmt.getLValue()), operator, toString(stmt.getRValue()));
        }
    }

    private String literalToString(Literal literal) {
        if (literal instanceof NumericLiteral numLiteral) {
            return numLiteral.getStringValue(false);
        } else if (literal instanceof StringLiteral strLiteral) {
            String prefix;
            switch (strLiteral.getStringType()) {
                case RAW ->  prefix = "r";
                default -> prefix = "";
            }
            String value;
            if (strLiteral.getStringType().equals(StringLiteral.Type.RAW)) {
                value = strLiteral.getUnescapedValue();
            } else {
                value = strLiteral.getEscapedValue();
            }
            return String.format("%s\"%s\"", prefix, value);
        } else if (literal instanceof InterpolatedStringLiteral interpolation) {
            String prefix = "f";
            switch (interpolation.getStringType()) {
                case RAW ->  prefix += "r";
                default -> prefix += "";
            }
            StringBuilder builder = new StringBuilder();
            for (Expression expr : interpolation.components()) {
                if (expr instanceof StringLiteral simpleString) {
                    if (interpolation.getStringType().equals(StringLiteral.Type.RAW)) {
                        builder.append(simpleString.getUnescapedValue());
                    } else {
                        builder.append(simpleString.getEscapedValue());
                    }
                } else {
                    builder.append(String.format("{%s}", toString(expr)));
                }
            }
            return String.format("%s\"%s\"", prefix, builder);
        } else if (literal instanceof BoolLiteral bool) {
           if (bool.getValue()) {
               return "True";
           } else {
               return "False";
           }
        } else if (literal instanceof ListLiteral list) {
            return String.format("[%s]", argumentsToString(list.getList()));
        } else if (literal instanceof ArrayLiteral list) {
            return String.format("[%s]", argumentsToString(list.getList()));
        } else if (literal instanceof SetLiteral set) {
            return String.format("{%s}", argumentsToString(set.getList()));
        } else if (literal instanceof UnmodifiableListLiteral tuple) {
            return String.format("(%s)", argumentsToString(tuple.getList()));
        } else if (literal instanceof DictionaryLiteral dict) {
            Map<Expression, Expression> map = dict.getDictionary();
            StringBuilder builder = new StringBuilder();
            builder.append('{');
            for (Expression key : map.keySet()) {
                builder.append(String.format("%s: %s, ", toString(key), toString(map.get(key))));
            }
            if (!map.isEmpty()) {
                builder.setCharAt(builder.length() - 2, '}');
                builder.setLength(builder.length() - 1);
            } else {
                builder.append("}");
            }
            return builder.toString();
        } else if (literal instanceof CharacterLiteral ch) {
            return "\"" + ch.escapedString() + "\"";
        }
        else {
            return "None";
        }
    }

    private String commentToString(Comment comment) {
        if (comment.isMultiline()) {
            return String.format("\"\"\"%s\"\"\"", comment.getUnescapedContent());
        } else {
            return String.format("# %s", comment.getUnescapedContent());
        }
    }

    private String rangeToString(Range range) {
        Expression start = range.getStart();
        Expression stop = range.getStop();
        Expression step = range.getStep();


        String[] parts = new String[] {"", "", ""};
        if (start != null) {
            parts[0] = toString(start).concat(":");
        }

        if (stop != null) {
            parts[1] = toString(stop);
        }

        if (step != null) {
            parts[2] = ":".concat(toString(step));
        }

        if (parts[0].isEmpty() && !parts[1].isEmpty() && !parts[2].isEmpty()) {
            parts[0] = ":";
        }
        if (parts[0].isEmpty() && parts[1].isEmpty() && !parts[2].isEmpty()) {
            parts[0] = ":";
            parts[1] = ":";
        }

        return String.join("", parts);
    }

    public String rangeFunctionToString(Range range) {
        Expression start = range.getStart();
        Expression stop = range.getStop();
        Expression step = range.getStep();

        boolean isStartDefault = range.getStart() instanceof IntegerLiteral intLit && intLit.getLongValue() == 0;
        boolean isStepDefault = range.getStep() instanceof IntegerLiteral intLit && intLit.getLongValue() == 1;

        if (stop == null) {
            throw new UnsupportedViewingException("Range must contain stop condition at least");
        }

        if ((start == null || isStartDefault) && (step == null || isStepDefault)) {
            return String.format("range(%s)", toString(stop));
        } else if (start != null && (step == null || isStepDefault)) {
            return String.format("range(%s, %s)", toString(start), toString(stop));
        }

        if (start == null || isStartDefault) {
            start = new IntegerLiteral(0);
        }

        return String.format("range(%s, %s, %s)", toString(start), toString(stop), toString(step));
    }

    private String binaryOpToString(BinaryExpression node) {
        node = parenFiller.process(node);
        String pattern = "";
        Expression left = node.getLeft();
        Expression right = node.getRight();
        String token = mapToToken(node).value;

        if (node instanceof ShortCircuitAndOp) {
            try {
                Node result = PythonSpecialNodeTransformations.detectCompoundComparison(node);
                if (result instanceof CompoundComparison
                        && !getConfigParameter(DisableCompoundComparisonConversion.class).orElse(false)) {
                    return compoundComparisonToString((CompoundComparison) result);
                } else {
                    return preferExplicitAndOpToString(result);
                }
            } catch (MeaningTreeException e) {
                return preferExplicitAndOpToString(node);
            }
        } else if (node instanceof InstanceOfOp) {
            return String.format("isinstance(%s, %s)", toString(node.getLeft()), toString(node.getRight()));
        } else {
            if (token.equals("%")) token = "%%";
            pattern = "%s " + token + " %s";
        }
        return String.format(pattern, toString(left), toString(right));
    }

    private String preferExplicitAndOpToString(Node node) {
        if (node instanceof ShortCircuitAndOp op) {
            return String.format("%s and %s", preferExplicitAndOpToString(op.getLeft()), preferExplicitAndOpToString(op.getRight()));
        } else if (node instanceof CompoundComparison op && getConfigParameter(DisableCompoundComparisonConversion.class).orElse(false)) {
           return preferExplicitAndOpToString(BinaryExpression.fromManyOperands
                   (op.getComparisons().toArray(new BinaryComparison[0]), 0, ShortCircuitAndOp.class));
        } else {
            return toString(node);
        }
    }

    private String callsToString(Node node) {
        switch (node) {
            case ArrayNewExpression newExpr -> {
                if (newExpr.getInitializer() != null) {
                    return arrayInitializerToString(newExpr.getInitializer());
                } else {
                    Shape shape = newExpr.getShape();
                    String result = _getListComprehensionByDimension(1,
                            shape.getDimension(shape.getDimensionCount() - 1), String.format("%s()", toString(newExpr.getType())));
                    for (int i = shape.getDimensionCount() - 2; i >= 0; i--) {
                        result = _getListComprehensionByDimension(shape.getDimensionCount() - i, shape.getDimension(i), result);
                    }
                    return result;
                }
            }
            case ObjectNewExpression newExpr -> {
                FunctionCall call = new FunctionCall(newExpr.getType(), newExpr.getConstructorArguments());
                return callsToString(call);
            }
            case MethodCall funcCall -> {
                MemberAccess memAcc = parenFiller.process(new MemberAccess(funcCall.getObject(),
                        (SimpleIdentifier) funcCall.getFunctionName()));
                return String.format("%s(%s)", toString(memAcc), argumentsToString(funcCall.getArguments()));
            }
            case FunctionCall funcCall -> {
                funcCall = parenFiller.processForPython(funcCall);
                return String.format("%s(%s)", toString(PythonSpecificFeatures.getFunctionExpression(funcCall)), argumentsToString(funcCall.getArguments()));
            }
            case CastTypeExpression cast -> {
                FunctionCall call = new FunctionCall(cast.getCastType(), cast.getValue());
                return callsToString(call);
            }
            case null, default -> throw new UnsupportedViewingException("Not a callable object");
        }
    }

    private String arrayInitializerToString(ArrayInitializer initializer) {
        return String.format("[%s]", argumentsToString(initializer.getValues()));
    }

    private String _getListComprehensionByDimension(int depth, Expression dimension, String fillExpression) {
        return String.format("[%s for %s in range(%s)]", fillExpression, "_".repeat(depth), dimension);
    }

    private String argumentsToString(List<Expression> expressions) {
        String[] exprStrings = new String[expressions.size()];
        for (int i = 0; i < exprStrings.length; i++) {
            exprStrings[i] = toString(expressions.get(i));
        }
        return String.join(", ", exprStrings);
    }

    private String conditionToString(IfStatement node, Tab tab) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.getBranches().size(); i++) {
            ConditionBranch branch = node.getBranches().get(i);
            if (i == 0) {
                if (!(branch.getBody() instanceof CompoundStatement))
                    sb.append(String.format("if %s:\n%s\n", toString(branch.getCondition()), toString(branch.getBody(), tab.up())));
                else
                    sb.append(String.format("if %s:\n%s\n", toString(branch.getCondition()), toString(branch.getBody(), tab)));
            } else {
                if (!(branch.getBody() instanceof CompoundStatement))
                    sb.append(String.format("%selif %s:\n%s\n", tab, toString(branch.getCondition()), toString(branch.getBody(), tab.up())));
                else
                    sb.append(String.format("%selif %s:\n%s\n", tab, toString(branch.getCondition()), toString(branch.getBody(), tab)));
            }
        }
        if (node.hasElseBranch()) {
            if (!(node.getElseBranch() instanceof CompoundStatement))
                sb.append(String.format("%selse:\n%s\n", tab, toString(node.getElseBranch(), tab.up())));
            else
                sb.append(String.format("%selse:\n%s\n", tab, toString(node.getElseBranch(), tab)));
        }
        return sb.toString().stripTrailing();
    }

    private String unaryToString(UnaryExpression node) {
        node = parenFiller.process(node);
        String pattern = "";
        Expression expr = node.getArgument();

        boolean expressionMode = getConfigParameter(ExpressionMode.class).orElse(false);

        if (node instanceof UnaryPlusOp) {
            pattern = "+%s";
        } else if (node instanceof UnaryMinusOp) {
            pattern = "-%s";
        } else if (node instanceof NotOp) {
            if (expr instanceof ParenthesizedExpression p && p.getExpression() instanceof InstanceOfOp op) {
                expr = op;
            }
            pattern = "not %s";
        } else if (node instanceof InversionOp) {
            pattern = "~%s";
        } else if (node instanceof PostfixDecrementOp || node instanceof PrefixDecrementOp) {
            boolean exprRepr = origin == null && expressionMode;
            Node parent = origin == null ? null : origin.findParentOfNode(node);
            if (exprRepr || parent instanceof Expression) {
                return String.format("%s := %s + 1", toString(expr), toString(expr));
            } else {
                pattern = "%s -= 1";
            }
        } else if (node instanceof PostfixIncrementOp || node instanceof PrefixIncrementOp) {
            boolean exprRepr = origin == null && expressionMode;
            Node parent = origin == null ? null : origin.findParentOfNode(node);
            if (exprRepr || parent instanceof Expression) {
                return String.format("%s := %s + 1", toString(expr), toString(expr));
            } else {
                pattern = "%s += 1";
            }
        } else if (node instanceof PointerPackOp || node instanceof PointerUnpackOp) {
            return toString(node);
        }
        return String.format(pattern, toString(expr));
    }

    private String blockToString(CompoundStatement node, Tab tab) {
        StringBuilder builder = new StringBuilder();
        tab = tab.up();
        if (node.getNodes().length == 0) {
            return tab.toString().concat("pass");
        }
        for (Node child : node.getNodes()) {
            builder.append(tab);
            if (child instanceof CompoundStatement) {
                // Схлопываем лишний таб, так как блоки как самостоятельная сущность в Python не поддерживаются
                builder.append(toString(child, tab.down().down()));
            } else {
                builder.append(toString(child, tab));
            }
            builder.append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private String nodeListToString(List<Node> nodes, Tab tab) {
        StringBuilder builder = new StringBuilder();
        if (nodes.isEmpty()) {
            return "pass";
        }
        for (Node child : nodes) {
            builder.append(tab);
            if (child instanceof CompoundStatement) {
                // Схлопываем лишний таб, так как блоки как самостоятельная сущность в Python не поддерживаются
                builder.append(toString(child, tab.down().down()));
            } else {
                builder.append(toString(child, tab));
            }
            builder.append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private String comparisonToString(BinaryComparison node) {
        node = (BinaryComparison) parenFiller.process(node);
        String pattern = "";
        Expression left = node.getLeft();
        Expression right = node.getRight();
        String token = mapToToken(node).value;
        if (node instanceof ReferenceEqOp eq) {
            if (eq.isNegative()) {
                pattern = "%s is not %s";
            } else {
                pattern = "%s is %s";
            }
        } else if (node instanceof ContainsOp cnt) {
            if (cnt.isNegative()) {
                pattern = "%s not in %s";
            } else {
                pattern = "%s in %s";
            }
        } else {
            if (token.equals("%")) token = "%%";
            pattern = "%s " + token + " %s";
        }
        return String.format(pattern, toString(left), toString(right));
    }

    private String compoundComparisonToString(CompoundComparison node) {
        StringBuilder sb = new StringBuilder();
        sb.append(toString(node.getComparisons().getFirst().getLeft()));
        for (BinaryComparison cmp : node.getComparisons()) {
            if (cmp instanceof EqOp) {
                sb.append(" == ");
            } else if (cmp instanceof NotEqOp) {
                sb.append(" != ");
            } else if (cmp instanceof GeOp) {
                sb.append(" >= ");
            } else if (cmp instanceof LeOp) {
                sb.append(" <= ");
            } else if (cmp instanceof GtOp) {
                sb.append(" > ");
            } else if (cmp instanceof LtOp) {
                sb.append(" < ");
            }
            sb.append(toString(cmp.getRight()));
        }
        return sb.toString();
    }

    public OperatorToken mapToToken(Expression expr) {
        String tok = switch (expr) {
            case AddOp op -> "+";
            case SubOp op -> "-";
            case MulOp op -> "*";
            case DivOp op -> "/";
            case ModOp op -> "%";
            case PowOp op -> "**";
            case MatMulOp op -> "@";
            case ContainsOp op -> {
                if (op.isNegative()) {
                    yield "not in";
                }
                yield "in";
            }
            case FloorDivOp op -> "//";
            case ReferenceEqOp op -> {
                if (op.isNegative()) {
                    yield "is not";
                }
                yield "is";
            }
            case EqOp op -> {
                if (op.getRight() instanceof NullLiteral) {
                    yield "is";
                }
                yield "==";
            }
            case NotEqOp op -> {
                if (op.getRight() instanceof NullLiteral) {
                    yield "is not";
                }
                yield "!=";
            }
            case GeOp op -> ">=";
            case LeOp op -> "<=";
            case LtOp op -> "<";
            case GtOp op -> ">";
            case ScopedIdentifier op -> ".";
            case InstanceOfOp op -> "CALL_(";
            case ShortCircuitAndOp op -> "and";
            case ShortCircuitOrOp op -> "or";
            case BitwiseAndOp op -> "&";
            case BitwiseOrOp op -> "|";
            case LeftShiftOp op -> "<<";
            case RightShiftOp op -> ">>";
            case FunctionCall op -> "CALL_(";
            case TernaryOperator op -> "if";
            case NewExpression op -> "new";
            case MemberAccess op -> ".";
            case XorOp op -> "^";
            case IndexExpression op -> "[";
            case ThreeWayComparisonOp op -> "<=>";
            case AssignmentExpression as -> {
                AugmentedAssignmentOperator op = as.getAugmentedOperator();
                yield switch (op) {
                    case NONE -> ":=";
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
            // unary section
            case NotOp op -> "not";
            case InversionOp op -> "~";
            case UnaryMinusOp op -> "UMINUS";
            case UnaryPlusOp op -> "UPLUS";
            case PostfixIncrementOp op -> "+=";
            case PrefixIncrementOp op -> "+=";
            case PostfixDecrementOp op -> "-=";
            case PrefixDecrementOp op -> "-=";
            default -> null;
        };
        return tokenizer.getOperatorByTokenName(tok);
    }
}