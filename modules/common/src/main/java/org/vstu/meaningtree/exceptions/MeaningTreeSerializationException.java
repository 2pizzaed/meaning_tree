package org.vstu.meaningtree.exceptions;

public class MeaningTreeSerializationException extends MeaningTreeException {
    public MeaningTreeSerializationException(Exception e) {
        super(e);
    }

    public MeaningTreeSerializationException(String msg) {
        super(msg);
    }
}
