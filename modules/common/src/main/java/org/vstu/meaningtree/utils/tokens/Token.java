package org.vstu.meaningtree.utils.tokens;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.utils.BytePosition;

import java.io.Serializable;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

public class Token implements Serializable {
    public final String value;
    public final TokenType type;

    public Token belongsTo = null;

    protected static AtomicLong _id_generator = new AtomicLong();
    protected static int idCounter = 0;

    protected Object assignedValue = null;
    private long id = _id_generator.incrementAndGet();
    protected BytePosition bytePos = null;

    public Token(String value, TokenType type) {
        this.value = value;
        this.type = type;
    }

    /** Может быть недоступно в случае выбора tokenizeExtended (detailedToken)*/
    @Nullable
    public BytePosition bytePos() {
        return bytePos;
    }

    public boolean setBytePosition(BytePosition bytePos) {
        if (this.bytePos == null) {
            this.bytePos = bytePos;
            return true;
        }
        return false;
    }

    /**
     * Внимание! После вызова этого метода, все новые узлы дерева начнут нумерацию своего id с нуля.
     * Это может привести к конфликтам. Убедитесь, что новые узлы не будут сравниваться по id с предыдущими узлами
     */
    public static void resetIdCounter() {
        System.err.println("Warning! Node counter was reset. It may cause conflicts");
        _id_generator = new AtomicLong();
    }

    public static void setupId(long startId) {
        _id_generator = new AtomicLong(startId);
    }

    @Override
    public String toString() {
        return String.format("token[\"%s\",%s%s]", value, type, assignedValue == null ? "" : ",tag=".concat(assignedValue.toString()));
    }

    /**
     * Сравнивает по всему содержимому
     * @param o - другой объект
     * @return
     */
    public boolean contentEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return Objects.equals(value, token.value) && type == token.type && belongsTo.equals(token.belongsTo) && Objects.equals(assignedValue, token.assignedValue);
    }

    /**
     * Сравнивает объекты. Токены сравниваются данным методом только по id
     * @param o - другой объект
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Token token = (Token) o;
        return id == token.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, type, assignedValue, id, belongsTo);
    }

    public void assignValue(Object tag) {
        assignedValue = tag;
    }

    public Object getAssignedValue() {
        return assignedValue;
    }

    public Token setOwner(Token t) {
        this.belongsTo = t;
        return this;
    }

    @Nullable
    public Token belongsTo() {
        return this.belongsTo;
    }

    public long getId() {
        return id;
    }

    public Token clone() {
        Token copy = new Token(value, type);
        copy.assignValue(assignedValue);
        copy.setOwner(belongsTo);
        copy.setBytePosition(bytePos);
        return copy;
    }

    public Token clone(String newName) {
        Token copy = new Token(newName, type);
        copy.assignValue(assignedValue);
        copy.setOwner(belongsTo);
        copy.setBytePosition(bytePos);
        return copy;
    }

    public OperandToken asOperand() {
        OperandToken copy = new OperandToken(value, type);
        copy.assignValue(assignedValue);
        copy.setOwner(belongsTo);
        copy.setBytePosition(bytePos);
        return copy;
    }
}

