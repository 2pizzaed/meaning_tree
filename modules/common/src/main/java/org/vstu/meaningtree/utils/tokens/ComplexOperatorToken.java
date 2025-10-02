package org.vstu.meaningtree.utils.tokens;

import java.util.List;
import java.util.Objects;

public class ComplexOperatorToken extends OperatorToken {
    public final int positionOfToken;
    public final List<String> complexTokenValues;

    ComplexOperatorToken(int positionOfToken, String value,
                         TokenType type,
                         OperatorTokenPosition tokenPos,
                         int precedence,
                         OperatorAssociativity assoc,
                         OperatorArity arity,
                         boolean isStrictOrder, String[] complexTokenValues) {
        super(value, type, precedence, assoc, arity, isStrictOrder, tokenPos);
        assert complexTokenValues.length >= 2;
        this.positionOfToken = positionOfToken;
        this.complexTokenValues = List.of(complexTokenValues);
    }

    public boolean isOpening() {
        return positionOfToken == 0;
    }

    public boolean isClosing() {
        return positionOfToken == complexTokenValues.size() - 1;
    }

    @Override
    public boolean contentEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ComplexOperatorToken that = (ComplexOperatorToken) o;
        return positionOfToken == that.positionOfToken && Objects.equals(complexTokenValues, that.complexTokenValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), positionOfToken, complexTokenValues);
    }

    public boolean isEqualComplex(ComplexOperatorToken that) {
        return that.complexTokenValues.equals(this.complexTokenValues) && precedence == that.precedence
                && isStrictOrder == that.isStrictOrder && assoc == that.assoc
                && arity == that.arity && tokenPos == that.tokenPos;
    }

    public int getClosingPosition() {
        return complexTokenValues.size() - 1;
    }

    public ComplexOperatorToken clone() {
        ComplexOperatorToken copy = new ComplexOperatorToken(positionOfToken,
                value, type, tokenPos, precedence, assoc, arity, isStrictOrder, complexTokenValues.toArray(new String[0])
        );
        copy.assignValue(assignedValue);
        copy.setMetadata(operandOf, operandPos);
        return copy;
    }

    public ComplexOperatorToken clone(String newName) {
        ComplexOperatorToken copy = new ComplexOperatorToken(positionOfToken,
                newName, type, tokenPos, precedence, assoc, arity, isStrictOrder, complexTokenValues.toArray(new String[0])
        );
        copy.assignValue(assignedValue);
        copy.setMetadata(operandOf, operandPos);
        return copy;
    }
}
