package com.ssafy.home.member.dto;

public record MemberSignupRequest(
        String email,
        String password,
        String name,
        String phone
) {
}
