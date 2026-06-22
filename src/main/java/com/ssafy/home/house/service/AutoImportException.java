package com.ssafy.home.house.service;

public class AutoImportException extends RuntimeException {

    private final Reason reason;

    public AutoImportException(String message, Throwable cause) {
        this(Reason.UNKNOWN, message, cause);
    }

    public AutoImportException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        KEY_MISSING,
        KEY_INVALID,
        QUOTA,
        TIMEOUT,
        PROVIDER_ERROR,
        UNKNOWN
    }
}
