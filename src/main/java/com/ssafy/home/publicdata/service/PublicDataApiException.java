package com.ssafy.home.publicdata.service;

public class PublicDataApiException extends RuntimeException {

    private final Reason reason;
    private final String resultCode;
    private final String resultMsg;

    public PublicDataApiException(Reason reason, String resultCode, String resultMsg) {
        super("Public data API returned non-success resultCode=" + safe(resultCode)
                + ", reason=" + reason);
        this.reason = reason;
        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
    }

    public Reason reason() {
        return reason;
    }

    public String resultCode() {
        return resultCode;
    }

    public String resultMsg() {
        return resultMsg;
    }

    public enum Reason {
        KEY_INVALID,
        QUOTA,
        PROVIDER_ERROR
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
