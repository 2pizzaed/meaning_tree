package org.vstu.meaningtree.exceptions;

public class UnsupportedConfigParameterException extends MeaningTreeConfigException {
    public UnsupportedConfigParameterException(String paramName) {
        super("Configuration parameter '" + paramName + "' is not supported");
    }
}
