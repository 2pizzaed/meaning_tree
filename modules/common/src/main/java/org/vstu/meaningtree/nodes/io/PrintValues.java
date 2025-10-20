package org.vstu.meaningtree.nodes.io;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.expressions.literals.StringLiteral;

import java.util.List;
import java.util.Objects;

public class PrintValues extends PrintCommand {
    @Nullable
    public final Expression separator;

    @Nullable
    public final Expression end;

    public PrintValues(
            @NotNull List<Expression> values,
            @Nullable Expression separator,
            @Nullable Expression end
    ) {
        super(values);
        this.separator = separator;
        this.end = end;
    }

    public boolean addsNewLine() {
        return end != null && end instanceof StringLiteral && ((StringLiteral)end).getUnescapedValue().equals("\n");
    }

    public int valuesCount() {
        return arguments.size();
    }

    public boolean hasAnyValues() {
        return valuesCount() > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        PrintValues that = (PrintValues) o;
        return Objects.equals(separator, that.separator) && Objects.equals(end, that.end);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), separator, end);
    }

    public static class PrintValuesBuilder {
        @Nullable
        private Expression _separator = null;

        @Nullable
        private Expression _end = null;

        @Nullable
        private List<Expression> _values;

        public PrintValuesBuilder separateBy(Expression separator) {
            _separator = separator;
            return this;
        }

        public PrintValuesBuilder separateBy(StringLiteral separator) {
            _separator = separator;
            return this;
        }

        public PrintValuesBuilder separateBy(String separator) {
            return separateBy(StringLiteral.fromUnescaped(separator, StringLiteral.Type.NONE));
        }

        public PrintValuesBuilder separateBySpace() {
            return separateBy(" ");
        }

        public PrintValuesBuilder endWith(Expression end) {
            _end = end;
            return this;
        }

        public PrintValuesBuilder endWith(StringLiteral end) {
            _end = end;
            return this;
        }

        public PrintValuesBuilder endWith(String end) {
            return endWith(StringLiteral.fromUnescaped(end, StringLiteral.Type.NONE));
        }

        public PrintValuesBuilder endWithNewline() {
            return endWith("\n");
        }

        public PrintValuesBuilder setValues(List<Expression> values) {
            _values = List.copyOf(values);
            return this;
        }

        public PrintValues build() {
            Objects.requireNonNull(_values);
            return new PrintValues(_values, _separator, _end);
        }
    }
}
