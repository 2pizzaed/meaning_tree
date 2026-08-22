package org.vstu.meaningtree.utils.analysis.types;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.*;
import org.vstu.meaningtree.nodes.declarations.components.DeclarationArgument;
import org.vstu.meaningtree.nodes.declarations.components.VariableDeclarator;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.Literal;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.bitwise.InversionOp;
import org.vstu.meaningtree.nodes.expressions.comparison.BinaryComparison;
import org.vstu.meaningtree.nodes.expressions.comparison.CompoundComparison;
import org.vstu.meaningtree.nodes.expressions.identifiers.SimpleIdentifier;
import org.vstu.meaningtree.nodes.expressions.literals.*;
import org.vstu.meaningtree.nodes.expressions.logical.*;
import org.vstu.meaningtree.nodes.expressions.math.AddOp;
import org.vstu.meaningtree.nodes.expressions.math.DivOp;
import org.vstu.meaningtree.nodes.expressions.other.*;
import org.vstu.meaningtree.nodes.expressions.pointers.PointerUnpackOp;
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.interfaces.HasAssignmentEffect;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.interfaces.HasComputedType;
import org.vstu.meaningtree.nodes.modules.PackageDeclaration;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.ChainedAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.assignments.MultipleAssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.statements.conditions.components.ConditionBranch;
import org.vstu.meaningtree.nodes.statements.conditions.components.MatchValueCaseBlock;
import org.vstu.meaningtree.nodes.statements.loops.*;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;
import org.vstu.meaningtree.utils.scopes.ScopeTable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

public class SimpleTypeInferrer {
    @NotNull
    public static NumericType inference(@NotNull NumericLiteral numericLiteral) {
        return switch (numericLiteral) {
            case FloatLiteral floatLiteral -> new FloatType();
            case IntegerLiteral integerLiteral -> new IntType();
            default -> throw new IllegalStateException("Unexpected number type: " + numericLiteral);
        };
    }

    @NotNull
    public static Type inference(@NotNull Literal literal) {
        return switch (literal) {
            case NumericLiteral numericLiteral -> inference(numericLiteral);
            case BoolLiteral boolLiteral -> new BooleanType();
            case StringLiteral stringLiteral -> new StringType();
            case InterpolatedStringLiteral interpolatedStringLiteral -> new StringType();
            case NullLiteral nullLiteral -> new UnknownType();
            case ArrayLiteral arrayLiteral -> inference(arrayLiteral);
            case ListLiteral listLiteral -> inference(listLiteral);
            case DictionaryLiteral dictionaryLiteral -> inference(dictionaryLiteral);
            case SetLiteral setLiteral -> inference(setLiteral);
            case UnmodifiableListLiteral unmodifiableListLiteral -> inference(unmodifiableListLiteral);
            case CharacterLiteral characterLiteral -> new CharacterType();
            default -> new UnknownType();
        };
    }

    @NotNull
    public static UnmodifiableListType inference(@NotNull UnmodifiableListLiteral unmodifiableListLiteral) {
        Type valueType = null;
        if (unmodifiableListLiteral.getTypeHint() != null
                && !(unmodifiableListLiteral.getTypeHint() instanceof UnknownType)) {
            valueType = unmodifiableListLiteral.getTypeHint();
        }

        if (valueType == null) {
            valueType = chooseGeneralType(inference(unmodifiableListLiteral.getList()));
        }

        return new UnmodifiableListType(valueType);
    }

    @NotNull
    public static SetType inference(@NotNull SetLiteral setLiteral) {
        Type valueType = null;
        if (setLiteral.getTypeHint() != null
                && !(setLiteral.getTypeHint() instanceof UnknownType)) {
            valueType = setLiteral.getTypeHint();
        }

        if (valueType == null) {
            valueType = chooseGeneralType(inference(setLiteral.getList()));
        }

        return new SetType(valueType);
    }

    @NotNull
    public static DictionaryType inference(@NotNull DictionaryLiteral dictionaryLiteral) {
        Type keyType = null;
        if (dictionaryLiteral.getKeyTypeHint() != null
                && !(dictionaryLiteral.getKeyTypeHint() instanceof UnknownType)) {
            keyType = dictionaryLiteral.getKeyTypeHint();
        }

        Type valueType = null;
        if (dictionaryLiteral.getValueTypeHint() != null
                && !(dictionaryLiteral.getValueTypeHint() instanceof UnknownType)) {
            valueType = dictionaryLiteral.getValueTypeHint();
        }

        if (keyType == null || valueType == null) {
            var content = dictionaryLiteral.getContent();

            var keys = content.stream().map(KeyValuePair::key).toList();
            var values = content.stream().map(KeyValuePair::value).toList();

            var inferredKeyType = chooseGeneralType(inference(keys));
            var inferredValueType = chooseGeneralType(inference(values));

            if (keyType == null)
                keyType = inferredKeyType;

            if (valueType == null)
                valueType = inferredValueType;
        }

        return new OrderedDictionaryType(keyType, valueType);
    }

    @NotNull
    public static ListType inference(@NotNull ListLiteral listLiteral) {
        Type valueType = null;
        if (listLiteral.getTypeHint() != null
                && !(listLiteral.getTypeHint() instanceof UnknownType)) {
            valueType = listLiteral.getTypeHint();
        }

        if (valueType == null) {
            var inferredTypes = inference(listLiteral.getList());
            valueType = chooseGeneralType(inferredTypes);
        }

        return new ListType(valueType);
    }

    @NotNull
    public static ArrayType inference(@NotNull ArrayLiteral arrayLiteral) {
        Type valueType = null;
        if (arrayLiteral.getTypeHint() != null
                && !(arrayLiteral.getTypeHint() instanceof UnknownType)) {
            valueType = arrayLiteral.getTypeHint();
        }

        if (valueType == null) {
            var inferredTypes = inference(arrayLiteral.getList());
            valueType = chooseGeneralType(inferredTypes);
        }

        if (valueType instanceof ArrayType nestedArray) {
            return new ArrayType(
                    (Type) nestedArray.getItemType().freshClone(),
                    nestedArray.getDimensionsCount() + 1
            );
        }

        var size = new IntegerLiteral(arrayLiteral.getList().size());
        return new ArrayType(valueType, size);
    }

    @NotNull
    private static List<Type> inference(@NotNull List<Expression> expressions) {
        return expressions
                .stream()
                .map(SimpleTypeInferrer::inference)
                .toList();
    }

    @NotNull
    public static Type inference(
            @NotNull SimpleIdentifier identifier,
            @NotNull ScopeTable scope) {
        Type inferredType = scope.getVariableType(identifier);
        if (inferredType == null) {
            return new UnknownType();
        }

        return inferredType;
    }

    @NotNull
    public static Type chooseGeneralType(Type first, Type second) {
        if (first instanceof UnknownType || second instanceof UnknownType) {
            return new UnknownType();
        }
        else if (first instanceof NumericType fn && second instanceof NumericType sn) {
            if (first instanceof FloatType || second instanceof FloatType) {
                return new FloatType(Math.max(fn.getBitsize(), sn.getBitsize()));
            }

            return new IntType(Math.max(fn.getBitsize(), sn.getBitsize()));
        }
        else if (first instanceof StringType || second instanceof StringType) {
            return new StringType();
        }
        else if (first instanceof BooleanType && second instanceof BooleanType) {
            return new BooleanType();
        }
        else if (first instanceof ArrayType firstArray && second instanceof ArrayType secondArray
                && firstArray.getDimensionsCount() == secondArray.getDimensionsCount()) {
            Type itemType = chooseGeneralType(firstArray.getItemType(), secondArray.getItemType());
            return itemType instanceof UnknownType
                    ? itemType
                    : new ArrayType(itemType, firstArray.getDimensionsCount());
        }
        else if (first.equals(second)) {
            return (Type) first.freshClone();
        }

        return new UnknownType();
    }

    @NotNull
    public static Type chooseGeneralType(List<Type> types) {
        if (types.isEmpty()) {
            return new UnknownType();
        }
        else if (types.size() == 1) {
            return types.getFirst();
        }

        return chooseGeneralType(
                types.getFirst(),
                chooseGeneralType(types.subList(1, types.size()))
        );
    }

    /**
     * Проталкивает тип выражения внутрь: назначает его всем переменным,
     * входящим в это выражение.
     * <p>
     * Обход нерекурсивный, но спускается только по выражениям: тип относится
     * к вычислению, поэтому во вложенные объявления, типы и тела лямбд заходить
     * нельзя — идентификаторы там к этому вычислению отношения не имеют.
     * Повторные записи одного и того же типа безопасны, так как тип может только
     * повышаться (неизвестный -> известный, int -> float).
     */
    public static void backwardVariableTypeSet(
            @NotNull Expression expression,
            @NotNull ScopeTable scope,
            @NotNull Type type) {
        var queue = new ArrayDeque<Expression>();
        queue.add(expression);

        while (!queue.isEmpty()) {
            Expression current = queue.remove();

            if (current instanceof SimpleIdentifier identifier) {
                upgradeVariableType(identifier, scope, type);
                continue;
            }

            for (Node child : current.allChildren()) {
                if (child instanceof Expression childExpression) {
                    queue.add(childExpression);
                }
            }
        }
    }

    private static void upgradeVariableType(
            @NotNull SimpleIdentifier identifier,
            @NotNull ScopeTable scope,
            @NotNull Type type) {
        Type possibleType = scope.getVariableType(identifier);

        if (possibleType == null || possibleType instanceof UnknownType) {
            scope.changeVariableType(identifier, type);
        }
        // Добавить что-то про обобщение типов
        else if (possibleType instanceof IntType
                && type instanceof FloatType) {
            scope.changeVariableType(identifier, type);
        }
    }

    @NotNull
    public static Type inferenceOperandsTypeByExpressionType(@NotNull Expression expression) {
        return switch (expression) {
            case LongCircuitOrOp longCircuitOrOp -> new BooleanType();
            case LongCircuitAndOp longCircuitAndOp -> new BooleanType();
            case ShortCircuitOrOp shortCircuitOrOp -> new BooleanType();
            case ShortCircuitAndOp shortCircuitAndOp -> new BooleanType();
            case DivOp divOp -> new FloatType();
            default -> new UnknownType();
        };
    }

    @NotNull
    public static Type inference(@NotNull BinaryExpression binaryExpression, @NotNull ScopeTable scope) {
        Expression left = binaryExpression.getLeft();
        Expression right = binaryExpression.getRight();

        Type leftType = inference(left, scope);
        Type rightType = inference(right, scope);

        if (leftType instanceof UnknownType && rightType instanceof UnknownType) {
            if (binaryExpression instanceof BinaryComparison) {
                leftType = rightType = new FloatType();
            }
            else {
                leftType = rightType = inferenceOperandsTypeByExpressionType(binaryExpression);
            }

            if (!(leftType instanceof UnknownType)) {
                backwardVariableTypeSet(left, scope, leftType);
                backwardVariableTypeSet(right, scope, rightType);
            }
        }
        else if (leftType instanceof UnknownType) {
            backwardVariableTypeSet(left, scope, rightType);
            leftType = rightType;
        }
        else if (rightType instanceof UnknownType) {
            backwardVariableTypeSet(right, scope, leftType);
            rightType = leftType;
        }

        if (binaryExpression instanceof BinaryComparison || binaryExpression instanceof InstanceOfOp) {
            return new BooleanType();
        }
        else if (binaryExpression instanceof DivOp) {
            // FloatType — по умолчанию для деления, как в Python (/ всегда даёт float).
            // Но если оба операнда — уже известные целочисленные типы, отдаём предпочтение
            // C-подобной семантике: int / int усекается до int (Java, C++ и т.п.).
            // Различить эти два случая по одному только DivOp без языкового контекста нельзя,
            // это компромисс: известные целые типы не переопределяются в float.
            if (leftType instanceof NumericType && rightType instanceof NumericType
                    && !(leftType instanceof FloatType) && !(rightType instanceof FloatType)) {
                return chooseGeneralType(leftType, rightType);
            }
            return new FloatType();
        }

        Type generalType = chooseGeneralType(leftType, rightType);
        if (left instanceof SimpleIdentifier leftIdentifier) {
            backwardVariableTypeSet(leftIdentifier, scope, generalType);
        }
        if (right instanceof SimpleIdentifier rightIdentifier) {
            backwardVariableTypeSet(rightIdentifier, scope, generalType);
        }

        return generalType;
    }

    @NotNull
    public static Type inference(@NotNull UnaryExpression unaryExpression, @NotNull ScopeTable scope) {
        Expression argument = unaryExpression.getArgument();
        Type operandType = inference(argument, scope);

        if (unaryExpression instanceof NotOp) {
            // Тип операнда игнорируется?..
            Type expressionType = new BooleanType();
            backwardVariableTypeSet(argument, scope, expressionType);
            return expressionType;
        }
        else if (unaryExpression instanceof PostfixDecrementOp
                || unaryExpression instanceof PostfixIncrementOp
                || unaryExpression instanceof PrefixDecrementOp
                || unaryExpression instanceof PrefixIncrementOp
                || unaryExpression instanceof UnaryMinusOp
                || unaryExpression instanceof UnaryPlusOp
                || unaryExpression instanceof InversionOp
                || unaryExpression instanceof PointerUnpackOp
        ) {
            if (operandType instanceof UnknownType) {
                Type expressionType = switch (unaryExpression) {
                    case PointerUnpackOp op -> new PointerType(new UnknownType());
                    case InversionOp op -> new IntType();
                    default -> new FloatType();
                };
                backwardVariableTypeSet(argument, scope, expressionType);
                return expressionType;
            }
            return operandType;
        }

        return new UnknownType();
    }

    @NotNull
    public static Type inference(@NotNull AssignmentExpression assignmentExpression, @NotNull ScopeTable scope) {
        AddOp addOp = new AddOp(assignmentExpression.getLValue(), assignmentExpression.getRValue());
        Type generalType = inference(addOp, scope);

        Type rvalueType = inference(assignmentExpression.getRValue(), scope);
        updateRealType(assignmentExpression, rvalueType);

        return generalType;
    }

    /**
     * Записывает фактический тип присваиваемого/инициализирующего значения в realType узла,
     * если вывод дал что-то отличное от текущего значения.
     * <p>
     * Как и для {@link VariableDeclaration#setType}, эквивалентный тип не подменяется:
     * это сохраняет id узла в дереве стабильным между повторными проходами вывода.
     * <p>
     * Выведенный тип клонируется через {@code freshClone()} перед установкой: некоторые
     * методы вывода (например, для {@code ArrayLiteral}) возвращают тип, частично построенный
     * из уже присутствующих в дереве узлов (typeHint), и приживление такого узла как есть
     * сделало бы его достижимым из двух мест дерева одновременно.
     */
    private static void updateRealType(@NotNull HasComputedType node, @NotNull Type inferred) {
        Type current = node.getRealType();
        if (current.equals(inferred)) {
            return;
        }
        Type detached = (Type) inferred.freshClone();
        detached.remap(current);
        node.setRealType(detached);
    }

    @NotNull
    public static Type inference(@NotNull TernaryOperator ternaryOperator, @NotNull ScopeTable scope) {
        inference(ternaryOperator.getCondition(), scope);
        Type thenExprType = inference(ternaryOperator.getThenExpr(), scope);
        Type elseExprType = inference(ternaryOperator.getElseExpr(), scope);
        return chooseGeneralType(thenExprType, elseExprType);
    }

    @NotNull
    public static Type inference(@NotNull Range range, @NotNull ScopeTable scope) {
        var start = range.getStart();
        if (start != null) {
            inference(start, scope);
        }

        var stop = range.getStop();
        if (stop != null) {
            inference(range.getStop(), scope);
        }

        var step = range.getStep();
        if (step != null) {
            inference(range.getStep(), scope);
        }

        // Все, для чего нужен этот метод это обход детей диапазона,
        // чтобы вывести типы переменных, участвующих в формировании диапазона
        return new UnknownType();
    }

    @NotNull
    public static Type inference(@NotNull Expression expression, @NotNull ScopeTable scope) {
        return switch (expression) {
            case Literal literal -> inference(literal);
            case SimpleIdentifier identifier -> inference(identifier, scope);
            case AssignmentExpression assignmentExpression -> inference(assignmentExpression, scope);
            case UnaryExpression unaryExpression -> inference(unaryExpression, scope);
            case BinaryExpression binaryExpression -> inference(binaryExpression, scope);
            case ParenthesizedExpression parenthesizedExpression -> inference(parenthesizedExpression.getExpression(), scope);
            case CompoundComparison compoundComparison -> inference(compoundComparison, scope);
            case TernaryOperator ternaryOperator -> inference(ternaryOperator, scope);
            case Range range -> inference(range, scope);
            default -> new UnknownType();
            //default -> throw new IllegalStateException("Unexpected expression type: " + expression.getClass());
        };
    }

    @NotNull
    public static Type inference(@NotNull Expression expression) {
        return inference(expression, new ScopeTable());
    }

    public static void inference(@NotNull HasAssignmentEffect stmt, @NotNull ScopeTable scope) {
        if (stmt instanceof AssignmentStatement assignmentStatement) {
            AssignmentExpression assignmentExpression = new AssignmentExpression(
                    assignmentStatement.getLValue(),
                    assignmentStatement.getRValue(),
                    assignmentStatement.getAugmentedOperator()
            );
            inference(assignmentExpression, scope);
            updateRealType(assignmentStatement, assignmentExpression.getRealType());
        } else if (stmt instanceof ChainedAssignmentStatement chainedAssignmentStatement) {
            Type valueType = inference(chainedAssignmentStatement.getValue(), scope);
            updateRealType(chainedAssignmentStatement, valueType);
            for (Expression target : chainedAssignmentStatement.getTargets()) {
                if (target instanceof SimpleIdentifier identifier) {
                    Type currentType = scope.getVariableType(identifier);
                    scope.changeVariableType(identifier, currentType == null
                            ? valueType
                            : chooseGeneralType(currentType, valueType));
                }
            }
        } else if (stmt instanceof MultipleAssignmentStatement multipleAssignmentStatement) {
            for (AssignmentStatement assignment : multipleAssignmentStatement.getStatements()) {
                inference((HasAssignmentEffect) assignment, scope);
            }
        }
    }

    public static void inference(@NotNull CompoundStatement compoundStatement, @NotNull ScopeTable scope) {
        long previousScopeId = scope.currentScopeId();
        boolean scopeSwitched = enterBoundScope(compoundStatement, scope);

        try {
            for (var node : compoundStatement.getNodes()) {

                if (node instanceof Statement statement) {
                    inference(statement, scope);
                }
                else if (node instanceof Expression expression) {
                    inference(expression, scope);
                }
            }
        }
        finally {
            if (scopeSwitched) {
                scope.setCurrentScope(previousScopeId);
            }
        }
    }

    /**
     * Переключает таблицу областей видимости на область, привязанную к данному телу.
     * Новая область не создается: используется только та, что уже была построена
     * при разборе тела, иначе вывод остается в текущей области.
     *
     * @return {@code true}, если переключение произошло и область нужно вернуть обратно
     */
    private static boolean enterBoundScope(
            @NotNull CompoundStatement compoundStatement,
            @NotNull ScopeTable scope) {
        OptionalLong boundScopeId = compoundStatement.getScopeId();
        if (boundScopeId.isEmpty()) {
            return false;
        }

        long scopeId = boundScopeId.getAsLong();
        if (scopeId == scope.currentScopeId() || scope.findScope(scopeId).isEmpty()) {
            return false;
        }

        scope.setCurrentScope(scopeId);
        return true;
    }

    public static void inference(@NotNull IfStatement ifStatement, @NotNull ScopeTable scope) {

        for (var conditionBranch : ifStatement.getBranches()) {
            inference(conditionBranch, scope);
        }

        if (ifStatement.hasElseBranch()) {
            inference(ifStatement.getElseBranch(), scope);
        }
    }

    public static Type inference(@NotNull CompoundComparison compoundComparison, @NotNull ScopeTable scope) {

        for (var binaryComparison : compoundComparison.getComparisons()) {
            inference(binaryComparison, scope);
        }

        return new BooleanType();
    }

    public static void inference(@NotNull SwitchStatement switchStatement, @NotNull ScopeTable scope) {
        inference(switchStatement.getTargetExpression(), scope);
        for (var caseBranch : switchStatement.getCases()) {
            if (caseBranch != null) {
                inference(caseBranch, scope);
            }
        }
    }

    public static void inference(@NotNull Statement statement, @NotNull ScopeTable scope) {
        inference(List.of(statement), scope);
    }

    public static void inference(@NotNull VariableDeclarator variableDeclarator, @NotNull ScopeTable scope) {
        if (variableDeclarator.hasInitialization()) {
            Type rvalueType = inference(variableDeclarator.getRValue(), scope);
            updateRealType(variableDeclarator, rvalueType);
        }
        inference(variableDeclarator.getIdentifier(), scope);
    }

    public static void inference(@NotNull VariableDeclaration variableDeclaration, @NotNull ScopeTable scope) {
        if (isKnown(variableDeclaration.getType())) {
            // Объявленный тип известен, выводить нечего. Его нужно только внести
            // в область видимости, чтобы использования переменной опирались
            // на объявление, а не на догадки по контексту
            scope.registerVariable(variableDeclaration);

            for (var variableDeclarator : variableDeclaration.getDeclarators()) {
                inference(variableDeclarator, scope);
            }

            return;
        }

        var types = new ArrayList<Type>();

        for (var variableDeclarator : variableDeclaration.getDeclarators()) {
            inference(variableDeclarator, scope);

            var varType = scope.getVariableType(variableDeclarator.getIdentifier());
            if (varType != null) {
                types.add(varType);
            }
            else if (variableDeclarator.hasInitialization()) {
                varType = inference(variableDeclarator.getRValue(), scope);
                types.add(varType);
            }
        }

        Type inferred = chooseGeneralType(types);
        Type current = variableDeclaration.getType();
        if (current != null && current.equals(inferred)) {
            // Вывод дал тот же тип: подменять узел нельзя. Вывод выполняется в том числе при
            // отрисовке, и замена эквивалентного узла на новый экземпляр меняла бы id типа в
            // дереве на каждую генерацию — source map переставал бы воспроизводиться
            return;
        }
        if (current != null) {
            inferred.remap(current);
        }
        variableDeclaration.setType(inferred);
    }

    public static void inference(@NotNull Declaration declaration, @NotNull ScopeTable scope) {
        switch (declaration) {
            case VariableDeclaration variableDeclaration -> inference(variableDeclaration, scope);
            case DeclarationArgument declarationArgument -> inference(declarationArgument, scope);
            case SeparatedVariableDeclaration separatedVariableDeclaration -> inference(separatedVariableDeclaration, scope);
            case ListUnpackingVariableDeclaration listUnpackingVariableDeclaration -> inference(listUnpackingVariableDeclaration, scope);
            case PackageDeclaration packageDeclaration -> {} // do nothing
            // объявление перечисления не вводит переменных, выводить нечего
            case EnumDeclaration enumDeclaration -> {}
            default -> throw new IllegalStateException("Unexpected declaration type: " + declaration.getClass());
        }
    }

    public static void inference(@NotNull SeparatedVariableDeclaration varDecl, @NotNull ScopeTable scope) {
        for (var variableDeclaration : varDecl.getDeclarations()) {
            inference(variableDeclaration, scope);
        }
    }

    public static void inference(@NotNull ListUnpackingVariableDeclaration decl, @NotNull ScopeTable scope) {
        inference(decl.getValue(), scope);
        for (var ident : decl.getVariableNames()) {
            inference(ident, scope);
        }
    }

    public static void inference(@NotNull DeclarationArgument declarationArgument, @NotNull ScopeTable scope) {
        Type type;
        if (declarationArgument.getType() != null
                && !(declarationArgument.getType() instanceof UnknownType)) {
            type = declarationArgument.getType();
        }
        else {
            if (declarationArgument.hasInitialExpression()) {
                type = inference(declarationArgument.getInitialExpression(), scope);
            }
            else {
                type = new UnknownType();
            }
        }

        if (!(type instanceof UnknownType)
                || !isKnown(scope.getVariableType(declarationArgument.getName()))) {
            scope.changeVariableType(declarationArgument.getName(), type);
        }
    }

    public static void inference(@NotNull Node node, @NotNull ScopeTable scope) {
        switch (node) {
            case ExpressionStatement expressionStatement -> inference(expressionStatement.getExpression(), scope);
            case HasAssignmentEffect assignmentStatement -> inference(assignmentStatement, scope);
            case Annotation annotation -> inference(List.of(annotation.getArguments()), scope);
            case CompoundStatement compoundStatement -> inference(compoundStatement, scope);
            case IfStatement ifStatement -> inference(ifStatement, scope);
            case SwitchStatement switchStatement -> inference(switchStatement, scope);
            case Declaration variableDeclaration -> inference(variableDeclaration, scope);
            case VariableDeclarator variableDeclarator -> inference(variableDeclarator, scope);
            case Expression expression -> inference(expression, scope);
            case HasBodyStatement hasBodyStatement -> inferenceWithBody(hasBodyStatement, scope);
            default -> {
                /* TODO: проанализировать, какие еще есть варианты для обобщения... */
            }
        }
    }

    /**
     * Выводит типы для узла с телом: сначала для заголовка (условия, инициализатора,
     * шага, перебираемого выражения), затем для самого тела.
     * <p>
     * Заголовок цикла со счетчиком обрабатывается в области видимости тела, потому что
     * объявленная в нем переменная принадлежит именно телу. Условие же вычисляется
     * снаружи, и обрабатывать его нужно в текущей области — иначе переменная, впервые
     * связанная в условии, зарегистрировалась бы во вложенной области и пропала бы
     * для кода после цикла.
     */
    public static void inferenceWithBody(@NotNull HasBodyStatement hasBodyStatement, @NotNull ScopeTable scope) {
        CompoundStatement body = hasBodyStatement.getBody() instanceof CompoundStatement compoundStatement
                ? compoundStatement
                : null;

        long previousScopeId = scope.currentScopeId();
        boolean scopeSwitched = body != null
                && headerBelongsToBodyScope(hasBodyStatement)
                && enterBoundScope(body, scope);

        try {
            inferenceHeader(hasBodyStatement, scope);

            if (body != null) {
                inference(body, scope);
            }
        }
        finally {
            if (scopeSwitched) {
                scope.setCurrentScope(previousScopeId);
            }
        }
    }

    /**
     * Объявляет ли заголовок узла переменные, принадлежащие телу. Верно для циклов
     * со счетчиком или элементом перебора и неверно для условий, которые вычисляются
     * в объемлющей области видимости.
     */
    private static boolean headerBelongsToBodyScope(@NotNull HasBodyStatement hasBodyStatement) {
        return hasBodyStatement instanceof GeneralForLoop
                || hasBodyStatement instanceof RangeForLoop
                || hasBodyStatement instanceof ForEachLoop;
    }

    /**
     * Выводит типы для заголовка узла с телом.
     * <p>
     * Тип условия наружу не проталкивается: условие часто является сравнением,
     * и {@code BooleanType} ушел бы в его операнды, а не в само условие.
     */
    private static void inferenceHeader(@NotNull HasBodyStatement hasBodyStatement, @NotNull ScopeTable scope) {
        switch (hasBodyStatement) {
            case ConditionBranch conditionBranch -> inference(conditionBranch.getCondition(), scope);
            case MatchValueCaseBlock matchValueCaseBlock -> inference(matchValueCaseBlock.getMatchValue(), scope);
            case WhileLoop whileLoop -> inference(whileLoop.getCondition(), scope);
            case DoWhileLoop doWhileLoop -> inference(doWhileLoop.getCondition(), scope);
            case GeneralForLoop generalForLoop -> {
                if (generalForLoop.hasInitializer()) {
                    inference(generalForLoop.getInitializer(), scope);
                }
                if (generalForLoop.hasCondition()) {
                    inference(generalForLoop.getCondition(), scope);
                }
                if (generalForLoop.hasUpdate()) {
                    inference(generalForLoop.getUpdate(), scope);
                }
            }
            case RangeForLoop rangeForLoop -> inference(rangeForLoop.getRange(), scope);
            case ForEachLoop forEachLoop -> {
                inference(forEachLoop.getExpression(), scope);
                inference(forEachLoop.getItem(), scope);
            }
            default -> {
                // У остальных узлов с телом заголовка нет
            }
        }
    }

    public static void inference(@NotNull List<Node> nodes, @NotNull ScopeTable scope) {
        for (var node: nodes) {
            inference(node, scope);
        }
    }

    private static boolean isKnown(Type type) {
        return type != null && !(type instanceof UnknownType);
    }
}
