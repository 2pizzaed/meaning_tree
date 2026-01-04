package org.vstu.meaningtree.exceptions;

public class UnsupportedSerializationException extends MeaningTreeSerializationException {
    // Reason: serialization are not planned for support in future

    public UnsupportedSerializationException(String message) {
        super(message);
    }

    public UnsupportedSerializationException() {
        super("Serialization for this feature is unsupported");
    }
}
