package com.ssafy.home.member.dto;

public record PasswordResetRequest(
        String email,
        String name,
        String phone,
        String newPassword
) {
}
