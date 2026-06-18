package com.ssafy.home.member.auth;

public final class AuthenticatedMember {

    public static final String REQUEST_ATTRIBUTE = AuthenticatedMember.class.getName() + ".memberId";

    private AuthenticatedMember() {
    }
}
