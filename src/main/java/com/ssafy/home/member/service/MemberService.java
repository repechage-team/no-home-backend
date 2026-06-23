package com.ssafy.home.member.service;

import com.ssafy.home.member.dto.Member;
import com.ssafy.home.member.dto.MemberResponse;
import com.ssafy.home.member.dto.MemberSignupRequest;
import com.ssafy.home.member.dto.MemberUpdateRequest;
import com.ssafy.home.member.dto.PasswordResetRequest;
import com.ssafy.home.member.mapper.MemberInsertCommand;
import com.ssafy.home.member.mapper.MemberMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordHasher passwordHasher;

    public MemberService(MemberMapper memberMapper, PasswordHasher passwordHasher) {
        this.memberMapper = memberMapper;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public MemberResponse signup(MemberSignupRequest request) {
        String email = required(request == null ? null : request.email(), "email is required.");
        String password = required(request == null ? null : request.password(), "password is required.");
        String name = required(request == null ? null : request.name(), "name is required.");
        String phone = trimToNull(request == null ? null : request.phone());

        memberMapper.selectByEmail(email).ifPresent(member -> {
            throw new MemberException(MemberErrorCode.DUPLICATE_EMAIL, "email already exists.");
        });

        MemberInsertCommand command = new MemberInsertCommand(email, passwordHasher.hash(password), name, phone);
        memberMapper.insertMember(command);
        return findResponseById(command.getMemberId());
    }

    public MemberResponse login(String email, String password) {
        String normalizedEmail = required(email, "email is required.");
        String rawPassword = required(password, "password is required.");
        Member member = memberMapper.selectByEmail(normalizedEmail)
                .orElseThrow(() -> invalidCredentials());
        if (!passwordHasher.matches(rawPassword, member.passwordHash())) {
            throw invalidCredentials();
        }
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse resetPassword(PasswordResetRequest request) {
        String email = required(request == null ? null : request.email(), "email is required.");
        String name = required(request == null ? null : request.name(), "name is required.");
        String phone = trimToNull(request == null ? null : request.phone());
        String newPassword = required(request == null ? null : request.newPassword(), "newPassword is required.");

        Member member = memberMapper.selectByEmail(email)
                .orElseThrow(() -> invalidCredentials());
        if (!member.name().equals(name) || !sameNullable(member.phone(), phone)) {
            throw invalidCredentials();
        }

        int updated = memberMapper.updatePassword(member.memberId(), passwordHasher.hash(newPassword));
        if (updated == 0) {
            throw new MemberException(MemberErrorCode.NOT_FOUND, "member not found.");
        }
        return findResponseById(member.memberId());
    }

    public MemberResponse findCurrentMember(Long memberId) {
        requireMemberId(memberId);
        return findResponseById(memberId);
    }

    public List<MemberResponse> searchMembers(Long currentMemberId, String keyword) {
        requireMemberId(currentMemberId);
        String normalizedKeyword = required(keyword, "keyword is required.");
        return memberMapper.searchMembers(normalizedKeyword).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @Transactional
    public MemberResponse updateCurrentMember(Long memberId, MemberUpdateRequest request) {
        requireMemberId(memberId);
        String name = required(request == null ? null : request.name(), "name is required.");
        String phone = trimToNull(request == null ? null : request.phone());
        int updated = memberMapper.updateCurrentMember(memberId, name, phone);
        if (updated == 0) {
            throw new MemberException(MemberErrorCode.NOT_FOUND, "member not found.");
        }
        return findResponseById(memberId);
    }

    @Transactional
    public void deleteCurrentMember(Long memberId) {
        requireMemberId(memberId);
        int deleted = memberMapper.deleteById(memberId);
        if (deleted == 0) {
            throw new MemberException(MemberErrorCode.NOT_FOUND, "member not found.");
        }
    }

    private MemberResponse findResponseById(Long memberId) {
        Member member = memberMapper.selectById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.NOT_FOUND, "member not found."));
        return MemberResponse.from(member);
    }

    private static void requireMemberId(Long memberId) {
        if (memberId == null) {
            throw new MemberException(MemberErrorCode.UNAUTHENTICATED, "login is required.");
        }
    }

    private static MemberException invalidCredentials() {
        return new MemberException(MemberErrorCode.INVALID_CREDENTIALS, "invalid email or password.");
    }

    private static String required(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new MemberException(MemberErrorCode.VALIDATION, message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean sameNullable(String left, String right) {
        String normalizedLeft = trimToNull(left);
        String normalizedRight = trimToNull(right);
        if (normalizedLeft == null) {
            return normalizedRight == null;
        }
        return normalizedLeft.equals(normalizedRight);
    }
}
