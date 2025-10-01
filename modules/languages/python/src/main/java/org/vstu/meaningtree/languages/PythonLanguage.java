package org.vstu.meaningtree.languages;

import org.treesitter.*;
import org.vstu.meaningtree.MeaningTree;
import org.vstu.meaningtree.exceptions.UnsupportedParsingException;
import org.vstu.meaningtree.languages.configs.params.ExpressionMode;
import org.vstu.meaningtree.languages.configs.params.SkipErrors;
import org.vstu.meaningtree.languages.utils.PythonSpecificFeatures;
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
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.comparison.*;
import org.vstu.meaningtree.nodes.expressions.comprehensions.Comprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.ContainerBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.comprehensions.RangeBasedComprehension;
import org.vstu.meaningtree.nodes.expressions.identifiers.ScopedIdentifier;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.NotOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitAndOp;
import org.vstu.meaningtree.nodes.expressions.logical.ShortCircuitOrOp;
import org.vstu.meaningtree.nodes.expressions.math.*;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryMinusOp;
import org.vstu.meaningtree.nodes.expressions.unary.UnaryPlusOp;
import org.vstu.meaningtree.nodes.io.PrintValues;
import org.vstu.meaningtree.nodes.modules.*;
import org.vstu.meaningtree.nodes.statements.*;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.BasicCaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.CaseBlock;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.conditions.components.DefaultCaseBlock;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.statements.loops.control.BreakStatement;
import org.vstu.meaningtree.nodes.statements.loops.control.ContinueStatement;
import org.vstu.meaningtree.nodes.types.GenericUserType;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.UserType;
import org.vstu.meaningtree.nodes.types.builtin.FloatType;
import org.vstu.meaningtree.nodes.types.builtin.IntType;
import org.vstu.meaningtree.nodes.types.builtin.StringType;
import org.vstu.meaningtree.nodes.types.containers.DictionaryType;
import org.vstu.meaningtree.nodes.types.containers.ListType;
import org.vstu.meaningtree.nodes.types.containers.SetType;
import org.vstu.meaningtree.nodes.types.containers.UnmodifiableListType;
import org.vstu.meaningtree.nodes.types.user.Class;
import org.vstu.meaningtree.utils.type_inference.HindleyMilner;
import org.vstu.meaningtree.utils.type_inference.TypeScope;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Stream;

public class PythonLanguage extends LanguageParser {
    private TSLanguage _language;
    private TSParser _parser;

    private TypeScope scope = new TypeScope();

    @Override
    public TSTree getTSTree() {
        _initBackend();

        /*
        TODO: only for test
        try {
            tree.printDotGraphs(new File("TSTree.dot"));
        } catch (IOException e) { }
        */

        return _parser.parseString(null, _code);
    }

    private void _initBackend() {
        if (_language == null) {
            _language = new TreeSitterPython();
            _parser = new TSParser();
            _parser.setLanguage(_language);
        }
    }

    @Override
    public synchronized MeaningTree getMeaningTree(String code) {
        scope = new TypeScope();
        _code = code;
        TSNode rootNode = getRootNode();
        List<String> errors = lookupErrors(rootNode);
        if (!errors.isEmpty() && !getConfigParameter(SkipErrors.class).orElse(false)) {
            throw new UnsupportedParsingException(String.format("Given code has syntax errors: %s", errors));
        }
        return new MeaningTree(parseTSNode(rootNode));
    }

    protected Node fromTSNode(TSNode node) {
        if (node.isNull()) {
            return null;
        }
        String nodeType = node.getType();
        Node createdNode = switch (nodeType) {
            case "ERROR" -> fromTSNode(node.getChild(0));
            case "module" -> createEntryPoint(node);
            case "block" -> fromCompoundTSNode(node);
            case "if_statement" -> fromIfStatementTSNode(node);
            case "expression_statement", "expression_list", "tuple_pattern" -> fromExpressionSequencesTSNode(node);
            case "parenthesized_expression" -> fromParenthesizedExpressionTSNode(node);
            case "binary_operator" -> fromBinaryExpressionTSNode(node);
            case "unary_operator" -> fromUnaryExpressionTSNode(node);
            case "not_operator" -> fromNotOperatorTSNode(node);
            case "pass_statement", "ellipsis" -> fromPassStatementOrEllipsis(node);
            case "integer" -> fromIntegerLiteralTSNode(node);
            case "float" -> fromFloatLiteralTSNode(node);
            case "identifier" -> fromIdentifier(node);
            case "keyword_argument" -> fromDefinitionArgument(node);
            case "delete_statement" -> new DeleteStatement((Expression) fromTSNode(node.getChild(0)));
            case "comparison_operator" -> fromComparisonTSNode(node);
            case "list", "set", "tuple" -> fromList(node, node.getType());
            case "dictionary" -> fromDictionary(node);
            case "string" -> fromString(node);
            case "interpolation" -> fromTSNode(node.getNamedChild(0));
            case "slice" -> fromSlice(node);
            case "for_statement" -> fromForLoop(node);
            case "class_definition" -> fromClass(node);
            case "comment" -> fromComment(node);
            case "boolean_operator" -> fromBooleanOperatorTSNode(node);
            case "none" -> new NullLiteral();
            case "type" -> determineType(node);
            case "list_splat" -> DefinitionArgument.listUnpacking((Expression) fromTSNode(node.getNamedChild(0)));
            case "dictionary_splat" -> DefinitionArgument.dictUnpacking((Expression) fromTSNode(node.getNamedChild(0)));
            case "true" -> new BoolLiteral(true);
            case "false" -> new BoolLiteral(false);
            case "call" -> fromFunctionCall(node);
            case "break_statement" -> new BreakStatement();
            case "continue_statement" -> new ContinueStatement();
            case "subscript" -> fromIndexTSNode(node);
            case "dotted_name" -> fromDottedNameTSNode(node);
            case "aliased_import" -> new Alias((Identifier)fromTSNode(node.getChildByFieldName("name")), (SimpleIdentifier) fromTSNode(node.getChildByFieldName("alias")));
            case "import_statement", "import_from_statement" -> fromImportNodes(node);
            case "attribute"-> fromAttributeTSNode(node);
            case "return_statement" -> fromReturnTSNode(node);
            case "conditional_expression"-> fromTernaryOperatorTSNode(node);
            case "named_expression" -> fromAssignmentExpressionTSNode(node);
            case "assignment", "augmented_assignment" -> fromAssignmentStatementTSNode(node);
            case "function_definition" -> fromFunctionTSNode(node);
            case "decorated_definition" -> detectAnnotated(node);
            case "while_statement" -> fromWhileLoop(node);
            case "assert_statement" -> fromAssertTSNode(node);
            case "set_comprehension", "dictionary_comprehension", "list_comprehension", "generator_expression" -> fromComprehension(node);
            case "match_statement" -> fromMatchStatement(node);
            case null, default -> throw new UnsupportedParsingException(String.format("Can't parse %s", node.getType()));
        };
        assignValue(node, createdNode);
        return createdNode;
    }

    @Override
    public MeaningTree getMeaningTree(TSNode node, String code) {
        _code = code;
        return new MeaningTree(parseTSNode(node));
    }

    private Node fromAssertTSNode(TSNode node) {
        return new FunctionCall(new SimpleIdentifier("assert"), (Expression)
                parseTSNode(node.getNamedChild(0)));
    }

    private void rollbackContext() {
        scope.leave();
    }

    private EmptyStatement fromPassStatementOrEllipsis(TSNode node) {
        return new EmptyStatement();
    }

    private Node fromComprehension(TSNode node) {
        TSNode body = node.getChildByFieldName("body");
        Comprehension.ComprehensionItem item;
        if (body.getType().equals("pair")) {
            item = new KeyValuePair(
                    (Expression) parseTSNode(body.getChildByFieldName("key")),
                    (Expression) parseTSNode(body.getChildByFieldName("value")));
        } else {
            if (node.getType().equals("set_comprehension")) {
                item = new Comprehension.SetItem((Expression) parseTSNode(body));
            } else {
                item = new Comprehension.ListItem((Expression) parseTSNode(body));
            }
        }
        body = body.getNextNamedSibling();
        Expression condition = null;
        TSNode for_clause = null;

        while (!body.isNull()) {
            if (body.getType().equals("if_clause")) {
                condition = (Expression) parseTSNode(body.getNamedChild(0));
            } else if (body.getType().equals("for_in_clause")) {
                for_clause = body;
            }
            body = body.getNextNamedSibling();
        }

        SimpleIdentifier leftOfForEach = (SimpleIdentifier) parseTSNode(for_clause.getChildByFieldName("left"));
        Expression rightOfForEach = (Expression) parseTSNode(for_clause.getChildByFieldName("right"));
        if (rightOfForEach instanceof FunctionCall call) {
            Range range = rangeFromFunction(call);
            if (range != null) {
                return new RangeBasedComprehension(
                        item,
                        leftOfForEach,
                        range,
                        condition
                );
            }
        }
        return new ContainerBasedComprehension(item, new VariableDeclaration(new UnknownType(), leftOfForEach), rightOfForEach, condition);
    }

    private Node fromMatchStatement(TSNode node) {
        Expression target = (Expression) parseTSNode(node.getChildByFieldName("subject"));
        node = node.getChildByFieldName("body");
        List<CaseBlock> branches = new ArrayList<>();
        DefaultCaseBlock defaultBranch = null;
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            TSNode alternative = node.getNamedChild(i);
            Expression condition;
            VariableDeclaration newDecl = null;
            if (alternative.getNamedChild(0).getNamedChildCount() == 0) {
                defaultBranch = new DefaultCaseBlock(
                        (Statement) parseTSNode(alternative.getChildByFieldName("consequence"))
                );
                continue;
            } else if (alternative.getNamedChild(0).getNamedChild(0).getType().equals("as_pattern")) {
                condition = (Expression) parseTSNode(alternative.getNamedChild(0).getNamedChild(0).getNamedChild(0).getNamedChild(0));
                SimpleIdentifier ident = (SimpleIdentifier) parseTSNode(alternative.getNamedChild(0).getNamedChild(0).getNamedChild(1));
                Type variableType = HindleyMilner.inference(condition, scope);
                newDecl = new VariableDeclaration(variableType, ident, condition);
            } else {
                condition = (Expression) parseTSNode(alternative.getNamedChild(0).getNamedChild(0));
            }
            CompoundStatement compoundStatement = (CompoundStatement) parseTSNode(alternative.getChildByFieldName("consequence"));
            if (newDecl != null) {
                compoundStatement.insert(0, newDecl);
            }
            branches.add(new BasicCaseBlock(condition, compoundStatement));
        }
        return new SwitchStatement(target, branches, defaultBranch);
    }

    private Node detectAnnotated(TSNode node) {
        TSNode definition = node.getChildByFieldName("definition");
        if (definition.getType().equals("class_definition")) {
            return fromClass(definition);
        } else {
            return fromFunctionTSNode(node);
        }
    }

    private DefinitionArgument fromDefinitionArgument(TSNode node) {
        SimpleIdentifier ident = (SimpleIdentifier) fromTSNode(node.getChildByFieldName("name"));
        Expression expr = (Expression) fromTSNode(node.getChildByFieldName("value"));
        return new DefinitionArgument(ident, expr);
    }

    private Node fromImportNodes(TSNode node) {
        if (node.getType().equals("import_statement")) {
            List<Identifier> scopes = new ArrayList<>();

            TSNode currentChild = node.getChildByFieldName("name");
            while (currentChild != null && !currentChild.isNull()) {
                scopes.add((Identifier) parseTSNode(currentChild));
                currentChild = currentChild.getNextNamedSibling();
            }
            if (scopes.size() == 1) {
                return new ImportModule(scopes.getFirst());
            } else {
                return new ImportModules(scopes);
            }
        } else if (node.getType().equals("import_from_statement")) {
            Identifier scope = (Identifier) parseTSNode(node.getChildByFieldName("module_name"));
            if (node.getNamedChild(1).getType().equals("wildcard_import")) {
                return new ImportAllFromModule(scope);
            }
            List<Identifier> members = new ArrayList<>();
            TSNode currentChild = node.getChildByFieldName("name");
            while (currentChild != null && !currentChild.isNull()) {
                members.add((Identifier) parseTSNode(currentChild));
                currentChild = currentChild.getNextNamedSibling();
            }
            return new ImportMembersFromModule(scope, members);
        } else {
            return parseTSNode(node);
        }
    }

    private Range rangeFromFunction(FunctionCall call) {
        if (call.hasFunctionName()
                && PythonSpecificFeatures.getFunctionName(call).equals(new SimpleIdentifier("range"))
                && !call.getArguments().isEmpty()
                && call.getArguments().size() <= 3) {
            Expression start = null, stop, step = null;
            List<Expression> exprs = call.getArguments();
            switch (exprs.size()) {
                case 1 -> stop = exprs.get(0);
                case 2 -> {
                    start = exprs.get(0);
                    stop = exprs.get(1);
                }
                default -> {
                    start = exprs.get(0);
                    stop = exprs.get(1);
                    step = exprs.get(2);
                }
            }
            Range.Type rangeType = Range.Type.UNKNOWN;
            if (step instanceof IntegerLiteral intLit) {
                rangeType = intLit.getLongValue() < 0 ? Range.Type.DOWN : Range.Type.UP;
            }
            return new Range(start, stop, step, false, true, rangeType);
        }
        return null;
    }

    private Node fromFunctionCall(TSNode node) {
        TSNode tsNode = node.getChildByFieldName("function");
        Expression name = (Expression) parseTSNode(tsNode);

        List<Expression> exprs = new ArrayList<>();
        TSNode arguments = node.getChildByFieldName("arguments");
        for (int i = 0; i < arguments.getNamedChildCount(); i++) {
            String tsNodeChildType = arguments.getNamedChild(i).getType();
            if (tsNodeChildType.equals("(") || tsNodeChildType.equals(")") || tsNodeChildType.equals(",") || tsNodeChildType.equals("comment")) {
                continue;
            }
            Expression expr = (Expression) parseTSNode(arguments.getNamedChild(i));
            exprs.add(expr);
        }

        if (getCodePiece(tsNode).equals("print")) {
            return new PrintValues.PrintValuesBuilder()
                    .endWithNewline()
                    .separateBySpace()
                    .setValues(exprs)
                    .build();
        }

        if (getCodePiece(tsNode).equals("isinstance") && exprs.size() == 2) {
            Type type = determineType(arguments.getNamedChild(1));
            return new InstanceOfOp(exprs.getFirst(), type);
        } else if (getCodePiece(tsNode).equals("matmul") && exprs.size() == 2) {
            return new MatMulOp(exprs.getFirst(), exprs.get(1));
        }

        if (name instanceof ScopedIdentifier scoped && scoped.getScopeResolution().size() > 1) {
            List<SimpleIdentifier> object = scoped.getScopeResolution()
                    .subList(0, scoped.getScopeResolution().size() - 1);
            return new MethodCall(object.size() == 1 ? object.getFirst() : new ScopedIdentifier(object)
                    , scoped.getScopeResolution().getLast(), exprs);
        }
        if (name instanceof MemberAccess memberAccess) {
            return new MethodCall(memberAccess.getExpression(), memberAccess.getMember(), exprs);
        }
        return new FunctionCall(name, exprs);
    }

    private Annotation fromDecorator(TSNode node) {
        TSNode child = node.getNamedChild(0);
        if (child.getType().equals("call")) {
            Node unknownCall = fromFunctionCall(node);
            if (unknownCall instanceof FunctionCall call) {
                return new Annotation(PythonSpecificFeatures.getFunctionName(call), call.getArguments().toArray(new Expression[0]));
            } else {
                throw new RuntimeException("Decorator call conflicting with operation node");
            }
        } else {
            Node ident = parseTSNode(node.getNamedChild(0));
            if (ident instanceof MemberAccess memAccess) {
                ident = memAccess.toScopedIdentifier();
            }
            return new Annotation((Identifier) ident);
        }
    }

    private Node fromFunctionTSNode(TSNode node) {
        List<Annotation> anno = new ArrayList<>();
        if (node.getType().equals("decorated_definition")) {
            TSNode decorator = node.getNamedChild(0);
            anno.add(fromDecorator(decorator));
            while (decorator.getNextNamedSibling().getType().equals("decorator")) {
                decorator = decorator.getNextNamedSibling();
                anno.add(fromDecorator(decorator));
            }
            node = node.getChildByFieldName("definition");
        }
        SimpleIdentifier name = new SimpleIdentifier(getCodePiece(node.getChildByFieldName("name")));
        List<DeclarationArgument> arguments = new ArrayList<>();
        for (int i = 0; i < node.getChildByFieldName("parameters").getNamedChildCount(); i++) {
            arguments.add(fromDeclarationArgument(node.getChildByFieldName("parameters").getNamedChild(i)));
        }
        Type returnType = determineType(node.getChildByFieldName("return_type"));

        scope.enter();
        CompoundStatement body = fromCompoundTSNode(node.getChildByFieldName("body"));

        assert body != null;
        rollbackContext();
        return new FunctionDefinition(new FunctionDeclaration(name, returnType, anno, arguments.toArray(new DeclarationArgument[0])), body);
    }

    private DeclarationArgument fromDeclarationArgument(TSNode namedChild) {
        Type type = new UnknownType();
        Expression initial = null;
        boolean isListUnpacking = false;
        boolean isDictUnpacking = false;
        if (namedChild.getType().equals("typed_parameter")) {
            type = determineType(namedChild.getChildByFieldName("type"));
            namedChild = namedChild.getNamedChild(0);
        } else if (namedChild.getType().equals("typed_default_parameter")) {
            type = determineType(namedChild.getChildByFieldName("type"));
            initial = (Expression) parseTSNode(namedChild.getChildByFieldName("value"));
            namedChild = namedChild.getNamedChild(0);
        }

        if (namedChild.getType().equals("default_parameter")) {
            initial = (Expression) parseTSNode(namedChild.getChildByFieldName("value"));
            namedChild = namedChild.getNamedChild(0);
        } else if (namedChild.getType().equals("list_splat_pattern")) {
            isListUnpacking = true;
            namedChild = namedChild.getNamedChild(0);
        } else if (namedChild.getType().equals("dictionary_splat_pattern")) {
            isDictUnpacking = true;
            namedChild = namedChild.getNamedChild(0);
        }

        SimpleIdentifier identifier = (SimpleIdentifier) parseTSNode(namedChild);
        if (isDictUnpacking) {
            return DeclarationArgument.dictUnpacking(type, identifier);
        } else if (isListUnpacking) {
            return DeclarationArgument.listUnpacking(type, identifier);
        }
        return new DeclarationArgument(type, identifier, initial);
    }

    private ClassDefinition fromClass(TSNode node) {
        TSNode superclasses = node.getChildByFieldName("superclasses");
        Type[] supertypes = new Type[0];
        if (!superclasses.isNull()) {
            supertypes = new Type[superclasses.getNamedChildCount()];
            for (int i = 0; i < supertypes.length; i++) {
                supertypes[i] = new Class((SimpleIdentifier) parseTSNode(superclasses.getNamedChild(i)));
            }
        }

        ClassDeclaration classDecl = new ClassDeclaration(new ArrayList<>(),
                (SimpleIdentifier) parseTSNode(node.getChildByFieldName("name")),
                supertypes
        );
        UserType type = new Class((SimpleIdentifier) classDecl.getName());

        scope.enter();
        CompoundStatement body = fromCompoundTSNode(node.getChildByFieldName("body"));

        Node[] bodyNodes = body.getNodes();
        for (int i = 0; i < body.getLength(); i++) {
            Node bodyNode = bodyNodes[i];
            if (bodyNode instanceof VariableDeclaration var) {
                body.substitute(i, var.makeField(List.of(DeclarationModifier.PUBLIC)));
            } else if (bodyNode instanceof FunctionDefinition func) {
                boolean isStatic = false;
                List<Annotation> anno = ((FunctionDeclaration) (func.getDeclaration())).getAnnotations();
                for (Annotation annotation : anno) {
                    isStatic = annotation.hasName() && (annotation.getName().toString().equals("staticmethod") || annotation.getName().toString().equals("classmethod"));
                    if (isStatic) break;
                }
                List<DeclarationModifier> modifiers = new ArrayList<>();
                if (isStatic) {
                    modifiers.add(DeclarationModifier.STATIC);
                }
                DeclarationModifier visibility = DeclarationModifier.PUBLIC;
                modifiers.add(visibility);
                MethodDefinition method = func.makeMethod(type, modifiers);
                MethodDeclaration decl = ((MethodDeclaration) method.getDeclaration());
                if (method.getName().toString().equals("__del__") && decl.getArguments().isEmpty()) {
                    method = new ObjectDestructorDefinition(decl.getOwner(), decl.getName(), decl.getAnnotations(), decl.getModifiers(), method.getBody());
                } else if (method.getName().toString().equals("__init__")) {
                    method = new ObjectConstructorDefinition(decl.getOwner(), decl.getName(), decl.getAnnotations(), decl.getModifiers(), decl.getArguments(), method.getBody());
                }
                body.substitute(i, PythonSpecialNodeTransformations.detectInstanceReferences(method));
            } else {
                body.substitute(i, bodyNode);
            }
        }

        ClassDefinition def = new ClassDefinition(classDecl, body);
        // TODO: кто такой владелец контекста и зачем он нужен?
        // currentContext.setOwner(def);
        scope.leave();

        return def;
    }

    private Statement fromForLoop(TSNode node) {
        SimpleIdentifier left = (SimpleIdentifier) parseTSNode(node.getChildByFieldName("left"));
        Node right = parseTSNode(node.getChildByFieldName("right"));
        Statement body = (Statement) parseTSNode(node.getChildByFieldName("body"));
        if (right instanceof FunctionCall call) {
            Range range = rangeFromFunction(call);
            if (range != null) {
                return new RangeForLoop(range, left, body);
            }
            return new ForEachLoop(new VariableDeclaration(new UnknownType(), left), (Expression) right, body);
        } else {
            return new ForEachLoop(new VariableDeclaration(new UnknownType(), left), (Expression) right, body);
        }
    }

    private Loop fromWhileLoop(TSNode node) {
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition"));
        Statement body = (Statement)  parseTSNode(node.getChildByFieldName("body"));
        if (
                condition instanceof IntegerLiteral integer && integer.getLongValue() != 0
                || condition instanceof BoolLiteral bool && bool.getValue()
                || condition instanceof StringLiteral str && !str.getUnescapedValue().isEmpty()
        ) {
            return new InfiniteLoop(body, getLoopType(node));
        }
        return new WhileLoop(condition, body);
    }

    private LoopType getLoopType(TSNode node) {
        return switch (node.getType()) {
            case "enhanced_for_statement", "for_statement" -> LoopType.FOR;
            case "while_statement" -> LoopType.WHILE;
            case "do_statement" -> LoopType.DO_WHILE;
            default -> throw new UnsupportedParsingException(String.format("Can't parse %s this code:\n%s", node.getType(), getCodePiece(node)));
        };
    }

    private IndexExpression fromIndexTSNode(TSNode node) {
        Expression base = (Expression) parseTSNode(node.getChildByFieldName("value"));
        TSNode subscriptNode = node.getChildByFieldName("subscript");
        ArrayList<Expression> subscripts = new ArrayList<>();
        subscripts.add((Expression) parseTSNode(subscriptNode));
        while (!subscriptNode.getNextNamedSibling().isNull()) {
            subscriptNode = subscriptNode.getNextNamedSibling();
            subscripts.add((Expression) parseTSNode(subscriptNode));
        }
        if (subscripts.size() > 1) {
            return new IndexExpression(base, new ExpressionSequence(subscripts));
        } else {
            return new IndexExpression(base, subscripts.getFirst());
        }
    }

    private MemberAccess fromAttributeTSNode(TSNode node) {
        Expression expr = (Expression) parseTSNode(node.getChildByFieldName("object"));
        SimpleIdentifier member = (SimpleIdentifier) parseTSNode(node.getChildByFieldName("attribute"));
        return new MemberAccess(expr, member);
    }

    private DictionaryLiteral fromDictionary(TSNode node) {
        LinkedHashMap<Expression, Expression> dict = new LinkedHashMap<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            Expression key = (Expression) parseTSNode(node.getNamedChild(i).getChildByFieldName("key"));
            Expression value = (Expression) parseTSNode(node.getNamedChild(i).getChildByFieldName("value"));
            dict.put(key, value);
        }
        return new DictionaryLiteral(dict);
    }

    private TernaryOperator fromTernaryOperatorTSNode(TSNode node) {
        Expression thenExpr = (Expression) parseTSNode(node.getNamedChild(0));
        Expression ifCond = (Expression) parseTSNode(node.getNamedChild(1));
        Expression elseExpr = (Expression) parseTSNode(node.getNamedChild(2));
        return new TernaryOperator(ifCond, thenExpr, elseExpr);
    }

    private Identifier fromDottedNameTSNode(TSNode node) {
        List<SimpleIdentifier> members = new ArrayList<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            members.add((SimpleIdentifier) parseTSNode(node.getNamedChild(i)));
        }
        if (members.size() == 1) {
            return members.getFirst();
        }
        return new ScopedIdentifier(members.toArray(new SimpleIdentifier[0]));
    }

    private Range fromSlice(TSNode node) {
        Expression start = null;
        Expression stop = null;
        Expression step = null;

        int stage = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (node.getChild(i).getType().equals(":")) {
                stage += 1;
            } else {
                switch (stage) {
                    case 0:
                        start = (Expression) parseTSNode(node.getChild(i));
                        break;
                    case 1:
                        stop = (Expression) parseTSNode(node.getChild(i));
                        break;
                    case 2:
                        step = (Expression) parseTSNode(node.getChild(i));
                        break;
                }
            }
        }
        return new Range(start, stop, step, false, true, Range.Type.UNKNOWN);
    }

    private ReturnStatement fromReturnTSNode(TSNode node) {
        if (node.getNamedChildCount() > 0) {
            return new ReturnStatement((Expression) parseTSNode(node.getNamedChild(0)));
        }
        return new ReturnStatement(null);
    }

    private Node createEntryPoint(TSNode node) {
        // detect if __name__ == __main__ construction
        scope.enter();
        CompoundStatement compound = fromCompoundTSNode(node);

        HindleyMilner.inference(List.of(compound.getNodes()), scope);

        Node entryPointNode = null;
        IfStatement entryPointIf = null;
        for (Node programNode : compound.getNodes()) {
            if (programNode instanceof IfStatement ifStmt) {
                ConditionBranch mainBranch = ifStmt.getBranches().get(0);
                if (mainBranch.getCondition() instanceof EqOp eqOp) {
                    if (eqOp.getLeft().toString().equals("__name__") && eqOp.getRight().toString().equals("__main__")) {
                        entryPointNode = mainBranch.getBody();
                        entryPointIf = ifStmt;
                    }
                }
            }
        }
        List<Node> nodes = new ArrayList<>(List.of(compound.getNodes()));
        nodes.remove(entryPointIf);

        boolean expressionMode = getConfigParameter(ExpressionMode.class).orElse(false);

        if (
                (nodes.size() > 1 && expressionMode)
                || (!nodes.isEmpty() && !(nodes.getFirst() instanceof ExpressionStatement) &&
                        !(nodes.getFirst() instanceof  AssignmentStatement) &&
                        !(nodes.getFirst() instanceof Expression) && expressionMode)
        ) {
            throw new UnsupportedParsingException("Cannot parse the code as expression in expression mode");
        }
        if (expressionMode && !nodes.isEmpty()) {
            if (nodes.getFirst() instanceof ExpressionStatement exprStmt) {
                return exprStmt.getExpression();
            }
            return nodes.getFirst();
        }

        return new ProgramEntryPoint(nodes, entryPointNode);
    }

    private Identifier fromIdentifier(TSNode node) {
        return new SimpleIdentifier(getCodePiece(node));
    }

    private Node fromString(TSNode node) {
        TSNode content = node.getChild(1);
        if (getCodePiece(node.getChild(0)).equals("\"\"\"")
                && node.getParent().getType().equals("expression_statement")
                && node.getParent().getNamedChildCount() == 1) {
            return Comment.fromUnescaped(getCodePiece(content));
        }
        StringLiteral.Type type = StringLiteral.Type.NONE;

        if (Stream.of("fr", "r", "rf")
                .anyMatch((String prefix) -> getCodePiece(node.getChild(0)).startsWith(prefix))) {
            type = StringLiteral.Type.RAW;
        }

        if (Stream.of("fr", "f", "rf")
                .anyMatch((String prefix) -> getCodePiece(node.getChild(0)).startsWith(prefix))) {
            TSNode contentNode = node.getNamedChild(1);
            List<Expression> interpolation = new ArrayList<>();

            while (!contentNode.getType().equals("string_end")) {
                if (contentNode.getType().equals("string_content")) {
                    interpolation.add(StringLiteral.fromEscaped(getCodePiece(contentNode), type));
                } else {
                    interpolation.add((Expression) parseTSNode(contentNode));
                }
                contentNode = contentNode.getNextNamedSibling();
            }
            return new InterpolatedStringLiteral(type, interpolation);
        }

        if (type == StringLiteral.Type.RAW) {
            return StringLiteral.fromUnescaped(getCodePiece(content), type);
        } else {
            return StringLiteral.fromEscaped(getCodePiece(content), type);
        }
    }

    private Comment fromComment(TSNode node) {
        return Comment.fromUnescaped(getCodePiece(node).replace("#", "").trim());
    }

    private Type determineType(TSNode typeNode) {
        if (typeNode.isNull()) {
            return new UnknownType();
        }
        if (typeNode.getNamedChildCount() > 0 && typeNode.getNamedChild(0).getType().equals("generic_type")) {
            TSNode genericTypeNode = typeNode.getNamedChild(0);
            List<Type> genericTypes = new ArrayList<>();
            String typeName = getCodePiece(typeNode.getNamedChild(0).getNamedChild(0));
            for (int i = 0; i < genericTypeNode.getNamedChild(1).getNamedChildCount(); i++) {
                genericTypes.add(determineType(genericTypeNode.getNamedChild(1).getNamedChild(i)));
            }
            switch (typeName) {
                case "list":
                    return new ListType(genericTypes.getFirst());
                case "tuple":
                    return new UnmodifiableListType(genericTypes.getFirst());
                case "dict":
                    return new DictionaryType(genericTypes.getFirst(), genericTypes.get(1));
                default:
                    return new GenericUserType(new SimpleIdentifier(typeName), genericTypes.toArray(new Type[0]));
            }
        }
        switch (getCodePiece(typeNode)) {
            case "str":
                return new StringType();
            case "int":
                return new IntType();
            case "float":
                return new FloatType();
            case "list":
                return new ListType(new UnknownType());
            case "tuple":
                return new UnmodifiableListType(new UnknownType());
            case "dict":
                return new DictionaryType(new UnknownType(), new UnknownType());
            case "set":
                return new SetType(new UnknownType());
            default:
                return new Class(new SimpleIdentifier(getCodePiece(typeNode)));
        }
    }

    private Node fromAssignmentExpressionTSNode(TSNode node) {
        Expression left = (Expression) parseTSNode(node.getChildByFieldName("name"));
        Expression right = (Expression) parseTSNode(node.getChildByFieldName("value"));

        if (left instanceof SimpleIdentifier variableName && right != null) {
            var leftType = scope.getVariableType(variableName);
            var rightType = HindleyMilner.inference(right, scope);

            if (leftType == null) {
                scope.changeVariableType(variableName, rightType);
            }
            else {
                scope.changeVariableType(
                        variableName,
                        HindleyMilner.chooseGeneralType(leftType, rightType)
                );
            }
        }

        return new AssignmentExpression(left, right);
    }

    private Node fromAssignmentStatementTSNode(TSNode node) {
        TSNode operator = node.getChildByFieldName("left").getNextSibling();
        AugmentedAssignmentOperator augOp;
        switch (getCodePiece(operator)) {
            case "+=":
                augOp = AugmentedAssignmentOperator.ADD;
                break;
            case "-=":
                augOp = AugmentedAssignmentOperator.SUB;
                break;
            case "*=":
                augOp = AugmentedAssignmentOperator.MUL;
                break;
            case "/=":
                augOp = AugmentedAssignmentOperator.DIV;
                break;
            case "//=":
                augOp = AugmentedAssignmentOperator.FLOOR_DIV;
                break;
            case "|=":
                augOp = AugmentedAssignmentOperator.BITWISE_OR;
                break;
            case "&=":
                augOp = AugmentedAssignmentOperator.BITWISE_AND;
                break;
            case ">>=":
                augOp = AugmentedAssignmentOperator.BITWISE_SHIFT_RIGHT;
                break;
            case "<<=":
                augOp = AugmentedAssignmentOperator.BITWISE_SHIFT_LEFT;
                break;
            case "%=":
                augOp = AugmentedAssignmentOperator.MOD;
                break;
            case "**=":
                augOp = AugmentedAssignmentOperator.POW;
                break;
            case "^=":
                augOp = AugmentedAssignmentOperator.BITWISE_XOR;
                break;
            default:
                augOp = AugmentedAssignmentOperator.NONE;
        }

        // Обработка распаковки a, b = ...
        if (node.getChildByFieldName("left").getType().equals("pattern_list")) {
            List<Identifier> idents = new ArrayList<>();
            TSNode left = node.getChildByFieldName("left");
            for (int i = 0; i < left.getNamedChildCount(); i++) {
                idents.add(fromIdentifier(left.getNamedChild(i)));
            }

            List<Expression> exprs = new ArrayList<>();
            TSNode rightNode = node.getChildByFieldName("right");
            if (rightNode.getType().equals("expression_list")) {
                for (int i = 0; i < rightNode.getNamedChildCount(); i++) {
                    exprs.add((Expression) parseTSNode(rightNode.getNamedChild(i)));
                }
            } else {
                exprs.add((Expression) parseTSNode(rightNode));
            }
            while (exprs.size() < idents.size()) {
                exprs.add(new NullLiteral());
            }
            if (idents.size() < exprs.size()) {
                throw new UnsupportedParsingException("Invalid using of unpacking construction");
            }

            boolean allNew = augOp == AugmentedAssignmentOperator.NONE
                    && idents.stream().allMatch(id -> scope.getVariableType((SimpleIdentifier) id) == null);

            if (allNew) {
                // Вычисляем общий тип по всем выражениям
                var evaluatedTypes = exprs.stream().map(expr -> HindleyMilner.inference(expr, scope)).toList();
                Type commonType = HindleyMilner.chooseGeneralType(evaluatedTypes);

                // Регистрируем все переменные и создаём декларатор-ы
                List<VariableDeclarator> decls = new ArrayList<>();
                for (int i = 0; i < idents.size(); i++) {
                    Identifier id = idents.get(i);
                    Expression init = exprs.get(i);
                    scope.changeVariableType((SimpleIdentifier) id, commonType);
                    decls.add(new VariableDeclarator((SimpleIdentifier) id, init));
                }
                return new VariableDeclaration(commonType, decls);
            }

            // Иначе — обычный multiple assignment
            List<AssignmentStatement> stmts = new ArrayList<>();
            for (int i = 0; i < idents.size(); i++) {
                stmts.add(new AssignmentStatement(idents.get(i), exprs.get(i), augOp));
            }
            return new MultipleAssignmentStatement(stmts);
        }

        // Обычное присваивание
        Expression leftExpr = (Expression) parseTSNode(node.getChildByFieldName("left"));
        Node rightRaw = parseTSNode(node.getChildByFieldName("right"));
        Expression rightExpr = (rightRaw instanceof AssignmentStatement r)
                ? r.toExpression()
                : (Expression) rightRaw;

        if (leftExpr instanceof SimpleIdentifier variableName
                && rightExpr != null
                && augOp == AugmentedAssignmentOperator.NONE) {
            Type leftType = scope.getVariableType(variableName);
            Type rightType = HindleyMilner.inference(rightExpr, scope);

            if (leftType == null) {
                scope.changeVariableType(variableName, rightType);
                return new VariableDeclaration(rightType, variableName, rightExpr);
            } else {
                scope.changeVariableType(
                        variableName,
                        HindleyMilner.chooseGeneralType(leftType, rightType)
                );
            }
        }

        return new AssignmentStatement(leftExpr, rightExpr, augOp);
    }

    private Node fromList(TSNode node, String type) {
        List<Expression> exprs = new ArrayList<>();
        for (int i = 0; i < node.getNamedChildCount(); i++) {
            Expression expr = (Expression) parseTSNode(node.getNamedChild(i));
            exprs.add(expr);
        }
        return switch (type) {
            case "list" -> new ListLiteral(exprs.toArray(new Expression[0]));
            case "tuple" -> new UnmodifiableListLiteral(exprs.toArray(new Expression[0]));
            case "set" -> new SetLiteral(exprs.toArray(new Expression[0]));
            default -> null;
        };
    }

    private CompoundStatement fromCompoundTSNode(TSNode node) {
        var nodes = new ArrayList<Node>();
        for (int i = 0; i < node.getChildCount(); i++) {
            nodes.add(parseTSNode(node.getChild(i)));
        }
        return new CompoundStatement(nodes);
    }

    private IfStatement fromIfStatementTSNode(TSNode node) {
        List<ConditionBranch> branches = new ArrayList<>();
        branches.add(createConditionBranchTSNode(node));
        Statement elseBranch = null;
        TSNode altNode = node.getChildByFieldName("alternative");
        while (!altNode.isNull() &&
                (altNode.getType().equals("elif_clause") || altNode.getType().equals("else_clause"))) {
            if (altNode.getType().equals("elif_clause")) {
                branches.add(createConditionBranchTSNode(altNode));
            } else {
                elseBranch = (Statement) parseTSNode(altNode.getChildByFieldName("body"));
            }
            altNode = altNode.getNextNamedSibling();
        }
        return new IfStatement(branches, elseBranch);
    }

    private ConditionBranch createConditionBranchTSNode(TSNode node) {
        Expression condition = (Expression) parseTSNode(node.getChildByFieldName("condition"));
        CompoundStatement consequence = (CompoundStatement) parseTSNode(node.getChildByFieldName("consequence"));
        return new ConditionBranch(condition, consequence);
    }

    private UnaryExpression fromUnaryExpressionTSNode(TSNode node) {
        Expression argument = (Expression) parseTSNode(node.getChildByFieldName("argument"));
        TSNode operation = node.getChildByFieldName("operator");
        return switch (getCodePiece(operation)) {
            case "~" -> new InversionOp(argument);
            case "-" -> new UnaryMinusOp(argument);
            case "+" -> new UnaryPlusOp(argument);
            case null, default -> throw new UnsupportedOperationException();
        };
    }

    private NotOp fromNotOperatorTSNode(TSNode node) {
        Expression argument = (Expression) parseTSNode(node.getChildByFieldName("argument"));
        if (argument instanceof InstanceOfOp op) {
            argument = new ParenthesizedExpression(op);
        }
        return new NotOp(argument);
    }


    private Node fromExpressionSequencesTSNode(TSNode node) {
        if (node.getNamedChildCount() == 1 && node.getType().equals("expression_statement")) {
            Node n = parseTSNode(node.getChild(0));
            if (n instanceof Statement || n instanceof Declaration || n instanceof Comment) {
                return n;
            }
            return new ExpressionStatement((Expression) n);
        } else {
            Expression[] exprs = new Expression[node.getNamedChildCount()];
            for (int i = 0; i < exprs.length; i++) {
                Node n = parseTSNode(node.getNamedChild(i));
                if (n instanceof Expression expr) {
                    exprs[i] = expr;
                } else {
                    throw new UnsupportedParsingException("Invalid type in expression statement, not expression");
                }
            }
            return new ExpressionSequence(exprs);
        }
    }

    private ParenthesizedExpression fromParenthesizedExpressionTSNode(TSNode node) {
        return new ParenthesizedExpression((Expression) parseTSNode(node.getChild(1)));
    }

    private IntegerLiteral fromIntegerLiteralTSNode(TSNode node) {
        String value = getCodePiece(node);
        return new IntegerLiteral(value, false, false);
    }

    private FloatLiteral fromFloatLiteralTSNode(TSNode node) {
        String value = getCodePiece(node);
        return new FloatLiteral(value, true);
    }

    private Node fromComparisonTSNode(TSNode node) {
        java.lang.Class<? extends BinaryComparison> operator = null;
        ArrayDeque<TSNode> operands = new ArrayDeque<>();

        List<BinaryComparison> comparisons = new ArrayList<>();

        boolean notFlag = false;
        for (int i = 0; i < node.getChildCount(); i++) {
            TSNode children = node.getChild(i);

            switch (children.getType()) {
                case "<":
                    operator = LtOp.class;
                    break;
                case ">":
                    operator = GtOp.class;
                    break;
                case "<=":
                    operator = LeOp.class;
                    break;
                case ">=":
                    operator = GeOp.class;
                    break;
                case "==":
                    operator = EqOp.class;
                    break;
                case "!=":
                    operator = NotEqOp.class;
                    break;
                case "not in":
                    operator = ContainsOp.class;
                    notFlag = true;
                    break;
                case "is not":
                    operator = ReferenceEqOp.class;
                    notFlag = true;
                    break;
                case "is":
                    operator = ReferenceEqOp.class;
                    break;
                case "in":
                    operator = ContainsOp.class;
                    break;
                default:
                    operands.add(children);
            }
            if (operands.size() == 2) {
                Expression firstOp = (Expression) parseTSNode(operands.removeFirst());
                TSNode secondNode = operands.removeFirst();
                Expression secondOp = (Expression) parseTSNode(secondNode);
                if (operator == ContainsOp.class) {
                    comparisons.add(new ContainsOp(firstOp, secondOp, notFlag));
                    notFlag = false;
                } else if (operator == ReferenceEqOp.class) {
                    comparisons.add(new ReferenceEqOp(firstOp, secondOp, notFlag));
                    notFlag = false;
                } else {
                    try {
                        BinaryComparison object = operator.getDeclaredConstructor(Expression.class, Expression.class).newInstance(firstOp, secondOp);
                        comparisons.add(object);
                    } catch (InstantiationException | InvocationTargetException | IllegalAccessException |
                             NoSuchMethodException e) {
                        e.printStackTrace();
                        throw new UnsupportedParsingException(e.getMessage());
                    }
                }
                operands.add(secondNode);
            }
        }
        if (comparisons.size() == 1) {
            return comparisons.getFirst();
        }
        return new CompoundComparison(comparisons.toArray(new BinaryComparison[0]));
    }

    private BinaryExpression fromBooleanOperatorTSNode(TSNode node) {
        Expression left = (Expression) parseTSNode(node.getChildByFieldName("left"));
        Expression right = (Expression) parseTSNode(node.getChildByFieldName("right"));
        TSNode operator = node.getChildByFieldName("operator");

        if (getCodePiece(operator).equals("and")) {
            return new ShortCircuitAndOp(left, right);
        } else if (getCodePiece(operator).equals("or")) {
            return new ShortCircuitOrOp(left, right);
        }
        return null;
    }

    private BinaryExpression fromBinaryExpressionTSNode(TSNode node) {
        Expression left = (Expression) parseTSNode(node.getChildByFieldName("left"));
        Expression right = (Expression) parseTSNode(node.getChildByFieldName("right"));
        TSNode operator = node.getChildByFieldName("operator");

        return switch (getCodePiece(operator)) {
            case "+" -> new AddOp(left, right);
            case "-" -> new SubOp(left, right);
            case "*" -> new MulOp(left, right);
            case "@" -> new MatMulOp(left, right);
            case "**" -> new PowOp(left, right);
            case "/" -> new DivOp(left, right);
            case "//" -> new FloorDivOp(left, right);
            case "%" -> new ModOp(left, right);
            case "<<" -> new LeftShiftOp(left, right);
            case ">>" -> new RightShiftOp(left, right);
            case "&" -> new BitwiseAndOp(left, right);
            case "|" -> new BitwiseOrOp(left, right);
            case "^" -> new XorOp(left, right);
            default -> throw new UnsupportedOperationException(String.format("Can't parse operator %s", getCodePiece(operator)));
        };
    }
}