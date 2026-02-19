package org.vstu.meaningtree.languages.query;

public record ByteRange(int startByte, int endByte) {
    public ByteRange {
        if (startByte < 0) {
            throw new IllegalArgumentException("Start byte cannot be negative");
        }
        if (endByte < startByte) {
            throw new IllegalArgumentException("End byte cannot be less than start byte");
        }
    }

    public static ByteRange from(int startByte, int endByte) {
        return new ByteRange(startByte, endByte);
    }

    public boolean contains(ByteRange other) {
        return startByte <= other.startByte && endByte >= other.endByte;
    }
}
