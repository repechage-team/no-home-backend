package com.ssafy.home.interest.service;

public class InterestRegionException extends RuntimeException {

    private final InterestRegionErrorCode errorCode;

    public InterestRegionException(InterestRegionErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InterestRegionErrorCode errorCode() {
        return errorCode;
    }
}
