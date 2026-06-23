package com.ssafy.home.notice.mapper;

import com.ssafy.home.notice.dto.Notice;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MybatisTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:notice_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@MapperScan("com.ssafy.home.notice.mapper")
@Sql(scripts = "classpath:schema.sql")
class NoticeMapperTest {

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertSelectUpdateAndDeleteNotice() {
        Long memberId = insertMember();
        NoticeInsertCommand command = new NoticeInsertCommand(memberId, "Before", "Content");

        int inserted = noticeMapper.insertNotice(command);
        int updated = noticeMapper.updateNotice(command.getNoticeId(), "After", "Changed");
        List<Notice> notices = noticeMapper.selectRecent(10);

        assertThat(inserted).isEqualTo(1);
        assertThat(command.getNoticeId()).isNotNull();
        assertThat(updated).isEqualTo(1);
        assertThat(notices).extracting(Notice::title).containsExactly("After");
        assertThat(noticeMapper.selectById(command.getNoticeId())).isPresent()
                .get()
                .extracting(Notice::content)
                .isEqualTo("Changed");
        int deleted = noticeMapper.deleteNotice(command.getNoticeId());
        assertThat(deleted).isEqualTo(1);
        assertThat(noticeMapper.selectById(command.getNoticeId())).isEmpty();
    }

    private Long insertMember() {
        jdbcTemplate.update("""
                INSERT INTO members (email, password_hash, name, phone)
                VALUES ('notice@example.com', 'hash', 'Notice User', '010')
                """);
        return jdbcTemplate.queryForObject("SELECT member_id FROM members WHERE email = 'notice@example.com'", Long.class);
    }
}
