package org.vstu.meaningtree.nodes.types.builtin;

import org.jetbrains.annotations.Nullable;
import org.vstu.meaningtree.iterators.utils.TreeNode;
import org.vstu.meaningtree.nodes.Expression;
import org.vstu.meaningtree.nodes.Type;
import org.vstu.meaningtree.nodes.interfaces.SequenceType;

import java.util.Objects;

/**
 * Строка как самостоятельный тип, независимо от того, чем она представлена в конкретном языке.
 * <p>
 * Три дополнительных признака описывают то, чем строки языков друг от друга отличаются, и без
 * чего строку нельзя было бы напечатать обратно на языке, откуда она пришла:
 * <ul>
 *   <li>{@link #isCStyleString()} — строка представлена как {@code char *} или {@code char[]},
 *       а не как отдельный строковый класс. Такую строку {@code CppViewer} печатает в исходном
 *       написании и в C++-режиме тоже: {@code std::string} на её месте не собрался бы с
 *       вызовами {@code <string.h>}, которые вокруг неё стоят;</li>
 *   <li>{@link #getMaxLength()} — ёмкость буфера ({@code char buf[64]}). Выражением, а не
 *       числом: в Си размер бывает константным выражением ({@code char buf[SIZE + 1]}), и
 *       {@code ArrayType} хранит размерности так же;</li>
 *   <li>{@link #isImmutable()} — значение неизменяемо (Java {@code String}, Python {@code str}).
 *       Это не то же самое, что {@link #isConst()}: константна бывает переменная, а
 *       неизменяемо — само значение, и {@code String s} в Java переприсваивать можно.</li>
 * </ul>
 */
public class StringType extends Type implements SequenceType {
    public final int charSize;

    /**
     * Ёмкость буфера, если строка живёт в массиве фиксированного размера. {@code null} для
     * указателя, для {@code char s[] = "abc"} без явного размера и для строковых классов.
     */
    @TreeNode @Nullable private Expression maxLength;

    private boolean cStyleString;
    private boolean immutable;

    public StringType() {
        charSize = 16;
    }

    public StringType(int charSize) {
        this.charSize = Math.min(Math.max(charSize, 8), 32);
    }

    public StringType(int charSize, boolean cStyleString, boolean immutable,
                      @Nullable Expression maxLength) {
        this(charSize);
        this.cStyleString = cStyleString;
        this.immutable = immutable;
        this.maxLength = maxLength;
    }

    public boolean isUnicode() {
        return charSize >= 16;
    }

    public int getCharSize() {
        return charSize;
    }

    @Nullable
    public Expression getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(@Nullable Expression maxLength) {
        this.maxLength = maxLength;
    }

    public boolean hasMaxLength() {
        return maxLength != null;
    }

    public boolean isCStyleString() {
        return cStyleString;
    }

    public void setCStyleString(boolean cStyleString) {
        this.cStyleString = cStyleString;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public void setImmutable(boolean immutable) {
        this.immutable = immutable;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        StringType that = (StringType) o;
        return charSize == that.charSize
                && cStyleString == that.cStyleString
                && immutable == that.immutable
                && Objects.equals(maxLength, that.maxLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), charSize, cStyleString, immutable, maxLength);
    }

    @Override
    public StringType clone() {
        StringType clone = (StringType) super.clone();
        clone.maxLength = maxLength == null ? null : maxLength.clone();
        return clone;
    }
}
