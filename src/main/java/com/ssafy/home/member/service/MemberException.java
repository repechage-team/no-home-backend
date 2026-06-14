package com.ssafy.home.member.service;

public class MemberException extends RuntimeException {

    private final MemberErrorCode errorCode;

    public MemberException(MemberErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MemberErrorCode errorCode() {
        return errorCode;
    }
}
