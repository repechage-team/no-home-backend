package com.ssafy.home.member.dto;

public record MemberLoginRequest(
        String email,
        String password
) {
}
