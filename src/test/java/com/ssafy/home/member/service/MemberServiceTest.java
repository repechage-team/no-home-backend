package com.ssafy.home.member.service;

import com.ssafy.home.member.dto.Member;
import com.ssafy.home.member.dto.MemberResponse;
import com.ssafy.home.member.dto.MemberSignupRequest;
import com.ssafy.home.member.dto.MemberUpdateRequest;
import com.ssafy.home.member.dto.PasswordResetRequest;
import com.ssafy.home.member.mapper.MemberInsertCommand;
import com.ssafy.home.member.mapper.MemberMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberServiceTest {

    @Test
    void signupCreatesMemberWithHashedPasswordAndNoSecretResponse() {
        StubMemberMapper mapper = new StubMemberMapper();
        PasswordHasher passwordHasher = new PasswordHasher();
        MemberService service = new MemberService(mapper, passwordHasher, "");

        MemberResponse response = service.signup(new MemberSignupRequest(
                " user@example.com ", "plain-password", " User ", " 010-0000-0000 "
        ));

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@example.com");
        assertThat(mapper.lastInsert.getPasswordHash()).isNotEqualTo("plain-password");
        assertThat(passwordHasher.matches("plain-password", mapper.lastInsert.getPasswordHash())).isTrue();
    }

    @Test
    void signupFailsWhenEmailAlreadyExists() {
        StubMemberMapper mapper = new StubMemberMapper();
        mapper.save(member(1L, "user@example.com", "hash", "User", null));
        MemberService service = new MemberService(mapper, new PasswordHasher(), "");

        assertThatThrownBy(() -> service.signup(new MemberSignupRequest(
                "user@example.com", "password", "User", null
        )))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void loginSucceedsWithMatchingPassword() {
        StubMemberMapper mapper = new StubMemberMapper();
        PasswordHasher passwordHasher = new PasswordHasher();
        mapper.save(member(1L, "user@example.com", passwordHasher.hash("password"), "User", null));
        MemberService service = new MemberService(mapper, passwordHasher, "");

        MemberResponse response = service.login("user@example.com", "password");

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(response.email()).isEqualTo("user@example.com");
    }

    @Test
    void loginFailsWithWrongPassword() {
        StubMemberMapper mapper = new StubMemberMapper();
        PasswordHasher passwordHasher = new PasswordHasher();
        mapper.save(member(1L, "user@example.com", passwordHasher.hash("password"), "User", null));
        MemberService service = new MemberService(mapper, passwordHasher, "");

        assertThatThrownBy(() -> service.login("user@example.com", "wrong"))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void resetPasswordUpdatesHashAfterIdentityCheck() {
        StubMemberMapper mapper = new StubMemberMapper();
        PasswordHasher passwordHasher = new PasswordHasher();
        mapper.save(member(1L, "user@example.com", passwordHasher.hash("old-password"), "User", "010"));
        MemberService service = new MemberService(mapper, passwordHasher, "");

        MemberResponse response = service.resetPassword(new PasswordResetRequest(
                " user@example.com ", " User ", " 010 ", "new-password"
        ));

        assertThat(response.memberId()).isEqualTo(1L);
        assertThat(mapper.lastUpdatedPasswordMemberId).isEqualTo(1L);
        assertThat(passwordHasher.matches("new-password", mapper.membersById.get(1L).passwordHash())).isTrue();
        assertThat(passwordHasher.matches("old-password", mapper.membersById.get(1L).passwordHash())).isFalse();
    }

    @Test
    void resetPasswordFailsWhenIdentityDoesNotMatch() {
        StubMemberMapper mapper = new StubMemberMapper();
        PasswordHasher passwordHasher = new PasswordHasher();
        mapper.save(member(1L, "user@example.com", passwordHasher.hash("old-password"), "User", "010"));
        MemberService service = new MemberService(mapper, passwordHasher, "");

        assertThatThrownBy(() -> service.resetPassword(new PasswordResetRequest(
                "user@example.com", "Wrong", "010", "new-password"
        )))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void currentMemberLookupUpdateAndDeleteUseCurrentMemberIdOnly() {
        StubMemberMapper mapper = new StubMemberMapper();
        mapper.save(member(1L, "one@example.com", "hash1", "One", null));
        mapper.save(member(2L, "two@example.com", "hash2", "Two", null));
        MemberService service = new MemberService(mapper, new PasswordHasher(), "");

        assertThat(service.findCurrentMember(1L).email()).isEqualTo("one@example.com");
        MemberResponse updated = service.updateCurrentMember(1L, new MemberUpdateRequest("Changed", "010"));
        service.deleteCurrentMember(1L);

        assertThat(updated.name()).isEqualTo("Changed");
        assertThat(mapper.membersById).doesNotContainKey(1L);
        assertThat(mapper.membersById).containsKey(2L);
        assertThat(mapper.lastUpdatedMemberId).isEqualTo(1L);
        assertThat(mapper.lastDeletedMemberId).isEqualTo(1L);
    }

    @Test
    void searchMembersRequiresAdminAndSearchesByKeyword() {
        StubMemberMapper mapper = new StubMemberMapper();
        mapper.save(member(1L, "one@example.com", "hash1", "One", "010-1111"));
        mapper.save(member(2L, "two@example.com", "hash2", "Two", "010-2222"));
        MemberService service = new MemberService(mapper, new PasswordHasher(), "one@example.com");

        List<MemberResponse> results = service.searchMembers(1L, "two");

        assertThat(results).extracting(MemberResponse::email).containsExactly("two@example.com");
        assertThatThrownBy(() -> service.searchMembers(null, "two"))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.UNAUTHENTICATED);
        assertThatThrownBy(() -> service.searchMembers(2L, "one"))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.FORBIDDEN);
    }

    @Test
    void unauthenticatedCurrentMemberAccessIsBlocked() {
        MemberService service = new MemberService(new StubMemberMapper(), new PasswordHasher(), "");

        assertThatThrownBy(() -> service.findCurrentMember(null))
                .isInstanceOf(MemberException.class)
                .extracting("errorCode")
                .isEqualTo(MemberErrorCode.UNAUTHENTICATED);
    }

    private static Member member(Long memberId, String email, String passwordHash, String name, String phone) {
        return new Member(memberId, email, passwordHash, name, phone,
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 10, 0));
    }

    private static class StubMemberMapper implements MemberMapper {
        private final Map<Long, Member> membersById = new HashMap<>();
        private MemberInsertCommand lastInsert;
        private long sequence = 1L;
        private Long lastUpdatedMemberId;
        private Long lastDeletedMemberId;
        private Long lastUpdatedPasswordMemberId;

        @Override
        public int insertMember(MemberInsertCommand command) {
            command.setMemberId(sequence++);
            lastInsert = command;
            save(member(command.getMemberId(), command.getEmail(), command.getPasswordHash(),
                    command.getName(), command.getPhone()));
            return 1;
        }

        @Override
        public Optional<Member> selectById(Long memberId) {
            return Optional.ofNullable(membersById.get(memberId));
        }

        @Override
        public Optional<Member> selectByEmail(String email) {
            return membersById.values().stream()
                    .filter(member -> member.email().equals(email))
                    .findFirst();
        }

        @Override
        public List<Member> searchMembers(String keyword) {
            String normalized = keyword.toLowerCase();
            return membersById.values().stream()
                    .filter(member -> member.email().toLowerCase().contains(normalized)
                            || member.name().toLowerCase().contains(normalized)
                            || (member.phone() != null && member.phone().toLowerCase().contains(normalized)))
                    .toList();
        }

        @Override
        public int updateCurrentMember(Long memberId, String name, String phone) {
            lastUpdatedMemberId = memberId;
            Member existing = membersById.get(memberId);
            if (existing == null) {
                return 0;
            }
            save(member(existing.memberId(), existing.email(), existing.passwordHash(), name, phone));
            return 1;
        }

        @Override
        public int updatePassword(Long memberId, String passwordHash) {
            lastUpdatedPasswordMemberId = memberId;
            Member existing = membersById.get(memberId);
            if (existing == null) {
                return 0;
            }
            save(member(existing.memberId(), existing.email(), passwordHash, existing.name(), existing.phone()));
            return 1;
        }

        @Override
        public int deleteById(Long memberId) {
            lastDeletedMemberId = memberId;
            return membersById.remove(memberId) == null ? 0 : 1;
        }

        private void save(Member member) {
            membersById.put(member.memberId(), member);
            sequence = Math.max(sequence, member.memberId() + 1);
        }
    }
}
