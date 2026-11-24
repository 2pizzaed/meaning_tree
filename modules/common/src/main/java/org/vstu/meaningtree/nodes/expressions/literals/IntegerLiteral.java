package org.vstu.meaningtree.nodes.expressions.literals;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

public class IntegerLiteral extends NumericLiteral {
    public enum Representation {
        DECIMAL,
        HEX,
        BINARY,
        OCTAL
    }

    private boolean _isLongNumber = false;
    private boolean _isUnsigned = false;
    private final long _value;
    private Representation _repr;

    public IntegerLiteral(String s) {
        _value = parseValue(s, true);
    }

    public IntegerLiteral(String s, boolean isLong, boolean isUnsigned) {
        _isLongNumber = isLong;
        _isUnsigned = isUnsigned;
        _value = parseValue(s, true);
    }

    public IntegerLiteral(long value) {
        _value = parseValue(Long.toString(value), false);
        _isLongNumber = true;
        _isUnsigned = false;
    }

    public long parseValue(String s, boolean parseModifiers) {
        int base = 10;
        s = s.toLowerCase();
        if (s.startsWith("0b")) {
            base = 2;
            _repr = Representation.BINARY;
        } else if (s.startsWith("0o")) {
            base = 8;
            _repr = Representation.OCTAL;
        } else if (s.startsWith("0x")) {
            base = 16;
            _repr = Representation.HEX;
        } else {
            _repr = Representation.DECIMAL;
        }
        s = StringUtils.removeStart(s, "0b");
        s = StringUtils.removeStart(s, "0o");
        s = StringUtils.removeStart(s, "0x");

        if (parseModifiers) {
            // В c++ литерал unsigned long может быть записан "75ul" или "75lu"
            // Поэтому проверяем не окончание строки, а содержание
            _isUnsigned = s.contains("u");
            _isLongNumber = s.contains("l");
            s = StringUtils.remove(s, "l");
            s = StringUtils.remove(s, "u");
        }

        return Long.parseLong(s, base);
    }

    @Override
    public Number getValue() {
        return _value;
    }

    public long getLongValue() {
        return _value;
    }

    @Override
    public String getStringValue(boolean outputModifiers) {
        StringBuilder builder = new StringBuilder();
        int base = switch (_repr) {
            case DECIMAL -> 10;
            case HEX -> 16;
            case BINARY -> 2;
            case OCTAL -> 8;
        };
        switch (base) {
            case 2 -> builder.append("0b");
            case 8 -> builder.append("0o");
            case 16 -> builder.append("0x");
        }
        builder.append(Long.toString(getLongValue(), base).toUpperCase());
        if (outputModifiers) {
            if (_isLongNumber) {
                builder.append("L");
            }
        }
        return builder.toString();
    }

    public Representation getIntegerRepresentation() {
        return _repr;
    }

    public boolean isLong() {
        return _isLongNumber;
    }

    public boolean isUnsigned() { return _isUnsigned; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        IntegerLiteral that = (IntegerLiteral) o;
        return _value == that._value && _repr == that._repr && _isLongNumber == that._isLongNumber && _isUnsigned == that._isUnsigned;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), _value, _isLongNumber, _isUnsigned, enumName(_repr));
    }

    @Override
    public String toString() {
        return getStringValue(false);
    }
}
