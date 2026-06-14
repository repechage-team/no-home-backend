package com.ssafy.home.member.mapper;

import com.ssafy.home.member.dto.Member;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.jdbc.Sql;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MybatisTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:member_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@MapperScan("com.ssafy.home.member.mapper")
@Sql(scripts = "classpath:schema.sql")
class MemberMapperTest {

    @Autowired
    private MemberMapper memberMapper;

    @Test
    void insertMemberCanBeSelectedByEmail() {
        MemberInsertCommand command = insertMember("email-select@example.com", "hash-email", "Email User", "010-1111-1111");

        Optional<Member> selected = memberMapper.selectByEmail("email-select@example.com");

        assertThat(command.getMemberId()).isNotNull();
        assertThat(selected).isPresent();
        assertThat(selected.get().memberId()).isEqualTo(command.getMemberId());
        assertThat(selected.get().email()).isEqualTo("email-select@example.com");
        assertThat(selected.get().passwordHash()).isEqualTo("hash-email");
        assertThat(selected.get().name()).isEqualTo("Email User");
        assertThat(selected.get().phone()).isEqualTo("010-1111-1111");
    }

    @Test
    void insertMemberCanBeSelectedById() {
        MemberInsertCommand command = insertMember("id-select@example.com", "hash-id", "Id User", null);

        Optional<Member> selected = memberMapper.selectById(command.getMemberId());

        assertThat(selected).isPresent();
        assertThat(selected.get().memberId()).isEqualTo(command.getMemberId());
        assertThat(selected.get().email()).isEqualTo("id-select@example.com");
        assertThat(selected.get().passwordHash()).isEqualTo("hash-id");
        assertThat(selected.get().phone()).isNull();
    }

    @Test
    void updateCurrentMemberChangesEditableFields() {
        MemberInsertCommand command = insertMember("update@example.com", "hash-update", "Before", "010-before");

        int updated = memberMapper.updateCurrentMember(command.getMemberId(), "After", "010-after");

        Optional<Member> selected = memberMapper.selectById(command.getMemberId());
        assertThat(updated).isEqualTo(1);
        assertThat(selected).isPresent();
        assertThat(selected.get().name()).isEqualTo("After");
        assertThat(selected.get().phone()).isEqualTo("010-after");
        assertThat(selected.get().email()).isEqualTo("update@example.com");
        assertThat(selected.get().passwordHash()).isEqualTo("hash-update");
    }

    @Test
    void deleteByIdRemovesMember() {
        MemberInsertCommand command = insertMember("delete@example.com", "hash-delete", "Delete User", null);

        int deleted = memberMapper.deleteById(command.getMemberId());

        assertThat(deleted).isEqualTo(1);
        assertThat(memberMapper.selectById(command.getMemberId())).isEmpty();
        assertThat(memberMapper.selectByEmail("delete@example.com")).isEmpty();
    }

    @Test
    void duplicateEmailInsertFailsByUniqueConstraint() {
        insertMember("duplicate@example.com", "hash-one", "One", null);

        MemberInsertCommand duplicate = new MemberInsertCommand("duplicate@example.com", "hash-two", "Two", null);

        assertThatThrownBy(() -> memberMapper.insertMember(duplicate))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private MemberInsertCommand insertMember(String email, String passwordHash, String name, String phone) {
        MemberInsertCommand command = new MemberInsertCommand(email, passwordHash, name, phone);
        int inserted = memberMapper.insertMember(command);
        assertThat(inserted).isEqualTo(1);
        assertThat(command.getMemberId()).isNotNull();
        return command;
    }
}
