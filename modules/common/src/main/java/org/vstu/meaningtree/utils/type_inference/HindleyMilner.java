package org.vstu.meaningtree.utils.type_inference;

import org.jetbrains.annotations.NotNull;
import org.vstu.meaningtree.nodes.*;
import org.vstu.meaningtree.nodes.declarations.Annotation;
import org.vstu.meaningtree.nodes.declarations.VariableDeclaration;
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
import org.vstu.meaningtree.nodes.expressions.unary.*;
import org.vstu.meaningtree.nodes.interfaces.HasBodyStatement;
import org.vstu.meaningtree.nodes.statements.CompoundStatement;
import org.vstu.meaningtree.nodes.statements.ExpressionStatement;
import org.vstu.meaningtree.nodes.statements.assignments.AssignmentStatement;
import org.vstu.meaningtree.nodes.statements.conditions.IfStatement;
import org.vstu.meaningtree.nodes.statements.conditions.SwitchStatement;
import org.vstu.meaningtree.nodes.types.UnknownType;
import org.vstu.meaningtree.nodes.types.builtin.*;
import org.vstu.meaningtree.nodes.types.containers.*;

import java.util.ArrayList;
import java.util.List;

public class HindleyMilner {

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

        return new DictionaryType(keyType, valueType);
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

        var size = new IntegerLiteral(arrayLiteral.getList().size());
        return new ArrayType(valueType, size);
    }

    @NotNull
    private static List<Type> inference(@NotNull List<Expression> expressions) {
        return expressions
                .stream()
                .map(HindleyMilner::inference)
                .toList();
    }

    @NotNull
    public static Type inference(
            @NotNull SimpleIdentifier identifier,
            @NotNull TypeScope scope) {
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
        else if (first instanceof NumericType && second instanceof NumericType) {
            if (first instanceof FloatType || second instanceof FloatType) {
                return new FloatType();
            }

            return new IntType();
        }
        else if (first instanceof StringType || second instanceof StringType) {
            return new StringType();
        }
        else if (first instanceof BooleanType && second instanceof BooleanType) {
            return new BooleanType();
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

    @NotNull
    private static List<Expression> expressionChildren(@NotNull Expression expression) {
        return expression.allChildren()
                .stream()
                .map(node -> (Expression) node)
                .toList();
    }

    public static void backwardVariableTypeSet(
            @NotNull Expression expression,
            @NotNull TypeScope scope,
            @NotNull Type type) {
        List<Expression> children;
        if (expression instanceof SimpleIdentifier identifier) {
            children = List.of(identifier);
        }
        else {
            children = expressionChildren(expression);
        }

        for (Expression childExpression : children) {

            if (childExpression instanceof SimpleIdentifier identifier) {
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
            else {
                backwardVariableTypeSet(childExpression, scope, type);
            }
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
    public static Type inference(@NotNull BinaryExpression binaryExpression, @NotNull TypeScope scope) {
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
            backwardVariableTypeSet(left, scope, leftType);
            rightType = leftType;
        }

        if (binaryExpression instanceof BinaryComparison || binaryExpression instanceof InstanceOfOp) {
            return new BooleanType();
        }
        else if (binaryExpression instanceof DivOp) {
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
    public static Type inference(@NotNull UnaryExpression unaryExpression, @NotNull TypeScope scope) {
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
        ) {
            if (operandType instanceof UnknownType) {
                Type expressionType = new FloatType();
                backwardVariableTypeSet(argument, scope, expressionType);
                return expressionType;
            }
            return operandType;
        }

        throw new IllegalArgumentException("Unsupported unary expression type: " + unaryExpression.getClass());
    }

    @NotNull
    public static Type inference(@NotNull AssignmentExpression assignmentExpression, @NotNull TypeScope scope) {
        AddOp addOp = new AddOp(assignmentExpression.getLValue(), assignmentExpression.getRValue());
        return inference(addOp, scope);
    }

    @NotNull
    public static Type inference(@NotNull TernaryOperator ternaryOperator, @NotNull TypeScope scope) {
        inference(ternaryOperator.getCondition(), scope);
        Type thenExprType = inference(ternaryOperator.getThenExpr(), scope);
        Type elseExprType = inference(ternaryOperator.getElseExpr(), scope);
        return chooseGeneralType(thenExprType, elseExprType);
    }

    @NotNull
    public static Type inference(@NotNull Range range, @NotNull TypeScope scope) {
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
    public static Type inference(@NotNull Expression expression, @NotNull TypeScope scope) {
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
        return inference(expression, new TypeScope());
    }

    public static void inference(@NotNull AssignmentStatement assignmentStatement, @NotNull TypeScope scope) {
        AssignmentExpression assignmentExpression = new AssignmentExpression(
                assignmentStatement.getLValue(),
                assignmentStatement.getRValue(),
                assignmentStatement.getAugmentedOperator()
        );
        inference(assignmentExpression, scope);
    }

    public static void inference(@NotNull CompoundStatement compoundStatement, @NotNull TypeScope scope) {

        for (var node : compoundStatement.getNodes()) {

            if (node instanceof Statement statement) {
                inference(statement, scope);
            }
            else if (node instanceof Expression expression) {
                inference(expression, scope);
            }
        }
    }

    public static void inference(@NotNull IfStatement ifStatement, @NotNull TypeScope scope) {

        for (var conditionBranch : ifStatement.getBranches()) {
            inference(conditionBranch, scope);
        }

        if (ifStatement.hasElseBranch()) {
            inference(ifStatement.getElseBranch(), scope);
        }
    }

    public static Type inference(@NotNull CompoundComparison compoundComparison, @NotNull TypeScope scope) {

        for (var binaryComparison : compoundComparison.getComparisons()) {
            inference(binaryComparison, scope);
        }

        return new BooleanType();
    }

    public static void inference(@NotNull SwitchStatement switchStatement, @NotNull TypeScope scope) {
        inference(switchStatement.getTargetExpression(), scope);
        for (var caseBranch : switchStatement.getCases()) {
            if (caseBranch != null) {
                inference(caseBranch, scope);
            }
        }
    }

    public static void inference(@NotNull Statement statement, @NotNull TypeScope scope) {
        inference(List.of(statement), scope);
    }

    public static void inference(@NotNull VariableDeclarator variableDeclarator, @NotNull TypeScope scope) {
        if (variableDeclarator.hasInitialization()) {
            inference(variableDeclarator.getRValue(), scope);
        }
        inference(variableDeclarator.getIdentifier(), scope);
    }

    public static void inference(@NotNull VariableDeclaration variableDeclaration, @NotNull TypeScope scope) {
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

        variableDeclaration.setType(chooseGeneralType(types));
    }

    public static void inference(@NotNull Declaration declaration, @NotNull TypeScope scope) {
        switch (declaration) {
            case VariableDeclaration variableDeclaration -> inference(variableDeclaration, scope);
            case DeclarationArgument declarationArgument -> inference(declarationArgument, scope);
            case Annotation annotation -> inference(List.of(annotation.getArguments()), scope);
            default -> throw new IllegalStateException("Unexpected declaration type: " + declaration.getClass());
        }
    }

    public static void inference(@NotNull DeclarationArgument declarationArgument, @NotNull TypeScope scope) {
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

        scope.changeVariableType(declarationArgument.getName(), type);
    }

    public static void inference(@NotNull List<Node> nodes, @NotNull TypeScope scope) {
        for (var node: nodes) {
            switch (node) {
                case ExpressionStatement expressionStatement -> inference(expressionStatement.getExpression(), scope);
                case AssignmentStatement assignmentStatement -> inference(assignmentStatement, scope);
                case CompoundStatement compoundStatement -> inference(compoundStatement, scope);
                case IfStatement ifStatement -> inference(ifStatement, scope);
                case SwitchStatement switchStatement -> inference(switchStatement, scope);
                case VariableDeclaration variableDeclaration -> inference(variableDeclaration, scope);
                case VariableDeclarator variableDeclarator -> inference(variableDeclarator, scope);
                case Expression expression -> inference(expression, scope);
                case HasBodyStatement hasBodyStatement -> {
                    if (hasBodyStatement.getBody() instanceof CompoundStatement compoundStatement) {
                        for (var possibleStatement: compoundStatement.getNodes()) {
                            if (possibleStatement instanceof Statement statement) {
                                inference(statement, scope);
                            }
                        }
                    }
                }
                default -> {
                    /* TODO: проанализировать, какие еще есть варианты для обобщения... */
                }
            }
        }
    }
}
