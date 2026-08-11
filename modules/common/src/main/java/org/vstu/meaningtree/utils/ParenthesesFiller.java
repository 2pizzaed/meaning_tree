package org.vstu.meaningtree.utils;

import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.BinaryExpression;
import org.vstu.meaningtree.nodes.expressions.ParenthesizedExpression;
import org.vstu.meaningtree.nodes.expressions.UnaryExpression;
import org.vstu.meaningtree.nodes.expressions.calls.FunctionCall;
import org.vstu.meaningtree.nodes.expressions.calls.MethodCall;
import org.vstu.meaningtree.nodes.expressions.identifiers.QualifiedIdentifier;
import org.vstu.meaningtree.nodes.expressions.other.CastTypeExpression;
import org.vstu.meaningtree.nodes.expressions.other.IndexExpression;
import org.vstu.meaningtree.nodes.expressions.other.MemberAccess;
import org.vstu.meaningtree.nodes.expressions.other.TernaryOperator;
import org.vstu.meaningtree.utils.tokens.OperatorArity;
import org.vstu.meaningtree.utils.tokens.OperatorAssociativity;
import org.vstu.meaningtree.utils.tokens.OperatorToken;
import org.vstu.meaningtree.utils.tokens.OperatorTokenPosition;

import java.util.function.Function;
import java.util.function.Predicate;

public class ParenthesesFiller {
    // Warning: Приоритет в токенах от высшего (1) к низшему!!

    private Function<Expression, OperatorToken> _mapper;
    private Predicate<String> _isOperatorText;

    public ParenthesesFiller(Function<Expression, OperatorToken> mapperNodeToOperatorToken) {
        this(mapperNodeToOperatorToken, text -> false);
    }

    /**
     * @param isOperatorText есть ли в языке оператор, который печатается указанным текстом.
     *                       Нужно, чтобы поймать склейку соседних знаков: см. {@link #gluesIntoOtherOperator}.
     */
    public ParenthesesFiller(Function<Expression, OperatorToken> mapperNodeToOperatorToken,
                             Predicate<String> isOperatorText) {
        this._mapper = mapperNodeToOperatorToken;
        this._isOperatorText = isOperatorText;
    }

    public IndexExpression process(IndexExpression expr) {
        OperatorToken tok = _mapper.apply(expr);
        Expression arg = prepareOperand(tok, expr.getExpression());
        if (!arg.uniquenessEquals(expr.getExpression())) {
            expr = expr.clone();
            replaceOrThrow(expr, "expression", arg);
        }
        return expr;
    }

    public CastTypeExpression process(CastTypeExpression expr) {
        OperatorToken tok = _mapper.apply(expr);
        Expression arg = prepareOperand(tok, expr.getValue());
        if (!arg.uniquenessEquals(expr.getValue())) {
            expr = expr.clone();
            replaceOrThrow(expr, "value", arg);
        }
        return expr;
    }

    public MemberAccess process(MemberAccess expr) {
        OperatorToken tok = _mapper.apply(expr);
        Expression arg = prepareOperand(tok, expr.getExpression());
        if (!arg.uniquenessEquals(expr.getExpression())) {
            expr = expr.clone();
            replaceOrThrow(expr, "expression", arg);
        }
        return expr;
    }

    public MethodCall process(MethodCall expr) {
        if (expr.getObject() == null) {
            return expr;
        }
        OperatorToken tok = _mapper.apply(expr);
        Expression arg = prepareOperand(tok, expr.getObject());
        if (!arg.uniquenessEquals(expr.getObject())) {
            expr = expr.clone();
            replaceOrThrow(expr, "object", arg);
        }
        return expr;
    }

    public FunctionCall processForPython(FunctionCall expr) {
        if (expr instanceof MethodCall call) {
            return process(call);
        }
        if (expr.getFunction() == null) {
            return expr;
        }
        OperatorToken tok = _mapper.apply(expr);
        Expression arg = prepareOperand(tok, expr.getFunction());
        if (!arg.uniquenessEquals(expr.getFunction())) {
            expr = expr.clone();
            replaceOrThrow(expr, "function", arg);
        }
        return expr;
    }

    public TernaryOperator process(TernaryOperator expr) {
        OperatorToken tok = _mapper.apply(expr);
        Expression cond = prepareOperand(tok, expr.getCondition());
        Expression then = prepareOperand(tok, expr.getThenExpr());
        Expression elseBranch = prepareOperand(tok, expr.getElseExpr());
        expr = expr.clone();
        if (!expr.getCondition().uniquenessEquals(cond)) replaceOrThrow(expr, "condition", cond);
        if (!expr.getThenExpr().uniquenessEquals(then)) replaceOrThrow(expr, "thenExpr", then);
        if (!expr.getElseExpr().uniquenessEquals(elseBranch)) replaceOrThrow(expr, "elseExpr", elseBranch);
        return expr;
    }

    public BinaryExpression process(BinaryExpression expr) {
        OperatorToken tok = _mapper.apply(expr);
        if (tok == null) {
            return expr;
        }

        Pair<Expression, Expression> pair = prepareBinary(tok, expr.getLeft(), expr.getRight());
        if (!pair.getLeft().uniquenessEquals(expr.getLeft()) || !pair.getRight().uniquenessEquals(expr.getRight())) {
            expr = expr.clone();
            replaceOrThrow(expr, "left", pair.getLeft());
            replaceOrThrow(expr, "right", pair.getRight());
        }

        return expr;
    }

    public UnaryExpression process(UnaryExpression expr) {
        OperatorToken tok = _mapper.apply(expr);
        Expression arg = prepareOperand(tok, expr.getArgument());
        if (!arg.uniquenessEquals(expr.getArgument())) {
            expr = expr.clone();
            replaceOrThrow(expr, "argument", arg);
        }
        return expr;
    }

    public QualifiedIdentifier process(QualifiedIdentifier qual) {
        OperatorToken tok = _mapper.apply(qual);
        Expression arg = prepareOperand(tok, qual.getScope());
        if (!arg.uniquenessEquals(qual.getScope())) {
            qual = qual.clone();
            replaceOrThrow(qual, "scope", arg);
        }
        return qual;
    }

    private void replaceOrThrow(Expression owner, String fieldName, Expression replacement) {
        ReplaceResult result = owner.replace(owner.getFieldDescriptor(fieldName), replacement);
        if (!result.isSuccess()) {
            throw new IllegalStateException("Failed to replace field '%s' in %s: %s".formatted(
                    fieldName,
                    owner.getNodeUniqueName(),
                    result.message()
            ));
        }
    }

    private Expression prepareOperand(OperatorToken tok, Expression arg) {
        OperatorToken argTok = _mapper.apply(arg);
        if (tok == null || argTok == null) {
            return arg;
        }
        if (argTok.precedence > tok.precedence ||
                (arg instanceof BinaryExpression && tok.arity == OperatorArity.TERNARY && argTok.precedence >= tok.precedence) ||
                (arg instanceof UnaryExpression && tok.arity != OperatorArity.UNARY) ||
                (arg instanceof BinaryExpression && tok.arity == OperatorArity.UNARY) ||
                gluesIntoOtherOperator(tok, arg, argTok)
        ) {
            arg = new ParenthesizedExpression(arg);
        }
        return arg;
    }

    /**
     * Склеится ли знак оператора со знаком его аргумента в знак другого оператора.
     * <p>
     * Приоритет здесь ни при чём: {@code -} и {@code -a} имеют одинаковый приоритет и по
     * приоритету скобок не требуют, но печатаются подряд и дают {@code --a} — префиксный
     * декремент, то есть другой смысл. То же с {@code +(+a)} → {@code ++a}. А вот
     * {@code !(!a)} склейки не образует: {@code !!} оператором не является, и {@code !!a}
     * читается верно.
     * <p>
     * Проверка только для унарного оператора над унарным аргументом: там знаки печатаются
     * вплотную. У бинарных операторов viewer'ы разделяют операнды пробелами.
     */
    private boolean gluesIntoOtherOperator(OperatorToken tok, Expression arg, OperatorToken argTok) {
        if (tok.arity != OperatorArity.UNARY || !(arg instanceof UnaryExpression)) {
            return false;
        }
        if (tok.tokenPos != OperatorTokenPosition.PREFIX || argTok.tokenPos != OperatorTokenPosition.PREFIX) {
            return false;
        }
        if (tok.value.isEmpty() || argTok.value.isEmpty()) {
            return false;
        }
        return _isOperatorText.test(tok.value + argTok.value.charAt(0));
    }

    private Pair<Expression, Expression> prepareBinary(OperatorToken tok, Expression left, Expression right) {
        OperatorToken tokLeft = _mapper.apply(left);
        OperatorToken tokRight = _mapper.apply(right);


        if (tokLeft != null && tokLeft.precedence > tok.precedence && !(left instanceof ParenthesizedExpression)) {
            left = new ParenthesizedExpression(left);
        }

        if (tokRight != null && tokRight.precedence > tok.precedence && !(right instanceof ParenthesizedExpression)) {
            right = new ParenthesizedExpression(right);
        }

        if (tokRight != null && right instanceof BinaryExpression rightBinOp) {
            if (tok.precedence == tokRight.precedence
                    && tok.assoc == tokRight.assoc && tok.assoc == OperatorAssociativity.LEFT
            ) {
                right = new ParenthesizedExpression(rightBinOp);
            }
        }

        if (tokLeft != null && left instanceof BinaryExpression leftBinOp) {
            if (tok.precedence == tokLeft.precedence
                    && tok.assoc == tokLeft.assoc && tok.assoc == OperatorAssociativity.RIGHT
            ) {
                left = new ParenthesizedExpression(leftBinOp);
            }
        }

        return Pair.of(left, right);
    }
}
