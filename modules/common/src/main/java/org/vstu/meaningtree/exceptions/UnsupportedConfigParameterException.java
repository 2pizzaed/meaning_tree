package org.vstu.meaningtree.exceptions;

public class UnsupportedConfigParameterException extends RuntimeException {
    public UnsupportedConfigParameterException(String paramName) {
        super("Configuration parameter '" + paramName + "' is not supported");
    }
}
