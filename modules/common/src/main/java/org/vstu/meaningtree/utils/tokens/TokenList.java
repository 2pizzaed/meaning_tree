package org.vstu.meaningtree.utils.tokens;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.vstu.meaningtree.utils.ObservableArrayList;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Нужен для представления последовательного представления списка токенов, а не выборки токенов
 */
public class TokenList extends ObservableArrayList<Token> {

    public TokenList() {
        super();
    }

    public TokenList(Collection<? extends Token> tokens) {
        super(tokens);
    }

    public TokenList subtract(TokenGroup group) {
        TokenList copy = clone();
        copy.subList(group.start, group.stop).clear();
        return copy;
    }

    public TokenList replace(TokenGroup group, Token fillValue) {
        TokenList copy = clone();
        for (int i = group.start; i < group.stop; i++) {
            copy.set(i, fillValue);
        }
        return copy;
    }

    public void setAll(int start, List<? extends Token> tokens) {
        for (int i = start; i < start + tokens.size(); i++) {
            set(i, tokens.get(i - start));
        }
    }

    public Pair<Integer, Token> findLeftmostToken(String value) {
        int index = stream().map((Token t) -> t.value).toList().indexOf(value);
        return new ImmutablePair<>(index, get(index));
    }

    public Pair<Integer, Token> findRightmostToken(String value) {
        int reversedResult = reversed().stream().map((Token t) -> t.value).toList().indexOf(value);
        int index = reversedResult >= size() ? -1 : size() - reversedResult;
        return new ImmutablePair<>(index, get(index));
    }

    public ComplexOperatorToken isInComplex(int tokenIndex) {
        for (int i = tokenIndex; i >= 0; i--) {
            if (get(i) instanceof ComplexOperatorToken complex && complex.isOpening()) {
                if (tokenIndex > i && tokenIndex < findClosingComplex(i)) {
                    return complex;
                }
            }
        }
        return null;
    }

    public Pair<Integer, Integer> getEnclosingParentheses(int operatorToken) {
        if (!(get(operatorToken) instanceof OperatorToken)) {
            return ImmutablePair.of(-1, -1);
        }
        for (int i = operatorToken; i >= 0; i--) {
            if (get(i).type == TokenType.OPENING_BRACE) {
                int brace = 1;
                int closing = -1;
                int j = i + 1;
                for (;j < size() && brace != 0; j++) {
                    if (get(j).type == TokenType.OPENING_BRACE) {
                        brace++;
                    } else if (get(j).type == TokenType.CLOSING_BRACE) {
                        closing = j;
                        brace--;
                    }
                }
                if (brace == 0 && operatorToken > i && operatorToken < j) {
                    return ImmutablePair.of(i, closing);
                }
            }
        }
        return ImmutablePair.of(-1, -1);
    }

    public void setMetadata(OperatorToken token, OperandPosition pos) {
        for (int i = 0; i < size(); i++) {
            Token t = get(i);
            if (!(t instanceof OperandToken)) {
                set(i, new OperandToken(t.value, t.type));
            }
            OperandToken op = ((OperandToken)get(i));
            if (op.operandOf() == null) {
                op.setMetadata(token, pos);
            }
        }
    }

    public void assignValue(Object tag) {
        for (Token t : this) {
            if (t.getAssignedValue() == null)
                t.assignValue(tag);
        }
    }

    public int findComplex(int tokenIndex, int complexPos) {
        assert get(tokenIndex) instanceof ComplexOperatorToken;
        ComplexOperatorToken complex = (ComplexOperatorToken) get(tokenIndex);
        int nesting = 1;
        int start = complex.positionOfToken <= complexPos ? tokenIndex + 1 : tokenIndex - 1;
        int stop = complex.positionOfToken <= complexPos ? size() : 0;
        int step = complex.positionOfToken <= complexPos ? 1 : -1;
        for (int i = start; (complex.positionOfToken <= complexPos) == (i < stop); i += step){
            if (get(i) instanceof ComplexOperatorToken currentComplex) {
                boolean increaseCondition = currentComplex.positionOfToken != complexPos;
                boolean decreaseCondition = currentComplex.positionOfToken == complexPos;

                if (currentComplex.isEqualComplex(complex) &&
                        increaseCondition
                ) {
                    nesting += 1;
                } else if (currentComplex.isEqualComplex(complex) && decreaseCondition) {
                    nesting -= 1;
                    if (nesting <= 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    public int findClosingComplex(int tokenIndex) {
        assert get(tokenIndex) instanceof ComplexOperatorToken;
        ComplexOperatorToken complex = (ComplexOperatorToken) get(tokenIndex);
        return findComplex(tokenIndex, complex.getClosingPosition());
    }

    public int findOpeningComplex(int tokenIndex) {
        return findComplex(tokenIndex, 0);
    }


    public TokenList clone() {
        return (TokenList) super.clone();
    }

    public OperandPosition isOperandInOperatorOperandsHierarchy(int operandPos, int operatorPos) {
        assert get(operandPos) instanceof OperandToken;
        assert get(operatorPos) instanceof OperatorToken;
        OperandToken operand = (OperandToken) get(operandPos);
        OperandPosition pos = null;
        boolean flag = false;
        while (operand.operandOf() != null) {
            if (operand.operandOf().equals(get(operatorPos))) {
                flag = true;
                pos = operand.operandPosition();
                break;
            }
            operand = operand.operandOf();
        }
        return flag ? pos : null;
    }

    public Map<OperandPosition, TokenList> findOperandsAsList(int opIndexToken) {
        OperandToken op = (OperandToken) get(opIndexToken);
        Map<OperandPosition, TokenList> result = new HashMap<>();
        for (int i = 0; i < size(); i++) {
            if (get(i) instanceof OperandToken operand && operand.operandOf() != null && operand.operandOf().equals(op)) {
                if (!result.containsKey(operand.operandPosition())) {
                    result.put(operand.operandPosition(), new TokenList());
                }
                result.get(operand.operandPosition()).add(operand);
            }
        }
        return result;
    }

    public Map<OperandPosition, TokenGroup> findOperands(int opIndexToken) {
        OperandToken op = (OperandToken) get(opIndexToken);
        Map<OperandPosition, TokenGroup> result = new HashMap<>();
        int i = 0;
        int start = -1;
        int stop = -1;
        OperandPosition oldPos = null;
        while (i < size()) {
            OperandPosition pos;
            if (get(i) instanceof OperandToken operand && (pos = isOperandInOperatorOperandsHierarchy(i, opIndexToken)) != null) {
                if (oldPos != null && !oldPos.equals(pos)) {
                    start = i;
                }
                if (start == -1) {
                    start = i;
                }
                stop = i + 1;
                while (stop < size() && get(stop) instanceof OperandToken && isOperandInOperatorOperandsHierarchy(stop, opIndexToken) == pos) {
                    stop = i + 1;
                    i++;
                }
                result.put(pos, new TokenGroup(start, stop, this));
                oldPos = pos;
            }
            i++;
        }
        return result;
    }
}
