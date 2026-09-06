package org.vstu.meaningtree.languages;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedViewingException;
import org.vstu.meaningtree.languages.helpers.ComprehensionLowerer;
import org.vstu.meaningtree.languages.helpers.LoopElseLowerer;
import org.vstu.meaningtree.languages.helpers.MeaningTreeTransformations;
import org.vstu.meaningtree.languages.helpers.MultiCatchSplitter;
import org.vstu.meaningtree.languages.helpers.ResourceContextLowerer;
import org.vstu.meaningtree.languages.helpers.ScopeDeclarationLowerer;
import org.vstu.meaningtree.languages.support.features.NonlocalBindingFeature;
import org.vstu.meaningtree.languages.helpers.ReferenceToPointerLowerer;
import org.vstu.meaningtree.languages.helpers.TryElseLowerer;
import org.vstu.meaningtree.languages.support.features.NonDirectionalRangeForFeature;
import org.vstu.meaningtree.languages.support.features.PointerToMemberOperatorFeature;
import org.vstu.meaningtree.languages.support.features.ReferenceTypeFeature;
import org.vstu.meaningtree.languages.support.features.TryFinallyFeature;
import org.vstu.meaningtree.languages.support.features.UninferableVariableTypeFeature;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.definitions.*;
import org.vstu.meaningtree.nodes.definitions.components.DefinitionArgument;
import org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator;
import org.vstu.meaningtree.nodes.enums.DeclarationModifier;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.Identifier;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.*;
import org.vstu.meaningtree.nodes.expressions.calls.ConstructorCall;
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
import org.vstu.meaningtree.nodes.expressions.newexpr.PlacementNewExpression;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerMemberAccess;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerPackOp;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.io.FormatPrint;
import org.vstu.meaningtree.nodes.io.InputCommand;
import org.vstu.meaningtree.nodes.io.PrintCommand;
import org.vstu.meaningtree.nodes.io.PrintValues;
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
import org.vstu.meaningtree.nodes.statements.conditions.components.*;
import org.vstu.meaningtree.nodes.statements.exceptions.ExceptionCatchStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.RaiseExceptionStatement;
import org.vstu.meaningtree.nodes.statements.exceptions.components.CatchClause;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.GotoStatement;
import org.vstu.meaningtree.nodes.types.GenericUserType;
import org.vstu.meaningtree.nodes.types.NoReturn;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.nodes.types.containers.components.Shape;
import org.vstu.meaningtree.utils.analysis.imports.CppLibraryImportRegistry;
import org.vstu.meaningtree.utils.modules.ImportPathConverter;
import org.vstu.meaningtree.utils.tokens.OperatorToken;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.vstu.meaningtree.nodes.enums.AugmentedAssignmentOperator.POW;

public class CppViewer extends LanguageViewer {
    /** Имя в напечатанном коде: по нему видно, обращается ли вывод к содержимому заголовка. */
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public CppViewer(LanguageTranslator translator) {
        super(translator);
        _indentation = "    ";
        _indentLevel = 0;
        _openBracketOnSameLine = false;
        _bracketsAroundCaseBranches = false;
        _autoVariableDeclaration = false;
        configureSupportAndRenderers();
    }

    @Override
    protected MeaningTree preprocessTree(MeaningTree tree) {
        // Владение ресурсами снимается первым: дальше по конвейеру никакой узел о нём не знает
        List<UnaryOperator<MeaningTree>> transformations = new ArrayList<>(List.of(
                ScopeDeclarationLowerer::dropGlobals,
                ResourceContextLowerer::flatten,
                this::comprehensionLowered,
                LoopElseLowerer::lower,
                TryElseLowerer::lower,
                MultiCatchSplitter::lower
        ));
        if (representsReferencesAsPointers()) {
            // После этого прохода ссылок в дереве не остаётся, и запрет ссылок в режиме Си,
            // который проверяется уже по подготовленному дереву, к ним не придирается
            transformations.add(ReferenceToPointerLowerer::lower);
        }
        return MeaningTreeTransformations.apply(tree, transformations);
    }

    private MeaningTree comprehensionLowered(MeaningTree tree) {
        return ComprehensionLowerer.lower(tree, new ComprehensionLowerer.Target() {
            @Override
            public Type collectionType(ComprehensionLowerer.CollectionKind kind) {
                return switch (kind) {
                    case LIST -> new ListType(objectType());
                    case SET -> new SetType(objectType());
                    case DICTIONARY -> new OrderedDictionaryType(objectType(), objectType());
                };
            }

            @Override
            public Expression createCollection(ComprehensionLowerer.CollectionKind kind) {
                return null;
            }

            @Override
            public Expression append(SimpleIdentifier collection, ComprehensionLowerer.CollectionKind kind,
                                     List<Expression> values, Node origin) {
                if (kind == ComprehensionLowerer.CollectionKind.DICTIONARY) {
                    Expression pair = new FunctionCall(new SimpleIdentifier(stdPrefix() + "make_pair").remap(origin), values)
                            .remap(origin);
                    return new MethodCall(collection, new SimpleIdentifier("insert").remap(origin), pair);
                }
                String method = kind == ComprehensionLowerer.CollectionKind.SET ? "insert" : "push_back";
                return new MethodCall(collection, new SimpleIdentifier(method).remap(origin), values);
            }

            @Override
            public String loopVariableName(String originalName, int ordinal) {
                return originalName;
            }

            private Type objectType() {
                return new org.vstu.meaningtree.nodes.types.user.Class(new SimpleIdentifier("object"));
            }
        });
    }

    private void configureSupportAndRenderers() {
        registerRenderer(ProgramEntryPoint.class, this::toStringEntryPoint);
        registerRenderer(ExpressionStatement.class, this::toStringExpressionStatement);
        registerRenderer(VariableDeclaration.class, this::toStringVariableDeclaration);
        registerRenderer(FieldDeclaration.class, this::toStringFieldDeclaration);
        registerRenderer(IndexExpression.class, this::toStringIndexExpression);
        registerRenderer(ExpressionSequence.class, this::toStringCommaExpression);
        registerRenderer(TernaryOperator.class, this::toStringTernaryOperator);
        registerRenderer(MemoryAllocationCall.class, this::toStringMemoryAllocation);
        registerRenderer(MemoryFreeCall.class, this::toStringMemoryFree);
        registerRenderer(PrintCommand.class, this::toStringPrint);
        registerRenderer(InputCommand.class, this::toStringInput);
        registerRenderer(FunctionCall.class, this::toStringFunctionCall);
        registerRenderer(MethodCall.class, this::toStringMethodCall);
        registerRenderer(ConstructorCall.class, call -> toString(call.getOwner()) + "(" + toStringFunctionCallArgumentsList(call.getArguments()) + ")");
        registerRenderer(ParenthesizedExpression.class, this::toStringParenthesizedExpression);
        registerRenderer(AssignmentExpression.class, this::toStringAssignmentExpression);
        registerRenderer(AssignmentStatement.class, this::toStringAssignmentStatement);
        registerRenderer(IntType.class, this::toStringIntType);
        registerRenderer(FloatType.class, this::toStringFloatType);
        registerRenderer(CharacterType.class, this::toStringCharacterType);
        registerRenderer(PointerType.class, this::toStringType);
        registerRenderer(ReferenceType.class, this::toStringType);
        registerRenderer(DictionaryType.class, this::toStringType);
        registerRenderer(ArrayType.class, this::toStringType);
        registerRenderer(UnmodifiableListType.class, this::toStringType);
        registerRenderer(SetType.class, this::toStringType);
        registerRenderer(PlainCollectionType.class, this::toStringType);
        registerRenderer(GenericUserType.class, this::toStringType);
        registerRenderer(UserType.class, this::toStringType);
        registerRenderer(Type.class, this::toStringType);
        registerRenderer(SelfReference.class, n -> "this");
        registerRenderer(SimpleIdentifier.class, n -> n.getName());
        registerRenderer(ScopedIdentifier.class, this::toStringIdentifier);
        registerRenderer(QualifiedIdentifier.class, this::toStringIdentifier);
        registerRenderer(Identifier.class, this::toStringIdentifier);
        registerRenderer(FloatLiteral.class, n -> n.getStringValue(true));
        registerRenderer(IntegerLiteral.class, this::toStringNumericLiteral);
        registerRenderer(NumericLiteral.class, this::toStringNumericLiteral);
        registerRenderer(FloorDivOp.class, this::toStringFloorDiv);
        registerRenderer(UnaryExpression.class, this::toStringUnaryExpression);
        registerRenderer(BinaryExpression.class, this::toStringBinaryExpression);
        registerRenderer(NullLiteral.class, n -> "nullptr");
        registerRenderer(StringLiteral.class, this::toStringStringLiteral);
        registerRenderer(CharacterLiteral.class, this::toStringCharLiteral);
        registerRenderer(BoolLiteral.class, n -> n.getValue() ? "true" : "false");
        registerRenderer(PlainCollectionLiteral.class, this::toStringCollectionLiteral);
        registerRenderer(DictionaryLiteral.class, this::toStringDictionaryLiteral);
        registerRenderer(CastTypeExpression.class, this::toStringCast);
        registerRenderer(SizeofExpression.class, this::toStringSizeof);
        registerRenderer(NewExpression.class, this::toStringNew);
        registerRenderer(DeleteExpression.class, this::toStringDelete);
        registerRenderer(DeleteStatement.class, n -> toStringDelete(n.toExpression()) + ";");
        registerRenderer(MemberAccess.class, this::toStringMemberAccess);
        registerRenderer(CompoundComparison.class, this::toStringCompoundComparison);
        registerRenderer(DefinitionArgument.class, n -> toString(n.getInitialExpression()));
        registerRenderer(Comment.class, this::toStringComment);
        registerRenderer(InterpolatedStringLiteral.class, this::fromInterpolatedString);
        registerRenderer(MultipleAssignmentStatement.class, this::fromMultipleAssignmentStatement);
        registerRenderer(ChainedAssignmentStatement.class, this::toStringChainedAssignmentStatement);
        registerRenderer(IfStatement.class, this::toStringIfStatement);
        registerRenderer(ExceptionCatchStatement.class, this::toStringExceptionCatchStatement);
        registerRenderer(ResourceContextStatement.class, this::toStringResourceContextStatement);
        registerRenderer(CatchClause.class, this::toStringCatchClause);
        registerRenderer(RaiseExceptionStatement.class, this::toStringRaiseExceptionStatement);
        registerRenderer(CompoundStatement.class, this::toStringCompoundStatement);
        registerRenderer(RangeForLoop.class, this::toStringRangeForLoop);
        registerRenderer(GeneralForLoop.class, this::toStringGeneralForLoop);
        registerRenderer(WhileLoop.class, this::toStringWhileLoop);
        registerRenderer(DoWhileLoop.class, this::toStringDoWhileLoop);
        registerRenderer(InfiniteLoop.class, this::toStringInfiniteLoop);
        registerRenderer(SwitchStatement.class, this::toStringSwitchStatement);
        registerRenderer(FunctionDefinition.class, this::toStringFunctionDefinition);
        registerRenderer(FunctionDeclaration.class, this::toStringFunctionPrototype);
        registerRenderer(MethodDefinition.class, this::toStringMethodDefinition);
        registerRenderer(MethodDeclaration.class, this::toStringMethodDeclaration);
        registerRenderer(ObjectConstructorDefinition.class, this::toStringObjectConstructorDefinition);
        registerRenderer(ObjectDestructorDefinition.class, this::toStringObjectDestructorDefinition);
        registerRenderer(ClassDeclaration.class, this::toStringClassDeclaration);
        registerRenderer(ClassDefinition.class, this::toStringClassDefinition);
        registerRenderer(EnumDeclaration.class, this::toStringEnumDeclaration);
        registerRenderer(DeclarationArgument.class, this::toStringDeclarationArgument);
        registerRenderer(ArrayInitializer.class, this::toStringArrayInitializer);
        registerRenderer(ReturnStatement.class, this::toStringReturnStatement);
        registerRenderer(EmptyStatement.class, n -> "");
        registerRenderer(BreakStatement.class, this::toStringBreakStatement);
        registerRenderer(ContinueStatement.class, this::toStringContinueStatement);
        registerRenderer(GotoStatement.class, this::toStringGotoStatement);
        registerRenderer(ForEachLoop.class, this::toStringForEachLoop);
        registerRenderer(VariableDeclarator.class, n -> toStringVariableDeclarator(n, new UnknownType()));
        registerRenderer(Shape.class, this::toStringShape);
        registerRenderer(MultipleAssignmentStatement.class, this::toStringMultipleAssignmentStatement);
        registerRenderer(ConditionBranch.class, this::toStringConditionBranch);
        registerRenderer(MatchValueCaseBlock.class, this::toStringCaseBlock);
        registerRenderer(DefaultCaseBlock.class, this::toStringCaseBlock);
        registerRenderer(CaseBlock.class, this::toStringCaseBlock);
        registerRenderer(ListUnpackingVariableDeclaration.class,
                (node) -> toString(node.toVariableDeclaration()));
        registerRenderer(ListUnpackingAssignmentStatement.class,
                (node) -> toString(node.toMultipleAssignmentStstement()));
        registerRenderer(Import.class, this::toStringImport);
        registerRenderer(PackageDeclaration.class, this::toStringPackageDeclaration);

        registerPostRenderPreparation(Statement.class, (node, code) -> {
            if (node.getJumpLabel() != null) {
                return "%s:\n%s".formatted(node.getJumpLabel().getName(), code);
            }
            return code;
        });

        registerPreRenderPreparation(UnaryExpression.class, parenFiller::process);
        registerPreRenderPreparation(BinaryExpression.class, parenFiller::process);
        registerPreRenderPreparation(IndexExpression.class, parenFiller::process);
        registerPreRenderPreparation(TernaryOperator.class, parenFiller::process);
        registerPreRenderPreparation(CastTypeExpression.class, parenFiller::process);
        registerPreRenderPreparation(MethodCall.class, parenFiller::process);
        registerPreRenderPreparation(QualifiedIdentifier.class, parenFiller::process);
        registerPreRenderPreparation(MemberAccess.class, parenFiller::process);
        registerPreRenderPreparation(AssignmentExpression.class, node -> (AssignmentExpression) parenFiller.process(node));

        registerUnsupportedFeature(new NonDirectionalRangeForFeature());
        registerUnsupportedFeature(new PointerToMemberOperatorFeature());
        registerUnsupportedFeature(new UninferableVariableTypeFeature());
        registerUnsupportedFeature(new TryFinallyFeature());
        registerUnsupportedFeature(new NonlocalBindingFeature());
        // В Си ссылок нет. Проверка идёт после preprocessTree, то есть после того, как
        // representReferencesAsPointers уже переписал ссылки указателями: сюда доедет только
        // то, что переписать не удалось, и это в Си действительно невыразимо
        registerUnsupportedFeature(new ReferenceTypeFeature(this::isCMode));
        registerUnsupportedFeature(MatMulOp.class);
        // Генераторы и итераторы в C++ не поддерживаются на этом этапе. Запрет объявлен явно:
        // GeneratorDefinition наследует FunctionDefinition, а IteratorDefinition —
        // ClassDefinition, и подъём по надклассу молча отрисовал бы их обычной функцией
        // и обычным классом, потеряв весь смысл конструкции
        registerUnsupportedFeature(GeneratorDefinition.class);
        registerUnsupportedFeature(IteratorDefinition.class);
        registerUnsupportedFeature(YieldStatement.class);
    }

    private final String _indentation;
    private int _indentLevel;
    private final boolean _openBracketOnSameLine;
    private final boolean _bracketsAroundCaseBranches;
    private final boolean _autoVariableDeclaration;


    /*******************************************************************/
    /* Все, что касается индетации для блоков */
    private void increaseIndentLevel() {
        _indentLevel++;
    }

    private void decreaseIndentLevel() {
        _indentLevel--;

        if (_indentLevel < 0) {
            throw new UnsupportedViewingException("Indentation level can't be less than zero");
        }
    }

    private String indent(String s) {
        if (_indentLevel == 0) {
            return s;
        }

        return _indentation.repeat(Math.max(0, _indentLevel)) + s;
    }

    /*******************************************************************/
    /* Перевод MeaningTree и узлов в строки */
    @NotNull
    @Override
    public String toString(@NotNull MeaningTree meaningTree) {
        return super.toString(meaningTree);
    }


    private String toStringForEachLoop(ForEachLoop forEachLoop) {
        var type = toString(forEachLoop.getItem().getType());
        String iterVarId;
        if (forEachLoop.getItem().getDeclarators().length > 1) {
            iterVarId = "auto & [%s]".formatted(Arrays.stream(forEachLoop.getItem().getDeclarators())
                    .map(VariableDeclarator::getIdentifier).map(this::toString).collect(Collectors.joining(", "))
            );
        } else {
            iterVarId = toString(forEachLoop.getItem().getDeclarators()[0].getIdentifier());
        }
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

    private String toStringEmptyStatement(EmptyStatement emptyStatement) {
        return "";
    }

    /*******************************************************************/
    /* Перевод return */
    private String toStringReturnStatement(ReturnStatement returnStatement) {
        StringBuilder builder = new StringBuilder();
        builder.append("return");

        if (returnStatement.getExpression() != null)
            builder.append(" ").append(toString(returnStatement.getExpression()));

        builder.append(";");
        return builder.toString();
    }

    /*******************************************************************/
    /* Перевод инициализатора массива */
    private String toStringArrayInitializer(ArrayInitializer arrayInitializer) {
        StringBuilder builder = new StringBuilder();
        builder.append("{");

        for (var value : arrayInitializer.getValues()) {
            builder.append(toString(value)).append(", ");
        }

        builder.deleteCharAt(builder.length() - 1);
        builder.deleteCharAt(builder.length() - 1);

        builder.append("}");
        return builder.toString();
    }

    /*******************************************************************/
    /* Перевод оператора ввода (cin) */
    private String toStringInput(InputCommand inputCommand) {
        StringBuilder builder = new StringBuilder();

        builder.append(stdPrefix()).append("cin");
        for (var expr : inputCommand.getArguments()) {
            builder.append(" >> ").append(toString(expr));
        }

        return builder.toString();
    }

    /*******************************************************************/
    /* Перевод определения функции */
    private String toStringClassDeclaration(ClassDeclaration declaration) {
        return toStringClassDeclarationAs(declaration, declaration instanceof StructureDeclaration);
    }

    private String toStringClassDeclarationAs(ClassDeclaration declaration, boolean asStruct) {
        StringBuilder builder = new StringBuilder(asStruct ? "struct " : "class ");
        builder.append(toString(declaration.getName()));
        if (!declaration.getParents().isEmpty()) {
            builder.append(" : ");
            builder.append(declaration.getParents().stream()
                    .map(parent -> "public " + toString(parent))
                    .collect(Collectors.joining(", ")));
        }
        return builder.toString();
    }

    private String toStringEnumDeclaration(EnumDeclaration declaration) {
        StringBuilder builder = new StringBuilder(declaration.isScoped() ? "enum class " : "enum ");
        builder.append(toString(declaration.getName())).append("\n").append(indent("{"));

        increaseIndentLevel();
        List<String> constants = new ArrayList<>();
        for (Identifier constant : declaration.getConstants()) {
            Expression value = declaration.getConstant(constant);
            String constantCode = value == null
                    ? toString(constant)
                    : "%s = %s".formatted(toString(constant), toString(value));
            constants.add(indent(constantCode));
        }
        decreaseIndentLevel();

        if (!constants.isEmpty()) {
            builder.append("\n").append(String.join(",\n", constants));
        }
        builder.append("\n").append(indent("}")).append(";");
        return builder.toString();
    }

    private String toStringClassDefinition(ClassDefinition definition) {
        if (isCMode()) {
            return toStringCStructureDefinition(definition);
        }
        boolean asStruct = isRenderedAsStruct(definition);
        // У структуры члены по умолчанию публичные, у класса — приватные
        DeclarationModifier defaultAccess = asStruct ? DeclarationModifier.PUBLIC : DeclarationModifier.PRIVATE;

        StringBuilder builder = new StringBuilder();
        builder.append(toStringClassDeclarationAs(definition.getDeclaration(), asStruct)).append("\n").append(indent("{"));

        increaseIndentLevel();
        DeclarationModifier currentAccess = defaultAccess;
        // Печаталась ли уже явная секция доступа: после неё все члены лежат внутри какой-то
        // секции, в том числе те, чей доступ совпал с умолчанием класса
        boolean underAccessSpecifier = false;
        var constructor = ctx.viewingIterateBody(definition.getBody().getNodeList());
        for (Node member : constructor) {
            // Член под явной секцией доступа печатается на уровень глубже, чем сама секция.
            // Уровень поднимается на время рендеринга и вычисления отступа, а не приклеивается
            // к готовой строке вторым indent(): indent() ставит префикс только первой строке,
            // поэтому у многострочного члена (метода, вложенного класса) остальные строки берут
            // отступ из _indentLevel и должны рендериться уже на нужном уровне.
            // У члена без собственного объявления (комментарий) доступа нет: он остаётся в той
            // секции, которая открыта, и сам её не переключает.
            DeclarationModifier memberAccess = getMemberAccess(member);
            if (memberAccess == null) {
                memberAccess = currentAccess;
            }
            underAccessSpecifier = underAccessSpecifier || memberAccess != currentAccess;

            if (underAccessSpecifier) {
                increaseIndentLevel();
            }
            String memberCode = indent(toString(member));
            if (underAccessSpecifier) {
                decreaseIndentLevel();
            }

            if (memberCode.isBlank()) {
                continue;
            }

            if (memberAccess != currentAccess) {
                builder.append("\n").append(indent(toCppAccessSpecifier(memberAccess))).append(":");
                currentAccess = memberAccess;
            }

            builder.append("\n").append(memberCode);
        }
        constructor.getNodes();
        decreaseIndentLevel();

        builder.append("\n").append(indent("}")).append(";");
        return builder.toString();
    }

    private String toStringCStructureDefinition(ClassDefinition definition) {
        if (!isRenderedAsStruct(definition) || !definition.getDeclaration().getParents().isEmpty()) {
            throw new UnsupportedViewingException("C mode supports only structures without inheritance");
        }

        String name = toString(definition.getDeclaration().getName());
        List<String> fields = new ArrayList<>();
        var constructor = ctx.viewingIterateBody(definition.getBody().getNodeList());
        for (Node member : constructor) {
            if (!(member instanceof FieldDeclaration field)
                    || field.getModifiers().contains(DeclarationModifier.PRIVATE)
                    || field.getModifiers().contains(DeclarationModifier.PROTECTED)
                    || field.getModifiers().contains(DeclarationModifier.STATIC)) {
                throw new UnsupportedViewingException("C mode structures may contain only public instance fields");
            }
            fields.add(toString(field));
        }
        constructor.getNodes();
        if (fields.isEmpty()) {
            throw new UnsupportedViewingException("C mode does not support empty structures");
        }

        StringBuilder builder = new StringBuilder("typedef struct ").append(name).append("\n").append(indent("{"));
        increaseIndentLevel();
        for (String field : fields) {
            builder.append("\n").append(indent(field));
        }
        decreaseIndentLevel();
        return builder.append("\n} ").append(name).append(";").toString();
    }

    /**
     * Как struct раскрывается только структура: C++ struct, Python dataclass. Обычный класс
     * всегда остается классом, иначе обратная конверсия C++ class -> C++ теряет ключевое слово.
     */
    private boolean isRenderedAsStruct(ClassDefinition definition) {
        return definition instanceof StructureDefinition
                || definition.getDeclaration() instanceof StructureDeclaration;
    }

    /** {@code null} — у члена нет собственного объявления, а значит и модификатора доступа. */
    @Nullable
    private DeclarationModifier getMemberAccess(Node member) {
        List<DeclarationModifier> modifiers = switch (member) {
            case Definition definition -> definition.getDeclaration().getModifiers();
            case Declaration declaration -> declaration.getModifiers();
            default -> null;
        };
        if (modifiers == null) {
            return null;
        }
        if (modifiers.contains(DeclarationModifier.PUBLIC)) {
            return DeclarationModifier.PUBLIC;
        }
        if (modifiers.contains(DeclarationModifier.PROTECTED)) {
            return DeclarationModifier.PROTECTED;
        }
        return DeclarationModifier.PRIVATE;
    }

    private String toCppAccessSpecifier(DeclarationModifier modifier) {
        return switch (modifier) {
            case PUBLIC -> "public";
            case PROTECTED -> "protected";
            case PRIVATE -> "private";
            default -> throw new IllegalArgumentException("Not an access modifier: " + modifier);
        };
    }

    private String toStringFunctionDefinition(FunctionDefinition functionDefinition) {
        StringBuilder builder = new StringBuilder();

        // Преобразование типа нужно, чтобы избежать вызова toString(Node node)
        String functionDeclaration = toString(
                (FunctionDeclaration) functionDefinition.getDeclaration()
        );
        builder.append(functionDeclaration);

        String body = toString(functionDefinition.getBody());
        if (_openBracketOnSameLine)
        { builder.append(" ").append(body); }
        else
        { builder.append("\n").append(indent(body)); }

        return builder.toString();
    }

    /**
     * Объявление функции, стоящее в программе само по себе, — прототип, и он завершается точкой
     * с запятой. Внутри определения тот же узел — его заголовок, и точку с запятой там ставить
     * нельзя, поэтому решает окружение: определение печатает своё объявление через общую
     * диспетчеризацию (иначе узел не попал бы ни в хуки, ни в source map), и различить два
     * случая можно только по объемлющему узлу.
     */
    private String toStringFunctionPrototype(FunctionDeclaration functionDeclaration) {
        String signature = toStringFunctionDeclaration(functionDeclaration);
        return ctx.isDirectlyInNode(FunctionDefinition.class) ? signature : signature + ";";
    }

    private String toStringFunctionDeclaration(FunctionDeclaration functionDeclaration) {
        StringBuilder builder = new StringBuilder();

        if (functionDeclaration.getModifiers().contains(DeclarationModifier.STATIC)) {
            builder.append("static ");
        }

        String returnType = toString(functionDeclaration.getReturnType());
        builder.append(returnType).append(" ");

        String name = toString(functionDeclaration.getName());
        builder.append(name);

        if (isCMode() && functionDeclaration.getReturnType() instanceof ArrayType) {
            throw new UnsupportedViewingException("C functions cannot return arrays by value");
        }
        String parameters = isCMode()
                && functionDeclaration.getName().toString().equals("main")
                && functionDeclaration.getArguments().isEmpty()
                ? "(void)"
                : toStringParameters(functionDeclaration.getArguments());
        builder.append(parameters);

        return builder.toString();
    }

    private String toStringMethodDeclaration(MethodDeclaration declaration) {
        return toStringMethodSignature(declaration) + ";";
    }

    private String toStringMethodDefinition(MethodDefinition definition) {
        String signature = toStringMethodSignature(definition.getDeclaration());
        String body = toString(definition.getBody());
        return _openBracketOnSameLine
                ? signature + " " + body
                : signature + "\n" + indent(body);
    }

    private String toStringMethodSignature(MethodDeclaration declaration) {
        boolean isVirtual = declaration.getModifiers().contains(DeclarationModifier.VIRTUAL)
                || declaration.getModifiers().contains(DeclarationModifier.ABSTRACT);
        StringBuilder builder = new StringBuilder();
        if (isVirtual) {
            builder.append("virtual ");
        }
        builder.append(toStringFunctionDeclaration(declaration));
        if (declaration.getModifiers().contains(DeclarationModifier.ABSTRACT)) {
            builder.append(" = 0");
        }
        return builder.toString();
    }

    private String toStringObjectConstructorDefinition(ObjectConstructorDefinition definition) {
        return toStringConstructorLikeDefinition(classMemberOwnerName(definition.getDeclaration()), definition.getDeclaration().getArguments(), definition.getBody());
    }

    private String toStringObjectDestructorDefinition(ObjectDestructorDefinition definition) {
        return toStringConstructorLikeDefinition("~" + classMemberOwnerName(definition.getDeclaration()), List.of(), definition.getBody());
    }

    private String classMemberOwnerName(MethodDeclaration declaration) {
        if (declaration.getOwner() != null) {
            // Простое имя, а не квалифицированное: конструктор и деструктор печатаются внутри
            // тела класса, где вложенный Outer::Inner должен называться Inner
            return toString(declaration.getOwner().getName());
        }
        if (declaration.getParentDeclaration() != null) {
            return toString(declaration.getParentDeclaration().getName());
        }
        return toString(declaration.getName());
    }

    private String toStringConstructorLikeDefinition(String name,
                                                      List<DeclarationArgument> arguments,
                                                      CompoundStatement body) {
        StringBuilder builder = new StringBuilder(name);
        builder.append(toStringParameters(arguments));

        List<ConstructorCall> baseCalls = new ArrayList<>();
        List<Node> bodyNodes = new ArrayList<>();
        for (Node node : body.getNodes()) {
            if (node instanceof ExpressionStatement statement
                    && statement.getExpression() instanceof ConstructorCall call
                    && call.isBaseClassCall()) {
                baseCalls.add(call);
            } else {
                bodyNodes.add(node);
            }
        }
        if (!baseCalls.isEmpty()) {
            builder.append(" : ").append(baseCalls.stream().map(this::toString).collect(Collectors.joining(", ")));
        }

        // Тело пересобирается без вызовов базового конструктора — размечаем его исходным телом
        String bodyCode = toString(new CompoundStatement(bodyNodes).remap(body));
        if (_openBracketOnSameLine) {
            builder.append(" ").append(bodyCode);
        } else {
            builder.append("\n").append(indent(bodyCode));
        }
        return builder.toString();
    }

    private String toStringFieldDeclaration(FieldDeclaration declaration) {
        String prefix = declaration.getModifiers().contains(DeclarationModifier.STATIC) ? "static " : "";
        return prefix + toString(
                new VariableDeclaration(declaration.getType(), declaration.getDeclarators()).remap(declaration));
    }

    private String toStringDeclarationArgument(DeclarationArgument parameter) {
        // Параметр без имени бывает только в прототипе, где имя необязательно: печатается один
        // тип, ровно как в исходнике
        if (!parameter.hasName()) {
            return toString(parameter.getType());
        }
        if (isCharBufferString(parameter.getType())) {
            StringType buffer = (StringType) parameter.getType();
            return "%s %s[%s]".formatted(charElementSpelling(buffer), toString(parameter.getName()),
                    toString(buffer.getMaxLength()));
        }
        if (isCMode() && parameter.getType() instanceof ArrayType array) {
            return "%s %s%s".formatted(toString(array.getItemType()), toString(parameter.getName()),
                    toString(array.getShape()));
        }
        String type = toString(parameter.getType());
        String name = toString(parameter.getName());
        return "%s %s".formatted(type, name);
    }

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

    /*******************************************************************/
    /* Перевод свитча */
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

            var constructor = ctx.viewingIterateBody(nodesList);
            for (Node node : constructor) {
                constructor.appendString(indent(toString(node)));
            }
            builder.append(String.join("\n", constructor.stringBuffer())).append("\n");

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

    /*******************************************************************/
    /* Перевод бесконечного цикла */
    private String toStringInfiniteLoop(InfiniteLoop infiniteLoop) {
        StringBuilder builder = new StringBuilder();

        builder.append(indent("while (true)"));
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

        return builder.toString();
    }

    /*******************************************************************/
    /* Перевод операторов управления циклов */
    @NotNull
    private String toStringBreakStatement(BreakStatement stmt) {
        if (stmt.getJumpLabel() != null) {
            return toString(stmt.toGoto());
        }
        return "break;";
    }

    @NotNull
    private String toStringContinueStatement(ContinueStatement stmt) {
        if (stmt.getJumpLabel() != null) {
            return toString(stmt.toGoto());
        }
        return "continue;";
    }

    @NotNull
    private String toStringGotoStatement(GotoStatement stmt) {
        return "goto %s;".formatted(stmt.getJumpDestination());
    }


    /*******************************************************************/
    /* Перевод цикла while */
    public String toStringWhileLoop(WhileLoop whileLoop) {
        String header = "while (" + toString(whileLoop.getCondition()) + ")";

        Statement body = whileLoop.getBody();
        if (body instanceof CompoundStatement compStmt) {
            // indent сдвигает только первую строку, а это как раз открывающая скобка тела:
            // без него она осталась бы в нулевой колонке у вложенного цикла
            return header + (_openBracketOnSameLine
                    ? " " + toString(compStmt)
                    : "\n" + indent(toString(compStmt)));
        }
        else {
            increaseIndentLevel();
            String result = header + "\n" + indent(toString(body));
            decreaseIndentLevel();
            return result;
        }
    }

    private String toStringDoWhileLoop(DoWhileLoop doWhileLoop) {
        String header = "do";
        Statement body = doWhileLoop.getBody();
        String condition = "while (" + toString(doWhileLoop.getCondition()) + ");";

        if (body instanceof CompoundStatement compoundStatement) {
            String renderedBody = _openBracketOnSameLine
                    ? " " + toString(compoundStatement)
                    : "\n" + indent(toString(compoundStatement));
            return header + renderedBody + " " + condition;
        }

        increaseIndentLevel();
        String renderedBody = indent(toString(body));
        decreaseIndentLevel();
        return header + "\n" + renderedBody + "\n" + indent(condition);
    }

    private String toStringShape(Shape shape) {
        // размерность массива: [dim][dim]...
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < shape.getDimensionCount(); i++) {
            builder.append("[");
            Expression dim = shape.getDimension(i);
            if (dim != null) {
                builder.append(toString(dim));
            }
            builder.append("]");
        }
        return builder.toString();
    }

    /*******************************************************************/
    /* Перевод узла оператора присвоения */
    private String toStringAssignmentStatement(AssignmentStatement assignmentStatement) {
        return toStringAssignmentExpression(assignmentStatement.toExpression()).concat(";");
    }

    /*******************************************************************/
    /* Перевод узла цикла фор общего и по диапазону */
    public String toStringGeneralForLoop(GeneralForLoop generalForLoop) {
        StringBuilder builder = new StringBuilder();

        builder.append("for (");

        boolean addSemi = true;
        if (generalForLoop.hasInitializer()) {
            String init = toString(generalForLoop.getInitializer());
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

    private String toStringMultipleAssignmentStatement(MultipleAssignmentStatement multipleAssignmentStatement) {
        // Трансляция MultipleAssignmentStatement по умолчанию не подходит -
        // в результате будут получены присваивания, написанные через точку с запятой.
        // Поэтому вручную получаем список присваиваний и создаем правильное отображение.
        StringBuilder builder = new StringBuilder();

        for (AssignmentStatement assignmentStatement : multipleAssignmentStatement.getStatements()) {
            AssignmentExpression assignmentExpression = new AssignmentExpression(
                    assignmentStatement.getLValue(),
                    assignmentStatement.getRValue()
            ).remap(assignmentStatement);
            builder
                    .append(toStringAssignmentExpression(assignmentExpression))
                    .append(", ");
        }

        // Удаляем лишние пробел и запятую в конце последнего присвоения
        if (builder.length() > 2) {
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
        }

        return builder.toString();
    }

    private String getForRangeUpdate(RangeForLoop forRangeLoop) {
        if (forRangeLoop.getRangeType() == Range.Direction.UP) {
            long stepValue;
            try {
                stepValue = forRangeLoop.getRange().getStepValueAsLong();
            } catch (IllegalStateException exception) {
                if (forRangeLoop.getStep() == null) {
                    return String.format("%s++", toString(forRangeLoop.getIdentifier()));
                }
                return String.format("%s += %s", toString(forRangeLoop.getIdentifier()), toString(forRangeLoop.getStep()));
            }

            if (stepValue == 1) {
                return String.format("%s++", toString(forRangeLoop.getIdentifier()));
            }
            else {
                return String.format("%s += %d", toString(forRangeLoop.getIdentifier()), stepValue);
            }
        }
        else if (forRangeLoop.getRangeType() == Range.Direction.DOWN) {
            long stepValue;
            try {
                stepValue = forRangeLoop.getRange().getStepValueAsLong();
            } catch (IllegalStateException exception) {
                if (forRangeLoop.getStep() == null) {
                    return String.format("%s--", toString(forRangeLoop.getIdentifier()));
                }
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
        if (forRangeLoop.getRangeType() == Range.Direction.UP) {
            String header = "int %s = %s; %s %s %s; %s";
            String compOperator = forRangeLoop.getRange().isExcludingEnd() ? "<" : "<=";
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
        else if (forRangeLoop.getRangeType() == Range.Direction.DOWN) {
            String header = "int %s = %s; %s %s %s; %s";
            String compOperator = forRangeLoop.getRange().isExcludingEnd() ? ">" : ">=";
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
            } else {
                builder.append("\n");
                builder.append(indent(toString(body)));
            }
        } else {
            builder.append("\n");
            increaseIndentLevel();
            builder.append(indent(toString(body)));
            decreaseIndentLevel();
        }

        return builder.toString();
    }

    /*******************************************************************/
    /* Перевод узла блочного оператора  */
    public String toStringCompoundStatement(CompoundStatement stmt) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        increaseIndentLevel();
        
        // Use direct list of nodes instead of DFS traversal to maintain order
        var constructor = ctx.viewingIterateBody(stmt);
        for (Node node : constructor) {
            String s = toString(node);
            if (s.isEmpty()) {
                continue;
            }

            constructor.appendString(indent(s));
        }

        builder.append(String.join("\n", constructor.stringBuffer())).append("\n");
        
        decreaseIndentLevel();
        builder.append(indent("}"));
        return builder.toString();
    }

    /*******************************************************************/
    /* Перевод обработки исключений */
    public String toStringExceptionCatchStatement(ExceptionCatchStatement stmt) {
        if (stmt.hasFinallyBranch()) {
            // Гарантированное выполнение выражается в C++ деструкторами и scope guard'ами,
            // а не блоком, поэтому автоматически перевести ветвь нельзя
            throw new UnsupportedViewingException("C++ has no finally branch");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("try").append(toStringBlockAfterHeader(stmt.getBody()));
        for (CatchClause clause : stmt.getCatchClauses()) {
            builder.append("\n").append(indent(toString(clause)));
        }
        return builder.toString();
    }

    /**
     * Владение ресурсами C++ записать нечем, поэтому оно разворачивается в плоский блок.
     * Обычно это уже сделано в {@link #preprocessTree}, и сюда узел доходит только при
     * прямом рендере одного узла в обход {@code toString(MeaningTree)} — как и проверка
     * {@code finally} ниже, это страховка на такой вызов.
     */
    private String toStringResourceContextStatement(ResourceContextStatement stmt) {
        return toString(ResourceContextLowerer.flatten(new MeaningTree(stmt)).getRootNode());
    }

    private String toStringCatchClause(CatchClause clause) {
        return String.format("catch (%s)%s", toStringCatchParameter(clause), toStringBlockAfterHeader(clause.getBody()));
    }

    /**
     * Параметр {@code catch}. Перехват без типа записывается многоточием, объекты принимаются
     * по константной ссылке (иначе исключение копируется и срезается до объявленного типа),
     * а встроенные типы — по значению.
     */
    private String toStringCatchParameter(CatchClause clause) {
        if (clause.catchesAny()) {
            return "...";
        }

        Type exceptionType = clause.getExceptionTypes().getFirst();
        String rendered = exceptionType instanceof UserType || exceptionType instanceof UnknownType
                ? "const " + toString(exceptionType) + "&"
                : toString(exceptionType);

        return clause.hasName() ? rendered + " " + toString(clause.getName()) : rendered;
    }

    private String toStringRaiseExceptionStatement(RaiseExceptionStatement stmt) {
        return stmt.hasException() ? String.format("throw %s;", toString(stmt.getException())) : "throw;";
    }

    private String toStringBlockAfterHeader(Statement body) {
        if (body instanceof CompoundStatement compound) {
            return (_openBracketOnSameLine ? " " : "\n") + indent(toString(compound));
        }
        increaseIndentLevel();
        String rendered = indent(toString(body));
        decreaseIndentLevel();
        return (_openBracketOnSameLine ? " {\n" : "\n" + indent("{") + "\n") + rendered + "\n" + indent("}");
    }

    /*******************************************************************/
    /* Перевод узла ветвления  */
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

    /* Перевод одной ветки условия  */
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

    private String toStringMemoryFree(MemoryFreeCall mFree) {
        return String.format("free(%s)", toString(mFree.getArguments().getFirst()));
    }

    private String toStringMemoryAllocation(MemoryAllocationCall mAlloc) {
        if (mAlloc.isClearAllocation()) {
            return "calloc(%s, sizeof(%s))".formatted(toString(mAlloc.getCount()), toString(mAlloc.getType()));
        }
        return "malloc(sizeof(%s) * %s)".formatted(toString(mAlloc.getType()), toString(mAlloc.getCount()));
    }

    private String toStringPrint(PrintCommand print) {
        if (print instanceof FormatPrint fmt) {
            if (fmt.getArguments().isEmpty()) {
                return String.format("printf(%s)", toString(fmt.getFormatString()));
            }
            return String.format("printf(%s, %s)", toString(fmt.getFormatString()), toStringFunctionCallArgumentsList(fmt.getArguments()));
        }
        String res = String.format("%scout << %s", stdPrefix(), print.getArguments().stream().map(this::toString).collect(Collectors.joining(" << ")));
        if (print instanceof PrintValues pVal) {
            res += pVal.addsNewLine() ? " << %sendl".formatted(stdPrefix()) : "";
        }
        return res;
    }

    /**
     * Экранирование берётся Си-шное, а не общее: {@code CharacterLiteral.escapedString()} даёт
     * запись Java, и нулевой символ выходит из неё как {@code '\u0000'} — в Си такой литерал
     * не собирается.
     */
    private String toStringCharLiteral(CharacterLiteral cl) {
        return "'%s'".formatted(switch (cl.getValue()) {
            case 0 -> "\\0";
            case 0x07 -> "\\a";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case 0x0B -> "\\v";
            case '\\' -> "\\\\";
            case '\'' -> "\\'";
            default -> cl.escapedString();
        });
    }

    private String toStringComment(Comment comment) {
        if (comment.isMultiline()) {
            return "/*" + comment.getUnescapedContent() + "*/";
        }

        return "//%s".formatted(comment.getUnescapedContent());
    }

    private String fromMultipleAssignmentStatement(MultipleAssignmentStatement mas) {
        StringBuilder builder = new StringBuilder();
        for (AssignmentStatement s : mas.getStatements()) {
            builder.append(toStringAssignmentStatement(s));
            builder.append("\n");
        }
        return builder.substring(0, builder.length() - 1);
    }

    private String toStringChainedAssignmentStatement(ChainedAssignmentStatement statement) {
        String assignments = statement.getTargets().stream()
                .map(this::toString)
                .collect(Collectors.joining(" = "))
                + " = " + toString(statement.getValue()) + ";";
        if (statement.getVariableDeclarations().isEmpty()) {
            return assignments;
        }
        String declarations = statement.getVariableDeclarations().stream()
                .map(this::toString)
                .collect(Collectors.joining("\n"));
        return declarations + "\n" + assignments;
    }

    private String toStringCompoundComparison(CompoundComparison cmpCmp) {
        StringBuilder expr = new StringBuilder();
        for (BinaryComparison cmp : cmpCmp.getComparisons()) {
            expr.append(toStringBinaryExpression(cmp));
            expr.append(" && ");
        }
        return expr.substring(0, expr.length() - 4);
    }

    private String toStringMemberAccess(MemberAccess memAccess) {
        // Константа перечисления квалифицируется в C++ через ::, а не через точку
        if (isEnumConstantAccess(memAccess)) {
            return String.format("%s::%s", toString(memAccess.getExpression()), toString(memAccess.getMember()));
        }

        String token = memAccess instanceof PointerMemberAccess || memAccess.getExpression() instanceof SelfReference
                ? "->"
                : ".";
        return String.format("%s%s%s",toString(memAccess.getExpression()), token, toString(memAccess.getMember()));
    }

    /**
     * Обращение вида {@code Color.RED}, где {@code Color} — видимое в текущей области
     * перечисление, а {@code RED} — его константа.
     */
    private boolean isEnumConstantAccess(MemberAccess memAccess) {
        if (!(memAccess.getExpression() instanceof SimpleIdentifier owner)) {
            return false;
        }
        return ctx.getScopeTable()
                .findDeclaration(owner, EnumDeclaration.class)
                .map(declaration -> ((EnumDeclaration) declaration).hasConstant(memAccess.getMember()))
                .orElse(false);
    }

    private String fromInterpolatedString(InterpolatedStringLiteral interpolatedStringLiteral) {
        StringBuilder builder = new StringBuilder("%sformat(\"".formatted(stdPrefix()));
        List<Expression> dynamicExprs = new ArrayList<>();
        for (Expression expr : interpolatedStringLiteral.components()) {
            if (expr instanceof StringLiteral str) {
                builder.append(str.getEscapedValue());
            } else {
                builder.append("{}");
                dynamicExprs.add(expr);
            }
        }
        builder.append('\"');
        if (!dynamicExprs.isEmpty()) {
            builder.append(", ");
            builder.append(toStringArguments(dynamicExprs));
        }
        builder.append(")");
        return builder.toString();
    }

    private String toStringDelete(DeleteExpression del) {
        if (isCMode()) {
            return "free(%s)".formatted(toString(del.getTarget()));
        }
        StringBuilder builder = new StringBuilder("delete");
        if (del.isCollectionTarget()) {
            builder.append("[]");
        }
        builder.append(' ');
        builder.append(toString(del.getTarget()));
        return builder.toString();
    }

    private String toStringNew(NewExpression _new) {
        if (isCMode()) {
            return toStringCAllocation(_new);
        }
        if (_new instanceof ArrayNewExpression arrayNew) {
            StringBuilder newBuilder = new StringBuilder();
            // DISABLED DUE TO RARE SYNTAX
            /*
            StringBuilder newBuilder = new StringBuilder("new ");
            newBuilder.append(toString(arrayNew.getType()));
            for (int i = 0; i < arrayNew.getShape().getDimensionCount(); i++) {
                newBuilder.append(String.format("[%s]", arrayNew.getShape().getDimension(i)));
            }
            */
            if (arrayNew.getInitializer() != null) {
                if (shouldUseHeapAllocation(arrayNew)) {
                    newBuilder.append("new ");
                    newBuilder.append(toString(arrayNew.getType()));
                    newBuilder.append(toString(arrayNew.getShape()));
                    newBuilder.append(' ');
                }
                newBuilder.append("{%s}".formatted(toStringArguments(arrayNew.getInitializer().getValues())));
            } else {
                newBuilder.append("new ");
                newBuilder.append(toString(arrayNew.getType()));
                for (int i = 0; i < arrayNew.getShape().getDimensionCount(); i++) {
                    Expression dimension = arrayNew.getShape().getDimension(i);
                    newBuilder.append("[%s]".formatted(dimension == null ? "" : toString(dimension)));
                }
            }
            return newBuilder.toString();
        } else if (_new instanceof PlacementNewExpression placementNew) {
            return String.format("new(%s) %s", toStringArguments(placementNew.getConstructorArguments()), toString(placementNew.getType()));
        } else if (_new instanceof ObjectNewExpression objectNew) {
            // Объект на стеке — это временный объект, а не выделение памяти: Box(1)
            String allocation = objectNew.isStackAllocated() ? "" : "new ";
            return String.format("%s%s(%s)", allocation, toString(objectNew.getType()),
                    toStringArguments(objectNew.getConstructorArguments()));
        } else {
            throw new UnsupportedViewingException("Unknown new expression");
        }
    }

    private String toStringCAllocation(NewExpression allocation) {
        if (allocation instanceof PlacementNewExpression) {
            throw new UnsupportedViewingException("Placement new is unavailable in C mode");
        }
        if (allocation instanceof ObjectNewExpression objectNew) {
            if (objectNew.isStackAllocated() || !objectNew.getConstructorArguments().isEmpty()) {
                throw new UnsupportedViewingException("C mode cannot lower object construction");
            }
            return "malloc(sizeof(%s))".formatted(toString(objectNew.getType()));
        }
        if (allocation instanceof ArrayNewExpression arrayNew) {
            if (arrayNew.getInitializer() != null) {
                throw new UnsupportedViewingException("C mode cannot lower initialized dynamic arrays");
            }
            if (arrayNew.getShape().getDimensionCount() != 1) {
                throw new UnsupportedViewingException("C mode can lower only one-dimensional dynamic arrays");
            }
            List<String> dimensions = new ArrayList<>();
            for (Expression dimension : arrayNew.getShape().getDimensions()) {
                if (dimension == null) {
                    throw new UnsupportedViewingException("Dynamic array dimensions must be known in C mode");
                }
                dimensions.add(toString(dimension));
            }
            return "malloc(sizeof(%s) * %s)".formatted(toString(arrayNew.getType()),
                    String.join(" * ", dimensions));
        }
        throw new UnsupportedViewingException("Unknown allocation in C mode");
    }

    private String toStringSizeof(SizeofExpression sizeof) {
        return String.format("sizeof(%s)", toString(sizeof.getExpression()));
    }

    private String toStringCast(CastTypeExpression cast) {
        return String.format("(%s) %s", toString(cast.getCastType()), toString(cast.getValue()));
    }

    private String toStringCollectionLiteral(PlainCollectionLiteral colLit) {
        return String.format("{%s}", toStringArguments(colLit.getList()));
    }

    private String toStringDictionaryLiteral(DictionaryLiteral dLit) {
        StringBuilder builder = new StringBuilder("{");
        for (Map.Entry<Expression, Expression> entry : dLit.getDictionary().entrySet()) {
            builder.append(String.format("{%s, %s}", toString(entry.getKey()), toString(entry.getValue())));
            builder.append(", ");
        }
        String result = !dLit.getDictionary().isEmpty() ? builder.substring(0, builder.length() - 2) : builder.toString();
        return result.concat("}");
    }

    private String toStringArguments(List<Expression> exprs) {
        return String.join(", ", exprs.stream().map(this::toString).toList());
    }

    private String toStringStringLiteral(StringLiteral literal) {
        return String.format("\"%s\"", literal.getEscapedValue());
    }

    private String toStringFloorDiv(FloorDivOp op) {
        return String.format("(long) (%s / %s)", toString(op.getLeft()), toString(op.getRight()));
    }

    @Nullable
    private MethodDefinition getMainFunction(List<Node> nodes) {
        for (var node : nodes) {
            if (node instanceof FunctionDefinition functionDefinition
                    && functionDefinition.getName().toString().equals("main")) {
                MethodDefinition method = functionDefinition.makeMethod(
                        null,
                        List.of(DeclarationModifier.PUBLIC, DeclarationModifier.STATIC)
                );
                // Обёртка живёт только на время отрисовки — размечаем её исходной функцией
                method.getDeclaration().remap(functionDefinition.getDeclaration());
                return method.remap(functionDefinition);
            }
        }

        return null;
    }

    private List<Node> getNotFunction(List<Node> nodes) {
        var notMethods = new ArrayList<Node>();

        for (var node : nodes) {
            // Подключения и объявление пакета уже выведены в шапке makeSimpleProgram: внутри
            // main они не только бессмысленны, но и продублировались бы
            if (!(node instanceof FunctionDefinition) && !isProgramHeaderNode(node)) {
                notMethods.add(node);
            }
        }

        return notMethods;
    }

    private boolean isProgramHeaderNode(Node node) {
        return node instanceof Import || node instanceof PackageDeclaration;
    }

    private String makeSimpleProgram(List<Node> nodes) {
        StringBuilder builder = new StringBuilder();

        // #include — директива препроцессора: внутри тела функции она бессмысленна, поэтому
        // подключения выносятся в шапку, а не идут в общий список тела
        var includes = ctx.viewingIterateBody(nodes.stream().filter(this::isProgramHeaderNode).toList());
        for (Node includeNode : includes) {
            includes.appendString(toString(includeNode));
        }
        if (!includes.stringBuffer().isEmpty()) {
            builder.append(String.join("\n", includes.stringBuffer())).append("\n");
        }

        // Определения функций не попадают ни в main, ни в notMethods, поэтому выводим их
        // отдельно перед сгенерированной точкой входа: иначе они бы просто потерялись
        for (Node node : nodes) {
            if (node instanceof FunctionDefinition functionDefinition
                    && !functionDefinition.getName().toString().equals("main")) {
                builder.append(toString(functionDefinition)).append("\n");
            }
        }

        builder.append(isCMode() ? "int main(void) {\n\n" : "int main(int argc, char * argv[]) {\n\n");
        increaseIndentLevel();

        var mainFunc = getMainFunction(nodes);
        var notMethods = getNotFunction(nodes);

        if (mainFunc != null) {
            // Добавляем все не-методы в body main
            var mainBody = mainFunc.getBody();
            for (Node node : notMethods) {
                mainBody.insert(mainBody.getLength(), node);
            }
            var constructor = ctx.viewingIterateBody(mainBody);
            for (var node : constructor) {
                constructor.appendString(indent(toString(node)));
            }
            builder.append(String.join("\n", constructor.stringBuffer())).append("\n");
        }
        else {
            var constructor = ctx.viewingIterateBody(notMethods);
            for (var node : constructor) {
                constructor.appendString(indent(toString(node)));
            }
            builder.append(String.join("\n", constructor.stringBuffer())).append("\n");
        }

        decreaseIndentLevel();
        builder.append("}\n");

        return builder.toString();
    }

    private String toStringEntryPoint(ProgramEntryPoint entryPoint) {
        if (isCMode() && requiresCStandardLibrary(entryPoint)) {
            // Отложенный импорт: preserveSystemInclude/withPreservedIncludes уже умеют не
            // дублировать подключение, если оно есть в исходной программе, — в отличие от
            // прежнего варианта, дописывавшего "#include <stdlib.h>" в шапку безусловно.
            preserveSystemInclude("stdlib.h", entryPoint);
        }
        // C++-написания Си-заголовков нормализуются до буферизации, а не после: дедупликация в
        // ImportBuffer сравнивает сырые имена файлов, и "cstdlib" рядом с "stdlib.h" (например,
        // отложенным preserveSystemInclude) не опознаются как один и тот же заголовок — оба
        // проходят, а на печати оба переписываются в одинаковую строку, давая дубликат.
        List<Node> nodes = entryPoint.getBody().stream().map(this::normalizeCHeaderSpelling).toList();
        // #include верхнего уровня уходят в тот же буфер, что и системные подключения,
        // отложенные по ходу отрисовки (preserveSystemInclude), — единая шапка с дедупликацией,
        // а не печать по месту вперемешку с добавленной сверху
        List<Node> bodyNodes = ctx.imports().bufferTopLevelImports(nodes);
        if (getConfigParameter("translationUnitMode").equalsValue("full") && !entryPoint.hasEntryPoint()) {
            return withPreservedIncludes(makeSimpleProgram(bodyNodes), bodyNodes);
        }

        var constructor = ctx.viewingIterateBody(bodyNodes);
        for (Node node : constructor) {
            constructor.appendString(toString(node));
        }
        String body = String.join("\n", constructor.stringBuffer());

        // Точка входа чужого языка (например, python def run(): ... под if __name__) не
        // содержит функции/метода буквально с именем main, поэтому её вызов никуда не попадает
        // при простом выводе body — C++ такую программу не запустить без настоящего main.
        if (getConfigParameter("translationUnitMode").equalsValue("full")
                && entryPoint.hasEntryPoint()
                && !hasOwnMainFunction(entryPoint.getEntryPoint())) {
            body = (body.isEmpty() ? "" : body + "\n") + synthesizeMainCallingEntryPoint(entryPoint.getEntryPoint());
        }

        return withPreservedIncludes(body, bodyNodes) + "\n";
    }

    private boolean hasOwnMainFunction(Node entryPointNode) {
        return entryPointNode instanceof FunctionDefinition fd && fd.getName().toString().equals("main");
    }

    /**
     * Строит {@code int main() { ... return 0; }}, вызывающий чужую точку входа: свободную
     * функцию без аргументов либо составной оператор (как тело {@code if __name__ == "__main__":}).
     */
    private String synthesizeMainCallingEntryPoint(Node entryPointNode) {
        StringBuilder builder = new StringBuilder();
        builder.append(isCMode() ? "int main(void)\n{\n" : "int main()\n{\n");
        increaseIndentLevel();

        if (entryPointNode instanceof FunctionDefinition functionDefinition) {
            FunctionCall call = new FunctionCall(functionDefinition.getName().clone()).remap(functionDefinition);
            builder.append(indent(toString(new ExpressionStatement(call).remap(functionDefinition)))).append("\n");
        } else if (entryPointNode instanceof CompoundStatement compound) {
            var constructor = ctx.viewingIterateBody(compound);
            for (var node : constructor) {
                constructor.appendString(indent(toString(node)));
            }
            builder.append(String.join("\n", constructor.stringBuffer())).append("\n");
        } else {
            builder.append(indent(toString(entryPointNode))).append("\n");
        }

        builder.append(indent("return 0;")).append("\n");
        decreaseIndentLevel();
        builder.append("}\n");
        return builder.toString();
    }

    /**
     * Дописывает шапку из подключений, о которых стало известно только по ходу отрисовки тела
     * (см. {@link #preserveSystemInclude}), — и только тех, которых в программе ещё нет.
     * <p>
     * В simple-режиме шапки не бывает вообще (тело — это просто содержимое main), поэтому
     * buffered #include в вывод не идут — но буфер всё равно дренируется (без печати
     * результата), иначе он утечёт в следующий рендер того же контекста.
     */
    private String withPreservedIncludes(String body, List<Node> nodes) {
        // Убираем ещё до печати, а не полагаемся на то, что toStringImport напечатает пустую
        // строку для непереводимого импорта, — иначе от него в шапке остаётся пустая строка
        pruneUnrenderableImports(this::isUnrenderableImport, body);
        if (getConfigParameter("translationUnitMode").equalsValue("simple")) {
            ctx.imports().flush();
            return body;
        }
        collapseHeaderSpellings();
        dropSupersededHeaders(body);
        if (usesDefaultNamespace()) {
            // Директива приписывается к телу до того, как сверху ляжет шапка включений:
            // prependPreserved строит шапку из буфера и ставит тело последним, поэтому
            // "using namespace std;" оказывается ровно между ними — и при пустом буфере тоже
            body = "using namespace std;\n\n" + body;
        }
        return ctx.imports().prependPreserved(body, nodes, "", this::toString);
    }

    /**
     * Оставляет один заголовок из тех, что подключены под разными написаниями: {@code <string.h>}
     * и {@code <cstring>} — один и тот же файл. Побеждает то написание, которое встретилось
     * раньше, то есть написание автора, а не дописанное нами.
     * <p>
     * Дедупликация в {@code ImportBuffer} сравнивает имена файлов буквально, и иначе нельзя:
     * знание о том, что два имени называют один заголовок, — это знание про C++, а буфер общий
     * для всех языков. В C-режиме то же расхождение снимает {@link #normalizeCHeaderSpelling},
     * приводя всё к Си-написанию ещё до буферизации; но в C++-режиме исходное написание автора
     * не переписывается, поэтому схлопывать приходится здесь, когда в буфере лежат уже и
     * подключения из программы, и дописанные по ходу отрисовки.
     */
    private void collapseHeaderSpellings() {
        Set<String> seen = new HashSet<>();
        Set<Import> duplicates = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Import buffered : ctx.imports().peek()) {
            if (!(buffered instanceof Include include)
                    || include.getIncludeType() != Include.IncludeType.POINTY_BRACKETS_FORM) {
                continue;
            }
            if (!seen.add(canonicalHeaderName(include))) {
                duplicates.add(buffered);
            }
        }
        if (!duplicates.isEmpty()) {
            ctx.imports().removeIf(duplicates::contains);
        }
    }

    /**
     * Убирает Си-заголовок, вытесненный C++-заголовком: средство, ради которого он подключался,
     * напечатано средством C++ из другого файла ({@code puts} — потоком из {@code <iostream>},
     * {@code char *} — {@code std::string} из {@code <string>}). Без этого оба подключения
     * попадают в шапку разом: вытесняющий заголовок откладывается по ходу отрисовки, а
     * вытесненный так и стоит в программе, хотя ничего из него в выводе уже нет.
     * <p>
     * Отношение «что чем вытесняется» само по себе выбрасывать подключение не разрешает: один
     * заголовок даёт много средств, и {@code <stdio.h>} рядом с потоками по-прежнему нужен,
     * если в выводе остался хоть один {@code fopen}. Поэтому решает не таблица, а напечатанный
     * код — как и в {@code requireDroppableImports}: заголовок уходит, только когда из него в
     * выводе не осталось ни одного имени. Состав заголовка известен не для всех — о заголовке,
     * которого нет в реестре, судить не по чему, и он остаётся на месте.
     * <p>
     * Неиспользованный заголовок без вытесняющего не трогается: его автор подключил сам, и
     * выбрасывать его не за что (см. {@code CppCModeTests.keepsCStandardHeaders}).
     */
    private void dropSupersededHeaders(String body) {
        Set<String> supersededHeaders = new HashSet<>();
        for (Import buffered : ctx.imports().peek()) {
            if (buffered instanceof Include include
                    && include.getIncludeType() == Include.IncludeType.POINTY_BRACKETS_FORM) {
                CppLibraryImportRegistry.headerSupersededBy(canonicalHeaderName(include))
                        .ifPresent(supersededHeaders::add);
            }
        }
        if (supersededHeaders.isEmpty()) {
            return;
        }
        Set<String> namesInCode = identifiersOf(body);
        ctx.imports().removeIf(buffered -> buffered instanceof Include include
                && include.getIncludeType() == Include.IncludeType.POINTY_BRACKETS_FORM
                && supersededHeaders.contains(canonicalHeaderName(include))
                && isHeaderUnreferenced(canonicalHeaderName(include), namesInCode));
    }

    /**
     * Ни одного имени заголовка в выводе не осталось.
     * <p>
     * Состав заголовка спрашивается у {@code CppStandardLibrary} — там же, где живёт всё
     * остальное знание про имена стандартной библиотеки. Она отвечает только про заголовки,
     * объявленные полными: у неполного описания пропущенное имя выглядело бы как отсутствие
     * обращений, и подключение выбрасывалось бы при живом вызове. Такой заголовок считается
     * используемым — судить о его ненужности не по чему.
     */
    private boolean isHeaderUnreferenced(String header, Set<String> namesInCode) {
        return CppLibraryImportRegistry.cppSpellingOf(header)
                .flatMap(unit -> languageBehavior().standardLibrary().namesOf(unit))
                .map(names -> names.stream().noneMatch(namesInCode::contains))
                .orElse(false);
    }

    /** Имена, встречающиеся в напечатанном коде, — по ним видно, что заголовок ещё нужен. */
    private static Set<String> identifiersOf(String code) {
        Set<String> identifiers = new HashSet<>();
        Matcher matcher = IDENTIFIER.matcher(code);
        while (matcher.find()) {
            identifiers.add(matcher.group());
        }
        return identifiers;
    }

    /** Си-написание как единственная форма заголовка: {@code cstring} и {@code string.h} — одно имя. */
    private static String canonicalHeaderName(Include include) {
        String fileName = include.getFileName().getUnescapedValue();
        return CppLibraryImportRegistry.cSpellingOf(fileName).orElse(fileName);
    }

    /**
     * Импорт, которому в C++ нечего соответствовать: C++-only заголовок (например,
     * {@code <string>} после того, как {@code std::string} превратился в {@code char *}) в
     * C-режиме, либо библиотечный импорт другого языка (Python {@code json} и т. п.), для
     * которого в {@link CppLibraryImportRegistry} нет заголовка. Метка "библиотечный" ставится
     * либо резолвером с контекстом проекта, либо сразу при разборе (см.
     * {@code JavaParser}/{@code PythonParser}), так что проверка работает и без него.
     */
    private boolean isUnrenderableImport(Import importNode) {
        if (isCMode() && isInvalidForCMode(importNode)) {
            return true;
        }
        return isLibraryImport(importNode) && libraryHeadersOf(importNode).isEmpty();
    }

    /**
     * Системное подключение, недопустимое в чистом Си. Проверяется положительно — по списку
     * Си-заголовков: перечислять C++-заголовки нельзя, их множество открытое, и всё
     * неперечисленное молча проезжало в C-вывод.
     * <p>
     * C++-написание Си-заголовка ({@code <cstdio>}) недопустимым не считается: оно переписывается
     * в Си-написание при печати, см. {@link #toStringInclude}.
     */
    private boolean isInvalidForCMode(Import importNode) {
        if (!(importNode instanceof Include include)
                || include.getIncludeType() != Include.IncludeType.POINTY_BRACKETS_FORM) {
            return false;
        }
        String header = include.getFileName().getUnescapedValue();
        return !CppLibraryImportRegistry.isCStandardHeader(header)
                && CppLibraryImportRegistry.cSpellingOf(header).isEmpty();
    }

    private boolean requiresCStandardLibrary(ProgramEntryPoint entryPoint) {
        return new MeaningTree(entryPoint).anyMatch(node -> node instanceof MemoryAllocationCall
                || node instanceof MemoryFreeCall
                || node instanceof NewExpression
                || node instanceof DeleteExpression
                || node instanceof DeleteStatement);
    }

    @NotNull
    private String toStringExpressionStatement(@NotNull ExpressionStatement expressionStatement) {
        if (expressionStatement.getExpression() == null) {
            return ";";
        }
        return toString(expressionStatement.getExpression()) + ";";
    }

    @NotNull
    private String toStringVariableDeclarator(@NotNull VariableDeclarator variableDeclarator, Type type) {
        return toStringVariableDeclarator(variableDeclarator, type, false);
    }

    @NotNull
    private String toStringVariableDeclarator(@NotNull VariableDeclarator variableDeclarator, Type type,
                                              boolean useHeapArrayAllocation) {
        String variableName = toString(variableDeclarator.getIdentifier());

        String arrayDeclarator = "";
        if (isCharBufferString(type)) {
            arrayDeclarator = "[%s]".formatted(toString(((StringType) type).getMaxLength()));
        } else if (!useHeapArrayAllocation && type instanceof ArrayType array) {
            StringBuilder builder = new StringBuilder();
            for (Expression expr : array.getShape().getDimensions()) {
                if (expr != null) {
                    builder.append(String.format("[%s]", toString(expr)));
                } else {
                    builder.append("[]");
                }
            }
            arrayDeclarator = builder.toString();
        }

        Expression rValue = variableDeclarator.getRValue();
        if (rValue == null) {
            return variableName.concat(arrayDeclarator);
        }

        if (!useHeapArrayAllocation && type instanceof ArrayType && rValue instanceof ArrayNewExpression arrayNew
                && arrayNew.getInitializer() == null) {
            return variableName.concat(toString(arrayNew.getShape()));
        }

        // Объект на стеке конструируется прямо в объявлении: Box a(1)
        if (rValue instanceof ObjectNewExpression objectNew
                && objectNew.isStackAllocated()
                && !(rValue instanceof PlacementNewExpression)) {
            if (objectNew.getConstructorArguments().isEmpty()) {
                return variableName.concat(arrayDeclarator);
            }
            return "%s%s(%s)".formatted(variableName, arrayDeclarator,
                    toStringArguments(objectNew.getConstructorArguments()));
        }

        return "%s%s = %s".formatted(variableName, arrayDeclarator, toString(rValue));
    }

    @NotNull
    private String toStringVariableDeclaration(@NotNull VariableDeclaration variableDeclaration) {
        StringBuilder builder = new StringBuilder();

        Type declarationType = variableDeclaration.getType();
        boolean useHeapArrayAllocation = usesHeapArrayAllocation(variableDeclaration);
        String type;
        if (isCharBufferString(declarationType)) {
            type = charElementSpelling((StringType) declarationType);
        } else if (!useHeapArrayAllocation && declarationType instanceof ArrayType array) {
            type = toString(array.getItemType());
        } else {
            type = useHeapArrayAllocation ? "auto*" : toString(declarationType);
        }
        builder
                .append(type)
                .append(" ");

        for (VariableDeclarator variableDeclarator : variableDeclaration.getDeclarators()) {
            builder.append(toStringVariableDeclarator(variableDeclarator, declarationType, useHeapArrayAllocation)).append(", ");
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

    private boolean usesHeapArrayAllocation(VariableDeclaration declaration) {
        if (isCMode() || !getConfigParameter("preferHeapAlloc").asBoolean()
                || !(declaration.getType() instanceof ArrayType)) {
            return false;
        }
        return Arrays.stream(declaration.getDeclarators()).allMatch(declarator ->
                declarator.getRValue() instanceof ArrayNewExpression arrayNew && shouldUseHeapAllocation(arrayNew));
    }

    private boolean shouldUseHeapAllocation(NewExpression newExpression) {
        return getConfigParameter("preferHeapAlloc").asBoolean() && !newExpression.isStackAllocated();
    }

    @NotNull
    private String toStringIndexExpression(@NotNull IndexExpression indexExpression) {
        String base = toString(indexExpression.getExpression());
        String indices = toString(indexExpression.getIndex());
        if (indexExpression.isPreferPointerRepresentation()) {
            return "*(%s + %s)".formatted(base, indices);
        } else {
            return "%s[%s]".formatted(base, indices);
        }
    }

    @NotNull
    private String toStringCommaExpression(@NotNull ExpressionSequence commaExpression) {
        StringBuilder builder = new StringBuilder();

        for (Expression expression : commaExpression.getExpressions()) {
            builder
                    .append(toString(expression))
                    .append(", ");
        }

        if (builder.length() > 1) {
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
        }

        return builder.toString();
    }

    @NotNull
    private String toStringTernaryOperator(@NotNull TernaryOperator ternaryOperator) {
        String condition = toString(ternaryOperator.getCondition());
        String then = toString(ternaryOperator.getThenExpr());
        String else_ = toString(ternaryOperator.getElseExpr());
        return "%s ? %s : %s".formatted(condition, then, else_);
    }

    @NotNull
    private String toStringFunctionCallArgumentsList(@NotNull List<Expression> arguments) {
        if (arguments.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();


        for (Expression argument : arguments) {
            builder
                    .append(toString(argument))
                    .append(", ");
        }

        if (builder.length() > 1) {
            builder.deleteCharAt(builder.length() - 1);
            builder.deleteCharAt(builder.length() - 1);
        }


        return builder.toString();
    }

    @NotNull
    private String toStringFunctionCall(@NotNull FunctionCall functionCall) {
        String functionName = toString(functionCall.getFunction());
        preserveStandardFunctionHeader(functionName, functionCall);
        return functionName + "(" + toStringFunctionCallArgumentsList(functionCall.getArguments()) + ")";
    }

    /**
     * Функции стандартной библиотеки узнаются по имени в точке вызова: отдельного узла под
     * каждую из них в дереве нет, а заводить его ради подключения заголовка — цена, которой
     * задача не стоит. Имя берётся уже отрисованное, поэтому квалифицированный вызов
     * ({@code std::sqrt}) сюда не попадёт — у него заголовок уже подключён вручную.
     * <p>
     * Имя, объявленное в самой программе, библиотечным не считается: своя {@code abs} или
     * {@code remove} — обычная функция автора, и заголовок под неё не подключается. Таблица
     * имён отвечает только за то, что даёт язык, а кто перекрыл это имя — знает таблица
     * областей видимости, и спросить её здесь ничего не стоит.
     */
    private void preserveStandardFunctionHeader(String functionName, Node origin) {
        if (isCMode() || isDeclaredInProgram(functionName)) {
            return;
        }
        languageBehavior().standardLibrary().unitFor(functionName)
                .ifPresent(header -> preserveSystemInclude(header, origin));
    }

    /**
     * Объявлена ли функция с таким именем в самой программе.
     * <p>
     * Проверка по имени, потому что и решение о заголовке принимается по имени: вызов
     * {@code pow} вообще приходит из {@code PowOp}, где узла-идентификатора нет.
     * <p>
     * Таблица отрисовки наполняется по ходу обхода, поэтому здесь видно то же, что видел бы
     * компилятор в этой точке: объявленное выше — видно, объявленное ниже — нет. Для C++ этого
     * достаточно, вызвать функцию до объявления там нельзя; и ошибка возможна только в
     * безопасную сторону — лишний {@code #include} вместо пропущенного.
     */
    private boolean isDeclaredInProgram(String functionName) {
        return ctx.getScopeTable()
                .findDeclaration(new SimpleIdentifier(functionName), FunctionDeclaration.class)
                .isPresent();
    }

    @NotNull
    private String toStringMethodCall(@NotNull MethodCall methodCall) {
        String token = methodCall.getObject() instanceof SelfReference ? "->" : ".";
        return "%s%s%s(%s)".formatted(
                toString(methodCall.getObject()),
                token,
                toString(methodCall.getFunctionName()),
                toStringFunctionCallArgumentsList(methodCall.getArguments())
        );
    }

    @NotNull
    private String toStringParenthesizedExpression(@NotNull ParenthesizedExpression parenthesizedExpression) {
        return "(" + toString(parenthesizedExpression.getExpression()) + ")";
    }

    @NotNull
    private String toStringAssignmentExpression(@NotNull AssignmentExpression assign) {
        Expression left = assign.getLValue();
        Expression right = assign.getRValue();
        AugmentedAssignmentOperator op = assign.getAugmentedOperator();

        // В С++ нет встроенного оператора возведения в степень, поэтому
        // используем функцию, необходимо убедится что подключен файл cmath: #include <cmath>
        if (op == POW) {
            return "%s = pow(%s, %s)".formatted(toString(left), toString(left), toString(right));
        }

        String o = switch (op) {
            case NONE -> "=";
            case ADD -> "+=";
            case SUB -> "-=";
            case MUL -> "*=";
            // В C++ тип деления определяется не видом операции, а типом операндов,
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

        String l = toString(left);
        String r = toString(right);

        if ((op == AugmentedAssignmentOperator.ADD || op == AugmentedAssignmentOperator.SUB) && r.equals("1")) {
            return (op == AugmentedAssignmentOperator.ADD) ? l + "++" : l + "--";
        }

        return "%s %s %s".formatted(l, o, r);
    }

    @NotNull
    /**
     * В C++ нет ни модулей в смысле Java/Python, ни импорта отдельных членов: единственная
     * доступная форма — подключение файла. Поэтому любой {@code Import} сводится к
     * {@code #include}, а всё, что богаче файла (список членов, алиас, static-семантика),
     * теряется — это ожидаемая деградация, а не ошибка.
     */
    private String toStringImport(Import importNode) {
        // Библиотечный импорт указывает не на файл проекта, а на стандартную библиотеку: путь
        // вида "math.h" был бы выдумкой. Если для модуля известен заголовок C++ — берём его,
        // иначе подключение исчезает: пусто честнее, чем ссылка на несуществующий файл
        if (isLibraryImport(importNode)) {
            return libraryHeadersOf(importNode);
        }
        return switch (importNode) {
            case Include include -> toStringInclude(include);
            // Импорт члена именует не модуль, а сущность в нём: путь к файлу собирается из
            // обоих имён, иначе подключился бы пакет, а не файл с этим членом
            case ImportMembersFromModule members -> members.getMembers().stream()
                    .map(member -> toStringLocalInclude(
                            dottedImportName(members.getModuleName()) + "." + dottedImportName(member)))
                    .collect(Collectors.joining("\n"));
            case ImportModules modules -> modules.getModulesNames().stream()
                    .map(module -> toStringLocalInclude(dottedImportName(module)))
                    .collect(Collectors.joining("\n"));
            case ImportModule module -> toStringLocalInclude(dottedImportName(module.getModuleName()));
            default -> throw new UnsupportedViewingException("Unexpected import type: " + importNode);
        };
    }

    /**
     * Импорт признан библиотечным резолвером. Без контекста проекта резолвер не запускался,
     * метаданных нет, и импорт остаётся наивным — см. {@code ImportResolver}.
     */
    private boolean isLibraryImport(Import importNode) {
        return !(importNode instanceof Include)
                && importNode.getResolverMetadata().map(ImportResolverMetadata::isLibrary).orElse(false);
    }

    private String libraryHeadersOf(Import importNode) {
        return candidateModuleNames(importNode).stream()
                .map(CppLibraryImportRegistry::headerForLibraryModule)
                .flatMap(Optional::stream)
                .distinct()
                .map("#include <%s>"::formatted)
                .collect(Collectors.joining("\n"));
    }

    private List<String> candidateModuleNames(Import importNode) {
        return switch (importNode) {
            case ImportMembersFromModule members -> {
                String module = dottedImportName(members.getModuleName());
                yield members.getMembers().stream()
                        .map(member -> module + "." + dottedImportName(member))
                        .toList();
            }
            case ImportModules modules -> modules.getModulesNames().stream().map(this::dottedImportName).toList();
            case ImportModule module -> List.of(dottedImportName(module.getModuleName()));
            default -> List.of();
        };
    }

    private String toStringInclude(Include include) {
        String fileName = include.getFileName().getUnescapedValue();
        if (include.getIncludeType() != Include.IncludeType.POINTY_BRACKETS_FORM) {
            return "#include \"%s\"".formatted(fileName);
        }
        // <cstdio> и <stdio.h> — один и тот же заголовок, но под первым именем в Си его нет.
        // Переписываем, а не выбрасываем: содержимое коду по-прежнему нужно.
        if (isCMode()) {
            fileName = CppLibraryImportRegistry.cSpellingOf(fileName).orElse(fileName);
        }
        return "#include <%s>".formatted(fileName);
    }

    /**
     * Откладывает {@code #include <...>} из стандартной библиотеки: о том, что он нужен,
     * становится известно только по ходу отрисовки — когда встретился тип или вызов, которому
     * он требуется. Шапку допишет точка входа.
     */
    private void preserveSystemInclude(String header, Node origin) {
        ctx.imports().preserveImport(new Include(
                (StringLiteral) StringLiteral.fromUnescaped(header, StringLiteral.Type.NONE).remap(origin),
                Include.IncludeType.POINTY_BRACKETS_FORM
        ).remap(origin));
    }

    /**
     * Приводит C++-написание Си-заголовка ({@code <cstdlib>}) к Си-написанию ({@code <stdlib.h>})
     * до того, как узел уйдёт в {@code ImportBuffer}: дедупликация там сравнивает сырые имена
     * файлов и не опознаёт два написания одного заголовка как один и тот же импорт (см.
     * {@link #preserveSystemInclude}, который откладывает уже Си-написание). Печать через
     * {@link #toStringInclude} после этой нормализации становится no-op на том же реестре.
     */
    private Node normalizeCHeaderSpelling(Node node) {
        if (!isCMode() || !(node instanceof Include include)
                || include.getIncludeType() != Include.IncludeType.POINTY_BRACKETS_FORM) {
            return node;
        }
        Optional<String> cSpelling = CppLibraryImportRegistry.cSpellingOf(include.getFileName().getUnescapedValue());
        if (cSpelling.isEmpty()) {
            return node;
        }
        Include normalized = new Include(
                (StringLiteral) StringLiteral.fromUnescaped(cSpelling.get(), StringLiteral.Type.NONE).remap(include),
                Include.IncludeType.POINTY_BRACKETS_FORM
        ).remap(include);
        include.getResolverMetadata().ifPresent(normalized::setResolverMetadata);
        return normalized;
    }

    private String toStringLocalInclude(String dottedName) {
        return "#include \"%s\"".formatted(ImportPathConverter.dottedNameToHeaderPath(dottedName));
    }

    /**
     * Пакета в C++ нет, поэтому объявление пакета просто исчезает: бросать исключение здесь
     * значило бы, что любой Java-файл с {@code package} нельзя перевести в C++.
     */
    private String toStringPackageDeclaration(PackageDeclaration declaration) {
        return "";
    }

    /**
     * Алиас импорта ({@code import x as y}) в C++ невыразим — берётся настоящее имя.
     */
    private Identifier unwrapAlias(Identifier identifier) {
        return identifier instanceof Alias alias ? alias.getRealName() : identifier;
    }

    private String dottedImportName(Identifier identifier) {
        return toString(unwrapAlias(identifier));
    }

    /** Имя из std, у которого под {@code using namespace std} снимается квалификация. */
    private boolean isStdScope(@NotNull QualifiedIdentifier qualified) {
        return usesDefaultNamespace()
                && qualified.getScope() instanceof SimpleIdentifier scope
                && scope.getName().equals("std");
    }

    /**
     * Имя пользовательского типа: цепочка вложенности печатается через {@code ::}, даже если
     * парсер исходного языка собрал её как {@link ScopedIdentifier}. В C++ {@code ScopedIdentifier}
     * — это доступ к членам ({@code a.b.c}), а квалификация имени типа пишется через {@code ::},
     * поэтому вложенный класс из Java/Python нельзя печатать общим кодом для идентификаторов.
     */
    private String toStringUserTypeName(@NotNull UserType type) {
        if (type.getQualifiedName() instanceof ScopedIdentifier scoped) {
            return String.join("::", scoped.getScopeResolution().stream().map(this::toStringIdentifier).toList());
        }
        return toString(type.getQualifiedName());
    }

    private String toStringIdentifier(@NotNull Identifier identifier) {
        return switch (identifier) {
            case SimpleIdentifier simpleIdentifier -> simpleIdentifier.getName();
            case ScopedIdentifier scopedIdentifier -> String.join(".", scopedIdentifier.getScopeResolution().stream().map(this::toStringIdentifier).toList());
            case QualifiedIdentifier qualifiedIdentifier -> {
                // Под using namespace std авторский std::sqrt печатается голым именем наравне
                // с теми std-именами, что генератор вставляет сам, — иначе в одном тексте
                // соседствовали бы два написания одного и того же
                if (isStdScope(qualifiedIdentifier)) {
                    yield this.toStringIdentifier(qualifiedIdentifier.getMember());
                }
                yield String.format("%s::%s", this.toStringIdentifier(qualifiedIdentifier.getScope()), this.toStringIdentifier(qualifiedIdentifier.getMember()));
            }
            default -> throw new IllegalStateException("Unexpected value: " + identifier);
        };
    }

    @NotNull
    private String toStringIntType(@NotNull IntType intType) {
        String prefix = intType.isUnsigned ? "unsigned" : "";
        
        String type = switch (intType.size) {
            case 8 -> "char";
            case 16 -> "short";
            case 32 -> "int";
            case 64 -> "long";
            default -> throw new IllegalStateException("Unexpected value: " + intType.size);
        };

        if (prefix.isEmpty()) {
            return type;
        }

        return prefix + " " + type;
    }

    @NotNull
    private String toStringFloatType(@NotNull FloatType floatType) {
        return switch (floatType.size) {
            case 32 -> "float";
            case 64 -> "double";
            default -> throw new IllegalStateException("Unexpected value: " + floatType.size);
        };
    }

    @NotNull
    private String toStringCharacterType(@NotNull CharacterType characterType) {
        return switch (characterType.size) {
            case 8 -> "char";
            case 16 -> "char16_t";
            default -> throw new IllegalStateException("Unexpected value: " + characterType.size);
        };
    }

    @NotNull
    private String toStringType(@NotNull Type type) {
        String initialType = switch (type) {
            case IntType intType -> toStringIntType(intType);
            case FloatType floatType -> toStringFloatType(floatType);
            case CharacterType characterType -> toStringCharacterType(characterType);
            case BooleanType booleanType -> "bool";
            case NoReturn voidType -> "void";
            case UnknownType unknown -> "auto";
            case PointerType ptr -> {
                if (ptr.getTargetType() instanceof UnknownType) {
                    yield "void *";
                }
                if (type.isConst()) {
                    yield String.format("%s * const", toStringType(ptr.getTargetType()));
                }
                yield String.format("%s *", toStringType(ptr.getTargetType()));
            }
            case ReferenceType ref ->  {
                if (type.isConst()) {
                    yield String.format("%s & const", toStringType(ref.getTargetType()));
                }
                yield String.format("%s &", toStringType(ref.getTargetType()));
            }
            case UnorderedDictionaryType dct -> cCollectionType(String.format("%sunordered_map<%s, %s>",
                    stdPrefix(), toStringType(dct.getKeyType()), toStringType(dct.getValueType())), dct);
            case DictionaryType dct -> cCollectionType(String.format("%smap<%s, %s>",
                    stdPrefix(), toStringType(dct.getKeyType()), toStringType(dct.getValueType())), dct);
            case ArrayType array -> {
                if (isCMode()) {
                    throw new UnsupportedViewingException("C array types require a declarator");
                }
                yield cCollectionType(String.format("%sarray<%s>", stdPrefix(), toStringType(array.getItemType())), array);
            }
            case UnmodifiableListType array -> cCollectionType(String.format("%sarray<%s>", stdPrefix(), toStringType(array.getItemType())), array);
            case SetType set -> cCollectionType(String.format("%sset<%s>", stdPrefix(), toStringType(set.getItemType())), set);
            case PlainCollectionType lst -> cCollectionType(String.format("%svector<%s>", stdPrefix(), toStringType(lst.getItemType())), lst);
            case StringType str -> isCStyleString(str)
                    ? "%s *".formatted(charElementSpelling(str))
                    : cCollectionType(stdPrefix() + "string", str);
            case GenericUserType gusr -> String.format("%s<%s>", toStringUserTypeName(gusr), toStringArguments(List.of(gusr.getTypeParameters())));
            case UserType usr -> toStringUserTypeName(usr);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
        if (type.isConst() && !(type instanceof ReferenceType) && !(type instanceof PointerType)
                && !(type instanceof StringType str && isCStyleString(str))) {
            return "const ".concat(initialType);
        }
        return initialType;
    }

    /**
     * Печатать ли строку в Си-написании ({@code char *}), а не как {@code std::string}.
     * <p>
     * Признак приходит с самим типом, а не только из режима генерации: строка, распознанная
     * {@code CStringTypeInferrer} в исходном тексте на C++, окружена вызовами
     * {@code <string.h>}, и {@code std::string} на её месте попросту не собрался бы.
     */
    private boolean isCStyleString(@NotNull StringType type) {
        return type.isCStyleString() || isCMode();
    }

    /**
     * Строка в буфере фиксированного размера печатается объявителем массива ({@code char buf[64]}),
     * как и обычный массив: размер в Си стоит при имени, а не при типе.
     */
    private boolean isCharBufferString(@NotNull Type type) {
        return type instanceof StringType str && str.hasMaxLength() && isCStyleString(str);
    }

    @NotNull
    private String charElementSpelling(@NotNull StringType type) {
        String element = type.getCharSize() > 8 ? "char16_t" : "char";
        return type.isConst() ? "const ".concat(element) : element;
    }

    /**
     * Тип из std (коллекция или строка) печатается только вместе с заголовком, который его
     * объявляет, поэтому заголовок откладывается здесь — в единственной точке, где
     * {@code std::}-форма типа действительно возникает. Массив с декларатором ({@code int a[3]})
     * сюда не попадает, и лишнего {@code <array>} у него не появится.
     */
    private String cCollectionType(String cppType, Type type) {
        if (isCMode()) {
            throw new UnsupportedViewingException("C mode supports arrays and char * but not std types");
        }
        CppLibraryImportRegistry.headerForType(type)
                .ifPresent(header -> preserveSystemInclude(header, type));
        return cppType;
    }

    private boolean isCMode() {
        return getConfigParameter("preferC").asBoolean();
    }

    /**
     * Печатаются ли ссылки указателями. В режиме Си это единственный способ их выразить, но
     * настройка от режима не зависит: включать её в C++ — осознанный выбор стиля, а не
     * вынужденная мера, и выключенной она оставляет ссылки в Си неподдерживаемыми.
     */
    private boolean representsReferencesAsPointers() {
        return getConfigParameter("representReferencesAsPointers").asBoolean();
    }

    /**
     * Печатается ли программа под {@code using namespace std;} — тогда префикс {@code std::}
     * не выводится нигде: ни у имён, вставленных самим генератором, ни у квалифицированных
     * идентификаторов, пришедших из дерева.
     * <p>
     * Режим проверяется здесь, а не только в точке печати директивы: снять префикс там, где
     * директиве негде встать, значит выдать несобираемый текст. Одно решение — один предикат.
     */
    private boolean usesDefaultNamespace() {
        return !isCMode()
                && getConfigParameter("useDefaultNamespace").asBoolean()
                && hasProgramHeader();
    }

    /**
     * Есть ли у программы шапка, куда встанет директива. В {@code simple} и {@code expression}
     * её нет: тело — это содержимое main, и {@code using namespace std;} оказался бы внутри
     * функции или вовсе вне программы.
     */
    private boolean hasProgramHeader() {
        var mode = getConfigParameter("translationUnitMode");
        return mode.equalsValue("full") || mode.equalsValue("procedural");
    }

    /** Префикс пространства имён для имени из std: пустой под {@code using namespace std}. */
    private String stdPrefix() {
        return usesDefaultNamespace() ? "" : "std::";
    }

    @NotNull
    private String toStringNumericLiteral(@NotNull NumericLiteral numericLiteral) {
        if (numericLiteral instanceof FloatLiteral floatLiteral) {
            return floatLiteral.getStringValue(true);
        }

        IntegerLiteral integerLiteral = (IntegerLiteral) numericLiteral;
        String result = integerLiteral.getStringValue(false);
        if (integerLiteral.isUnsigned()) {
            result = result.concat("U");
        }
        if (integerLiteral.isLong()) {
            result = result.concat("L");
        }
        return result;
    }

    @NotNull
    private String toStringUnaryExpression(@NotNull UnaryExpression unaryExpression) {
        if (unaryExpression instanceof NotOp notOp
                && notOp.getArgument() instanceof ParenthesizedExpression p
                && p.getExpression() instanceof InstanceOfOp op) {
            return String.format("dynamic_cast<%s>(%s) == nullptr", toString(op.getRight()), toString(op.getLeft()));
        }

        String operator = switch (unaryExpression) {
            case NotOp op -> "!";
            case InversionOp op -> "~";
            case UnaryMinusOp op -> "-";
            case UnaryPlusOp op -> "+";
            case PostfixIncrementOp op -> "++";
            case PrefixIncrementOp op -> "++";
            case PostfixDecrementOp op -> "--";
            case PrefixDecrementOp op -> "--";
            case PointerPackOp op -> "&";
            case PointerUnpackOp op -> "*";
            default -> throw new IllegalStateException("Unexpected value: " + unaryExpression);
        };

        if (unaryExpression instanceof PostfixDecrementOp
                || unaryExpression instanceof PostfixIncrementOp) {
            return toString(unaryExpression.getArgument()) + operator;
        }
        return operator + toString(unaryExpression.getArgument());
    }

    @NotNull
    private String toStringBinaryExpression(@NotNull BinaryExpression binaryExpression) {
        if (binaryExpression instanceof PowOp) {
            preserveStandardFunctionHeader("pow", binaryExpression);
            return String.format("pow(%s, %s)", toString(binaryExpression.getLeft()), toString(binaryExpression.getRight()));
        } else if (binaryExpression instanceof MatMulOp) {
            // Ни оператора, ни функции matmul в C++ нет: вызов matmul(a, b) не собрался бы,
            // а молча выданный несуществующий вызов хуже явного отказа
            throw new UnsupportedViewingException("C++ has no matrix multiplication operator or standard function");
        } else if (binaryExpression instanceof ContainsOp op) {
            String neg = op.isNegative() ? "!" : "";
            String left = toString(op.getRight());
            if (!(op.getRight() instanceof Identifier)) {
                left = "(".concat(left).concat(")");
            }
            return neg.concat(String.format("%s.contains(%s)", left, toString(op.getLeft())));
        } else if (binaryExpression instanceof ReferenceEqOp op) {
            String neg = op.isNegative() ? "!=" : "==";
            return String.format("%s %s %s",
                    toString(new PointerPackOp(op.getLeft()).remap(op.getLeft())), neg,
                    toString(new PointerPackOp(op.getRight()).remap(op.getRight())));
        } else if (binaryExpression instanceof InstanceOfOp op) {
            return String.format("dynamic_cast<%s>(%s) != nullptr", toString(op.getType()), toString(op.getLeft()));
        } else if (binaryExpression instanceof FloorDivOp op) {
            return String.format("(long) (%s / %s)", toString(op.getLeft()), toString(op.getRight()));
        }

        Expression left = binaryExpression.getLeft();
        Expression right = binaryExpression.getRight();

        String operator = switch (binaryExpression) {
            case AddOp op -> "+";
            case SubOp op -> "-";
            case MulOp op -> "*";
            case DivOp op -> "/";
            case LtOp op -> "<";
            case GtOp op -> ">";
            case NotEqOp op -> "!=";
            case GeOp op -> ">=";
            case LeOp op -> "<=";
            case ShortCircuitAndOp op -> "&&";
            case ShortCircuitOrOp op -> "||";
            case BitwiseAndOp op -> "&";
            case BitwiseOrOp op -> "|";
            case XorOp op -> "^";
            case LeftShiftOp op -> "<<";
            case RightShiftOp op -> ">>";
            case EqOp op -> "==";
            case ModOp op -> "%";
            case ThreeWayComparisonOp op -> "<=>";
            default -> throw new IllegalStateException("Unexpected value: " + binaryExpression);
        };

        return "%s %s %s".formatted(
                toString(left),
                operator,
                toString(right)
        );
    }

    @Override
    public OperatorToken mapToToken(Expression expr) {
        return ctx.requireTokenizer().getOperatorByNode(expr);
    }
}
