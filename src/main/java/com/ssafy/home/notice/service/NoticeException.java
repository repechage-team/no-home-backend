package com.ssafy.home.notice.service;

public class NoticeException extends RuntimeException {

    private final NoticeErrorCode errorCode;

    public NoticeException(NoticeErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NoticeErrorCode errorCode() {
        return errorCode;
    }
}
