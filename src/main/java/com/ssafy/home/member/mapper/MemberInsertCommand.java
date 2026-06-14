package com.ssafy.home.member.mapper;

public class MemberInsertCommand {

    private Long memberId;
    private final String email;
    private final String passwordHash;
    private final String name;
    private final String phone;

    public MemberInsertCommand(String email, String passwordHash, String name, String phone) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phone = phone;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }
}
