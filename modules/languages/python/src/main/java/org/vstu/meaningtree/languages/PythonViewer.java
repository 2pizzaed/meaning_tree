package org.vstu.meaningtree.languages;

import org.vstu.meaningtree.exceptions.MeaningTreeException;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.helpers.ContextualNodeRenderer;
import org.vstu.meaningtree.languages.support.features.*;
import org.vstu.meaningtree.languages.utils.PythonSpecificFeatures;
import org.vstu.meaningtree.languages.utils.Tab;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.*;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.*;
import org.vstu.meaningtree.nodes.expressions.bitwise.InversionOp;
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
import org.vstu.meaningtree.nodes.expressions.math.SubOp;
import org.vstu.meaningtree.nodes.expressions.newexpr.ArrayNewExpression;
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
import org.vstu.meaningtree.nodes.statements.assignments.ChainedAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.ListUnpackingAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.BasicCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.conditions.components.FallthroughCaseBlock;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.*;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class PythonViewer extends LanguageViewer {
    public PythonViewer(LanguageTranslator translator) {
        super(translator);
        configureSupportAndRenderers();
    }

    private void configureSupportAndRenderers() {
        registerTabRenderer(ProgramEntryPoint.class, this::entryPointToString);
        registerTabRenderer(AssignmentExpression.class, (node, tab) -> assignmentExpressionToString(node));
        registerTabRenderer(BinaryComparison.class, (node, tab) -> comparisonToString(node));
        registerTabRenderer(BinaryExpression.class, (node, tab) -> binaryOpToString(node));
        registerTabRenderer(IfStatement.class, this::conditionToString);
        registerTabRenderer(PointerPackOp.class, (node, tab) -> pointerPackToString(node));
        registerTabRenderer(PointerUnpackOp.class, (node, tab) -> pointerUnpackToString(node));
        registerTabRenderer(UnaryExpression.class, (node, tab) -> unaryToString(node));
        registerTabRenderer(CompoundStatement.class, this::blockToString);
        registerTabRenderer(CompoundComparison.class, (node, tab) -> compoundComparisonToString(node));
        registerTabRenderer(Type.class, (node, tab) -> typeToString(node));
        registerTabRenderer(FormatPrint.class, (node, tab) -> callsToString(node));
        registerTabRenderer(FormatInput.class, (node, tab) -> callsToString(node));
        registerTabRenderer(Identifier.class, (node, tab) -> identifierToString(node));
        registerTabRenderer(IndexExpression.class, (indexExpr, tab) ->
                String.format("%s[%s]", toString(indexExpr.getExpression()), toString(indexExpr.getIndex())));
        registerTabRenderer(MemberAccess.class, (memAccess, tab) ->
                String.format("%s.%s", toString(memAccess.getExpression()), toString(memAccess.getMember())));
        registerTabRenderer(TernaryOperator.class, (ternary, tab) ->
                String.format("%s if %s else %s", toString(ternary.getThenExpr()), toString(ternary.getCondition()), toString(ternary.getElseExpr())));
        registerTabRenderer(ParenthesizedExpression.class, (paren, tab) -> String.format("(%s)", toString(paren.getExpression())));
        registerTabRenderer(ObjectNewExpression.class, (node, tab) -> callsToString(node));
        registerTabRenderer(ArrayNewExpression.class, (node, tab) -> callsToString(node));
        registerTabRenderer(MemoryAllocationCall.class, (node, tab) -> toString(node.toNew()));
        registerTabRenderer(MemoryFreeCall.class, (node, tab) -> toString(node.toDelete()));
        registerTabRenderer(FunctionCall.class, (node, tab) -> callsToString(node));
        registerTabRenderer(BreakStatement.class, (node, tab) -> "break");
        registerTabRenderer(DeleteStatement.class, (node, tab) -> String.format("del %s", toString(node.getTarget())));
        registerTabRenderer(DeleteExpression.class, (node, tab) -> toString(node.toStatement()));
        registerTabRenderer(Range.class, (node, tab) -> rangeToString(node));
        registerTabRenderer(ContinueStatement.class, (node, tab) -> "continue");
        registerTabRenderer(ConstructorCall.class, (node, tab) -> String.format("super().__init__(%s)", argumentsToString(node.getArguments())));
        registerTabRenderer(Comment.class, (node, tab) -> commentToString(node));
        registerTabRenderer(Literal.class, (node, tab) -> literalToString(node));
        registerTabRenderer(SizeofExpression.class, (node, tab) -> callsToString(node));
        registerTabRenderer(ListUnpackingVariableDeclaration.class, (node, tab) -> listUnpackingVariableToString(node));
        registerTabRenderer(ListUnpackingAssignmentStatement.class, (node, tab) -> listUnpackingAssignmentToString(node));
        registerTabRenderer(AssignmentStatement.class, (node, tab) -> assignmentToString(node));
        registerTabRenderer(VariableDeclaration.class, (node, tab) -> variableDeclarationToString(node));
        registerTabRenderer(ForLoop.class, this::loopToString);
        registerTabRenderer(InfiniteLoop.class, this::loopToString);
        registerTabRenderer(WhileLoop.class, this::loopToString);
        registerTabRenderer(DoWhileLoop.class, this::loopToString);
        registerTabRenderer(SwitchStatement.class, this::loopToString);
        registerTabRenderer(MethodDefinition.class, (node, tab) -> functionToString(node, tab));
        registerTabRenderer(FunctionDefinition.class, (node, tab) -> functionToString(node, tab));
        registerTabRenderer(ObjectConstructorDefinition.class, this::objectConstructorToString);
        registerTabRenderer(ObjectDestructorDefinition.class, this::objectDestructorToString);
        registerTabRenderer(ClassDeclaration.class, this::classDeclToString);
        registerTabRenderer(ClassDefinition.class, this::classToString);
        registerTabRenderer(EnumDeclaration.class, this::enumToString);
        registerTabRenderer(StructureDeclaration.class, this::structDeclToString);
        registerTabRenderer(StructureDefinition.class, this::structToString);
        registerTabRenderer(FunctionDeclaration.class, this::functionDeclarationToString);
        registerTabRenderer(Import.class, (node, tab) -> importToString(node));
        registerTabRenderer(ExpressionStatement.class, (node, tab) -> toString(node));
        registerTabRenderer(ReturnStatement.class, (node, tab) -> returnToString(node));
        registerTabRenderer(ArrayInitializer.class, (node, tab) -> arrayInitializerToString(node));
        registerTabRenderer(DefinitionArgument.class, (node, tab) -> definitionArgumentToString(node));
        registerTabRenderer(PackageDeclaration.class, (node, tab) -> String.format("import %s", toString(node.getPackageName())));
        registerTabRenderer(CommaExpression.class, (node, tab) -> String.join(", ", node.getExpressions().stream().map(this::toString).toList().toArray(new String[0])));
        registerTabRenderer(ExpressionSequence.class, (node, tab) -> String.join(", ", node.getExpressions().stream().map(this::toString).toList().toArray(new String[0])));
        registerTabRenderer(MultipleAssignmentStatement.class, (node, tab) -> assignmentToString(node));
        registerTabRenderer(ChainedAssignmentStatement.class, (node, tab) -> chainedAssignmentToString(node));
        registerTabRenderer(CastTypeExpression.class, (node, tab) -> callsToString(node));
        registerTabRenderer(Comprehension.class, (node, tab) -> comprehensionToString(node));
        registerTabRenderer(EmptyStatement.class, (node, tab) -> emptyStatementToString(node));

        registerPreRenderPreparation(UnaryExpression.class, parenFiller::process);
        registerPreRenderPreparation(BinaryExpression.class, parenFiller::process);
        registerPreRenderPreparation(IndexExpression.class, parenFiller::process);
        registerPreRenderPreparation(TernaryOperator.class, parenFiller::process);
        registerPreRenderPreparation(CastTypeExpression.class, parenFiller::process);
        registerPreRenderPreparation(QualifiedIdentifier.class, parenFiller::process);
        registerPreRenderPreparation(MemberAccess.class, parenFiller::process);
        registerPreRenderPreparation(AssignmentExpression.class, node -> (AssignmentExpression) parenFiller.process(node));

        registerUnsupportedFeature(new PointerSubtractionInUnpackFeature());
        registerUnsupportedFeature(new PointerToMemberOperatorFeature());
        registerUnsupportedFeature(new LabeledLoopFeature());
        registerUnsupportedFeature(new StatementJumpFeature());
        registerUnsupportedFeature(new ConstructorDelegationFeature());
        registerUnsupportedFeature(new PointerTypeFeature());
        registerUnsupportedFeature(new ConstInFunctionSignatureFeature());
        registerUnsupportedFeature(FallthroughCaseBlock.class);
    }

    private <T extends Node> void registerTabRenderer(Class<T> nodeType, ContextualNodeRenderer<T, Tab> renderer) {
        registerRenderer(nodeType, (T node, Tab tab) -> renderer.render(node, tab == null ? new Tab() : tab));
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

    /**
     * Второй путь диспетчеризации — с отступом. Идёт через {@link #renderPrepared}, иначе
     * узлы, отрендеренные этим путём (в Python — почти всё дерево), не попадут в стек кадров
     * и {@code ctx.isInNode(...)} начнёт врать.
     */
    public String toString(Node node, Tab tab) {
        Node preparedNode = applyPreRenderPreparations(node);
        return renderPrepared(preparedNode, tab);
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

    public String pointerPackToString(PointerPackOp ptr) {
        return toString(ptr.getArgument());
    }

    public String pointerUnpackToString(PointerUnpackOp ptr) {
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
            case Include incl -> String.format("import %s", incl.getFileName().getUnescapedValue());
            default -> throw new IllegalStateException("Unexpected import type: " + importStmt);
        };
    }

    private String functionDeclarationToString(FunctionDeclaration decl, Tab tab) {
        // Обёртка живёт только на время отрисовки, поэтому размечается самим объявлением
        if (decl instanceof MethodDeclaration method) {
            return toString(new MethodDefinition(method, new CompoundStatement().remap(decl)).remap(decl), tab);
        }
        return toString(new FunctionDefinition(decl, new CompoundStatement().remap(decl)).remap(decl), tab);
    }

    private String listUnpackingVariableToString(ListUnpackingVariableDeclaration decl) {
        return "%s = %s".formatted(String.join(", ",
                decl.getVariableNames().stream().map(this::toString).toArray(String[]::new)),
                toString(decl.getValue())
        );
    }

    private String listUnpackingAssignmentToString(ListUnpackingAssignmentStatement decl) {
        return "%s = %s".formatted(String.join(", ",
                        decl.getVariableNames().stream().map(this::toString).toArray(String[]::new)),
                toString(decl.getValue())
        );
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
        return toString(new ClassDefinition(decl, new CompoundStatement().remap(decl)).remap(decl), tab);
    }

    /**
     * Перечисление транслируется в класс, унаследованный от {@code enum.Enum}. Константам без
     * явного значения выдается {@code auto()}: пустое присваивание в Python недопустимо.
     * Нужные имена откладываются в контекст импортов — их допишет точка входа программы.
     */
    private String enumToString(EnumDeclaration decl, Tab tab) {
        Tab constantTab = tab.up();
        List<String> constants = new ArrayList<>();
        boolean needsAuto = false;
        for (Identifier constant : decl.getConstants()) {
            Expression value = decl.getConstant(constant);
            needsAuto |= value == null;
            constants.add(constantTab.concat("%s = %s".formatted(
                    toString(constant),
                    value == null ? "auto()" : toString(value)
            )));
        }
        if (constants.isEmpty()) {
            constants.add(constantTab.concat("pass"));
        }

        List<Identifier> importedMembers = new ArrayList<>();
        importedMembers.add(new SimpleIdentifier("Enum").remap(decl));
        if (needsAuto) {
            importedMembers.add(new SimpleIdentifier("auto").remap(decl));
        }
        ctx.preserveImport(new ImportMembersFromModule(
                new SimpleIdentifier("enum").remap(decl),
                importedMembers
        ).remap(decl));

        return "class %s(Enum):\n%s".formatted(toString(decl.getName()), String.join("\n", constants));
    }

    /**
     * Структура транслируется в класс, помеченный декоратором dataclass. Сам декоратор без
     * {@code from dataclasses import dataclass} нерабочий, поэтому импорт откладывается в
     * контекст: точка входа допишет его в шапку программы, если его там еще нет.
     */
    private String structToString(StructureDefinition def, Tab tab) {
        ctx.preserveImport(new ImportMembersFromModule(
                new SimpleIdentifier("dataclasses").remap(def),
                new SimpleIdentifier("dataclass").remap(def)
        ).remap(def));
        return String.format("@dataclass\n%s%s", tab, classToString(def, tab));
    }

    private String structDeclToString(StructureDeclaration decl, Tab tab) {
        return toString(new StructureDefinition(decl, new CompoundStatement().remap(decl)).remap(decl), tab);
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
        if (decl.getModifiers().contains(DeclarationModifier.STATIC)
                && !decl.getAnnotations().stream().anyMatch(an ->
                an.getName().equalsIdentifier("classmethod") || an.getName().equalsIdentifier("staticmethod"))
        ) {
            function.append(String.format("@staticmethod\n%s", tab));
        }
        function.append("def ");
        function.append(toString(decl.getName()));
        function.append("(");
        if (decl instanceof MethodDeclaration methodDecl
                && !methodDecl.getModifiers().contains(DeclarationModifier.STATIC)
                && ctx.isInNode(ClassDefinition.class)
        ) {
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
                function.append(toString(arg.getType()));
            }
            if (!(arg.getElementType() instanceof UnknownType) && (arg.isListUnpacking() || arg.isDictUnpacking())) {
                function.append(": ");
                function.append(toString(arg.getElementType()));
            }
        }
        function.append(")");
        if (decl.getReturnType() != null && !(decl.getReturnType() instanceof UnknownType)
                && !(decl instanceof ObjectConstructorDeclaration || decl instanceof ObjectDestructorDeclaration)) {
            function.append(" -> ");
            function.append(toString(decl.getReturnType()));
        }
        function.append(":\n");
        if (func instanceof MethodDefinition methodDef) {
            function.append(toString(methodDef.getBody(), tab));
            function.append("\n");
        } else if (func instanceof FunctionDefinition funcDef) {
            function.append(toString(funcDef.getBody(), tab));
            function.append("\n\n");
        }
        return function.toString();
    }

    private String objectDestructorToString(ObjectDestructorDefinition destructor, Tab tab) {
        MethodDeclaration declaration = destructor.getDeclaration();
        ObjectDestructorDefinition pythonDestructor = new ObjectDestructorDefinition(
                declaration.getOwner(),
                new SimpleIdentifier("__del__").remap(declaration.getName()),
                declaration.getAnnotations(),
                declaration.getModifiers(),
                destructor.getBody()
        ).remap(destructor);
        return functionToString(pythonDestructor, tab);
    }

    private String objectConstructorToString(ObjectConstructorDefinition constructor, Tab tab) {
        MethodDeclaration declaration = constructor.getDeclaration();
        ObjectConstructorDefinition pythonConstructor = new ObjectConstructorDefinition(
                declaration.getOwner(),
                new SimpleIdentifier("__init__").remap(declaration.getName()),
                declaration.getAnnotations(),
                declaration.getModifiers(),
                declaration.getArguments(),
                constructor.getBody()
        ).remap(constructor);
        return functionToString(pythonConstructor, tab);
    }

    private String assignmentToString(MultipleAssignmentStatement stmtSequence) {
        AugmentedAssignmentOperator augOp = stmtSequence.getStatements().getFirst().getAugmentedOperator();
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

    private String chainedAssignmentToString(ChainedAssignmentStatement statement) {
        String targets = statement.getTargets().stream().map(this::toString).collect(Collectors.joining(" = "));
        return targets + " = " + toString(statement.getValue());
    }

    private String entryPointToString(ProgramEntryPoint programEntryPoint, Tab tab) {
        IfStatement entryPointIf = null;
        boolean omitEntryPointInNonFullMode = false;
        FunctionDefinition entryPointFunction = null;
        if (programEntryPoint.hasEntryPoint()) {
            Node entryPointNode = programEntryPoint.getEntryPoint();
            if (entryPointNode instanceof FunctionDefinition func) {
                entryPointFunction = func;
                if (!getConfigParameter("translationUnitMode").equalsValue("full")) {
                    omitEntryPointInNonFullMode = true;
                } else {
                    Identifier ident;
                    FunctionDeclaration funcDecl = func.getDeclaration();
                    if (funcDecl instanceof MethodDeclaration method) {
                        ident = new ScopedIdentifier(method.getOwner().getName(), method.getName())
                                .remap(method.getName());
                    } else {
                        ident = func.getName();
                    }
                    //NOTE: default behaviour - ignore arguments in call main function
                    List<Expression> nulls = new ArrayList<>();
                    for (DeclarationArgument arg : funcDecl.getArguments()) {
                        if (!arg.isListUnpacking()) {
                            nulls.add(new NullLiteral().remap(arg));
                        }
                    }
                    FunctionCall funcCall = new FunctionCall(ident, nulls.toArray(new Expression[0])).remap(func);
                    entryPointIf = makeEntryPointCondition(new CompoundStatement(funcCall).remap(func), func);
                }
            } else if (entryPointNode instanceof CompoundStatement compound) {
                entryPointIf = makeEntryPointCondition(compound, compound);
            }
        }
        List<Node> nodes = new ArrayList<>(programEntryPoint.getBody());
        if (omitEntryPointInNonFullMode && entryPointFunction != null && canFlattenEntryPointFunction(entryPointFunction)) {
            long entryPointId = entryPointFunction.getId();
            boolean entryPointWasInBody = nodes.removeIf(node -> node.getId() == entryPointId);
            if (entryPointWasInBody) {
                nodes.addAll(entryPointFunction.getBody().getNodeList());
            }
        }
        if (!getConfigParameter("translationUnitMode").equalsValue("full") && entryPointIf != null && !omitEntryPointInNonFullMode) {
            Statement body = entryPointIf.getBranches().getFirst().getBody();
            if (body instanceof CompoundStatement compoundStatement) {
                nodes.addAll(compoundStatement.getNodeList());
            } else {
                nodes.add(body);
            }
        } else if (getConfigParameter("translationUnitMode").equalsValue("full") && entryPointIf != null) {
            nodes.add(entryPointIf);
        }
        String body = nodeListToString(nodes, tab);
        return ctx.prependPreservedImports(body, nodes, tab.toString(), imp -> toString(imp, tab));
    }

    /**
     * Собирает обёртку {@code if __name__ == "__main__":}. Ни одного из этих узлов нет во
     * входном дереве, поэтому все они размечаются узлом точки входа: иначе в source map
     * появятся id, которых у потребителя нет.
     */
    private IfStatement makeEntryPointCondition(Statement body, Node entryPointNode) {
        EqOp condition = new EqOp(
                new SimpleIdentifier("__name__").remap(entryPointNode),
                StringLiteral.fromUnescaped("__main__", StringLiteral.Type.NONE).remap(entryPointNode)
        ).remap(entryPointNode);
        return new IfStatement(condition, body, null).remap(entryPointNode);
    }

    private boolean canFlattenEntryPointFunction(FunctionDefinition functionDefinition) {
        for (var nodeInfo : functionDefinition.getBody()) {
            if (nodeInfo.node() instanceof ReturnStatement) {
                return false;
            }
        }
        return true;
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
            builder.append(branchStmtToString(rangeFor.getBody(), tab));
            builder.append(loopElseToString(rangeFor, tab));
        } else if (stmt instanceof GeneralForLoop generalFor) {
            return toString(tab, PythonSpecialNodeTransformations.representGeneralFor(generalFor));
        } else if (stmt instanceof DoWhileLoop doWhile) {
            return toString(PythonSpecialNodeTransformations.representDoWhile(doWhile));
        } else if (stmt instanceof WhileLoop whileLoop) {
            builder.append(String.format("while %s:\n", toString(whileLoop.getCondition())));
            builder.append(branchStmtToString(whileLoop.getBody(), tab));
            builder.append(loopElseToString(whileLoop, tab));
        } else if (stmt instanceof ForEachLoop forEachLoop) {
            List<Expression> identifiers = new ArrayList<>();
            for (VariableDeclarator decl : forEachLoop.getItem().getDeclarators()) {
                identifiers.add(decl.getIdentifier());
            }
            builder.append(String.format("for %s in %s:\n", argumentsToString(identifiers), toString(forEachLoop.getExpression())));
            builder.append(branchStmtToString(forEachLoop.getBody(), tab));
            builder.append(loopElseToString(forEachLoop, tab));
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
                                        branchStmtToString(basicCaseBlock.getBody(), tab)
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
            builder.append(branchStmtToString(infLoop.getBody(), tab));
            builder.append(loopElseToString(infLoop, tab));
        }
        return builder.toString();
    }

    private String loopElseToString(Loop loop, Tab tab) {
        if (!loop.hasElseBranch()) {
            return "";
        }
        if (!(loop.getElseBranch() instanceof CompoundStatement)) {
            return String.format("\n%selse:\n%s\n", tab, branchStmtToString(loop.getElseBranch(), tab.up()));
        }
        return String.format("\n%selse:\n%s\n", tab, branchStmtToString(loop.getElseBranch(), tab));
    }

    private String variableDeclarationToString(VariableDeclaration varDecl) {
        StringBuilder lValues = new StringBuilder();
        StringBuilder rValues = new StringBuilder();
        VariableDeclarator[] decls = varDecl.getDeclarators();

        long rValuesCount = Arrays.stream(decls)
                .filter(decl -> hasDeclarationInitialValue(decl, varDecl.getType()))
                .count();

        for (int i = 0; i < decls.length; i++) {
            lValues.append(toString(decls[i].getIdentifier()));
            // NEED DISCUSSION, see typeToString notes
            // UPDATE: хинты о типах не добавляются в случае, если много переменных,
            // т.к. это синтаксическая ошибка
            if (decls.length == 1 && varDecl.getType() != null && !(varDecl.getType() instanceof UnknownType)
                    && !getConfigParameter("disableTypeAnnotations").asBoolean()) {
                 lValues.append(String.format(": %s", toString(varDecl.getType())));
            }
            String initialValue = declarationInitialValueToString(decls[i], varDecl.getType());
            if (initialValue != null) {
                rValues.append(initialValue);
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

    private boolean hasDeclarationInitialValue(VariableDeclarator declarator, Type declaredType) {
        return (declarator.hasInitialization() && declarator.getRValue() != null)
                || (declaredType instanceof ArrayType arrayType
                && arrayType.getShape().getDimensions().stream().allMatch(dimension -> dimension != null));
    }

    private String declarationInitialValueToString(VariableDeclarator declarator, Type declaredType) {
        if (declarator.hasInitialization() && declarator.getRValue() != null) {
            return toString(declarator.getRValue());
        }
        if (declaredType instanceof ArrayType arrayType
                && arrayType.getShape().getDimensions().stream().allMatch(dimension -> dimension != null)) {
            return arrayAllocationToString(arrayType.getItemType(), arrayType.getShape());
        }
        return null;
    }

    private String typeToString(Type type) {
        //NOTE: python 3.9+ typing support, without using typing library
        String typeStr = "object";
        if (type instanceof IntType) {
            typeStr = "int";
        } else if (type instanceof FloatType) {
            typeStr = "float";
        } else if (type instanceof DictionaryType dictType) {
            if (dictType.getKeyType() != null && dictType.getValueType() != null) {
                typeStr = String.format("dict[%s, %s]", toString(dictType.getKeyType()), toString(dictType.getValueType()));
            } else {
                typeStr = "dict";
            }
        } else if (type instanceof StringType) {
            typeStr = "str";
        } else if (type instanceof BooleanType) {
            typeStr = "bool";
        } else if (type instanceof ListType listType) {
            if (listType.getItemType() != null) {
                typeStr = String.format("list[%s]",  toString(listType.getItemType()));
            } else {
                typeStr = "list";
            }
        } else if (type instanceof ArrayType arrayType) {
            if (arrayType.getItemType() == null) {
                typeStr = "list";
            } else {
                typeStr = typeToString(arrayType.getItemType());
                for (int i = 0; i < arrayType.getDimensionsCount(); i++) {
                    typeStr = "list[%s]".formatted(typeStr);
                }
            }
        } else if (type instanceof SetType setType) {
            if (setType.getItemType() != null) {
                typeStr = String.format("set[%s]",  toString(setType.getItemType()));
            } else {
                typeStr = "set";
            }
        } else if (type instanceof UnmodifiableListType tupleType) {
            if (tupleType.getItemType() != null) {
                typeStr = String.format("tuple[%s]", toString(tupleType.getItemType()));
            } else {
                typeStr = "tuple";
            }
        } else if (type instanceof TupleType tupleType) {
            typeStr = "tuple[%s]".formatted(tupleType.getTupleElementTypes().stream().map(this::toString).collect(Collectors.joining(", ")));
        } else if (type instanceof GenericUserType generic) {
            typeStr = String.format("%s[%s]", generic.getName().toString(), String.join(", ", Arrays.stream(generic.getTypeParameters()).map(this::typeToString).toList().toArray(new String[0])));
        } else if (type instanceof UserType userType) {
            typeStr = userType.getName().toString();
        } else if (type instanceof NoReturn) {
            typeStr = "None";
        } else if (type instanceof PointerType) {
            throw new UnsupportedViewingException("Pointer type cannot be converted to Python");
        } else if (type instanceof ReferenceType ref) {
            typeStr = toString(ref.getTargetType());
        } else if (type instanceof LiteralType literal) {
            typeStr = "Literal[%s]".formatted(literal.getLiteral());
        } else if (type instanceof TypeAlternatives typeAlt) {
            typeStr = typeAlt.get().stream().map(this::typeToString).collect(Collectors.joining(" | "));
        } else if (type instanceof OptionalType optType) {
            typeStr = "%s | None".formatted(typeToString(optType.getTargetType()));
        }
        return type.isSafeReference() ? "'%s'".formatted(typeStr) : typeStr;
    }

    private String assignmentExpressionToString(AssignmentExpression expr) {
        if (!(expr.getLValue() instanceof SimpleIdentifier) || (expr.getRValue() instanceof AssignmentExpression)) {
            if (isExpressionMode()) {
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
            return String.format("#%s", comment.getUnescapedContent());
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
            return this.applyHooks(range, String.format("range(%s)", toString(stop)));
        } else if (start != null && (step == null || isStepDefault)) {
            return this.applyHooks(range, String.format("range(%s, %s)", toString(start), toString(stop)));
        }

        if (start == null || isStartDefault) {
            start = new IntegerLiteral(0).remap(range);
        }

        var result = String.format("range(%s, %s, %s)", toString(start), toString(stop), toString(step));
        result = this.applyHooks(range, result);
        return result;
    }

    private String binaryOpToString(BinaryExpression node) {
        String pattern = "";
        Expression left = node.getLeft();
        Expression right = node.getRight();
        String token = mapToToken(node).value;

        if (node instanceof ShortCircuitAndOp) {
            try {
                Node result = PythonSpecialNodeTransformations.detectCompoundComparison(node);
                if (result instanceof CompoundComparison
                        && !getConfigParameter("disableCompoundComparisons").asBoolean()) {
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
        } else if (node instanceof CompoundComparison op && getConfigParameter("disableCompoundComparisons").asBoolean()) {
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
                    return toString(newExpr.getInitializer());
                } else {
                    return arrayAllocationToString(newExpr.getType(), newExpr.getShape());
                }
            }
            case ObjectNewExpression newExpr -> {
                FunctionCall call = new FunctionCall(newExpr.getType(), newExpr.getConstructorArguments())
                        .remap(newExpr);
                return callsToString(call);
            }
            case MethodCall funcCall -> {
                MemberAccess methodAccess = new MemberAccess(funcCall.getObject(), funcCall.getFunctionName());
                methodAccess.remap(funcCall.getFunctionName());
                MemberAccess memAcc = parenFiller.process(methodAccess);
                return String.format("%s(%s)", toString(memAcc), argumentsToString(funcCall.getArguments()));
            }
            case FunctionCall funcCall -> {
                funcCall = parenFiller.processForPython(funcCall);
                return String.format("%s(%s)", toString(PythonSpecificFeatures.getFunctionExpression(funcCall)), argumentsToString(funcCall.getArguments()));
            }
            case CastTypeExpression cast -> {
                FunctionCall call = new FunctionCall(cast.getCastType(), cast.getValue()).remap(cast);
                return callsToString(call);
            }
            case null, default -> throw new UnsupportedViewingException("Not a callable object");
        }
    }

    private String arrayAllocationToString(Type itemType, Shape shape) {
        String result = _getListComprehensionByDimension(shape.getDimensionCount(),
                shape.getDimension(shape.getDimensionCount() - 1), "%s()".formatted(toString(itemType)));
        for (int i = shape.getDimensionCount() - 2; i >= 0; i--) {
            result = _getListComprehensionByDimension(i + 1, shape.getDimension(i), result);
        }
        return result;
    }

    private String arrayInitializerToString(ArrayInitializer initializer) {
        return String.format("[%s]", argumentsToString(initializer.getValues()));
    }

    private String _getListComprehensionByDimension(int depth, Expression dimension, String fillExpression) {
        return String.format("[%s for %s in range(%s)]", fillExpression, "_".repeat(depth), toString(dimension));
    }

    private String argumentsToString(List<Expression> expressions) {
        String[] exprStrings = new String[expressions.size()];
        for (int i = 0; i < exprStrings.length; i++) {
            exprStrings[i] = toString(expressions.get(i));
        }
        return String.join(", ", exprStrings);
    }

    private String branchStmtToString(Statement branchStmt, Tab tab) {
        if (branchStmt instanceof CompoundStatement) {
            return toString(branchStmt, tab);
        }
        return tab.concat(toString(branchStmt, tab));
    }

    private String conditionToString(IfStatement node, Tab tab) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.getBranches().size(); i++) {
            ConditionBranch branch = node.getBranches().get(i);
            if (i == 0) {
                if (!(branch.getBody() instanceof CompoundStatement))
                    sb.append(String.format("if %s:\n%s\n", toString(branch.getCondition()), branchStmtToString(branch.getBody(), tab.up())));
                else
                    sb.append(String.format("if %s:\n%s\n", toString(branch.getCondition()), branchStmtToString(branch.getBody(), tab)));
            } else {
                if (!(branch.getBody() instanceof CompoundStatement))
                    sb.append(String.format("%selif %s:\n%s\n", tab, toString(branch.getCondition()), branchStmtToString(branch.getBody(), tab.up())));
                else
                    sb.append(String.format("%selif %s:\n%s\n", tab, toString(branch.getCondition()), branchStmtToString(branch.getBody(), tab)));
            }
        }
        if (node.hasElseBranch()) {
            if (!(node.getElseBranch() instanceof CompoundStatement))
                sb.append(String.format("%selse:\n%s\n", tab, branchStmtToString(node.getElseBranch(), tab.up())));
            else
                sb.append(String.format("%selse:\n%s\n", tab, branchStmtToString(node.getElseBranch(), tab)));
        }
        return sb.toString().stripTrailing();
    }

    private String unaryToString(UnaryExpression node) {
        String pattern = "";
        Expression expr = node.getArgument();

        boolean expressionMode = isExpressionMode();

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
        tab = tab.up();
        if (node.getNodes().length == 0) {
            return tab.toString().concat("pass");
        }
        var constructor = ctx.viewingIterateBody(node);
        for (Node child : constructor) {
            StringBuilder builder = new StringBuilder();
            builder.append(tab);
            if (child instanceof CompoundStatement) {
                // Схлопываем лишний таб, так как блоки как самостоятельная сущность в Python не поддерживаются
                builder.append(toString(child, tab.down().down()));
            } else {
                builder.append(toString(child, tab));
            }
            constructor.appendString(builder.toString());
        }
        return String.join("\n", constructor.stringBuffer()).stripTrailing();
    }

    private String nodeListToString(List<Node> nodes, Tab tab) {
        if (nodes.isEmpty()) {
            return "pass";
        }
        var constructor = ctx.viewingIterateBody(nodes);
        for (Node child : constructor) {
            StringBuilder builder = new StringBuilder();
            builder.append(tab);
            if (child instanceof CompoundStatement) {
                // Схлопываем лишний таб, так как блоки как самостоятельная сущность в Python не поддерживаются
                var result = toString(child, tab.down().down());
                builder.append(result);
            } else {
                var result = toString(child, tab);
                builder.append(result);
            }
            constructor.appendString(builder.toString());
        }
        return String.join("\n", constructor.stringBuffer()).stripTrailing();
    }

    private String comparisonToString(BinaryComparison node) {
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

    @Override
    public OperatorToken mapToToken(Expression expr) {
        return ctx.requireTokenizer().getOperatorByNode(expr);
    }
}
