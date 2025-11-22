package org.vstu.meaningtree.exceptions;

public class MeaningTreeConfigException extends MeaningTreeException {
    public MeaningTreeConfigException(Exception e) {
        super(e);
    }

    public MeaningTreeConfigException(String msg) {
        super(msg);
    }
}
