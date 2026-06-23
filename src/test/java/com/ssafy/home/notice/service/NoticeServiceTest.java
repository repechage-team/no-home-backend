package com.ssafy.home.notice.service;

import com.ssafy.home.member.dto.Member;
import com.ssafy.home.member.mapper.MemberMapper;
import com.ssafy.home.notice.dto.Notice;
import com.ssafy.home.notice.dto.NoticeRequest;
import com.ssafy.home.notice.dto.NoticeResponse;
import com.ssafy.home.notice.mapper.NoticeInsertCommand;
import com.ssafy.home.notice.mapper.NoticeMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NoticeServiceTest {

    @Test
    void findRecentMarksNoticesEditableOnlyForAdmin() {
        NoticeMapper noticeMapper = mock(NoticeMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        when(noticeMapper.selectRecent(10)).thenReturn(List.of(
                notice(1L, 1L, "Mine"),
                notice(2L, 2L, "Other")
        ));
        when(memberMapper.selectById(1L)).thenReturn(Optional.of(member(1L, "admin@example.com")));
        when(memberMapper.selectById(2L)).thenReturn(Optional.of(member(2L, "user@example.com")));
        NoticeService service = new NoticeService(noticeMapper, memberMapper, "admin@example.com");

        List<NoticeResponse> adminNotices = service.findRecent(null, 1L);
        List<NoticeResponse> userNotices = service.findRecent(null, 2L);

        assertThat(adminNotices).hasSize(2);
        assertThat(adminNotices).allMatch(NoticeResponse::editable);
        assertThat(userNotices).noneMatch(NoticeResponse::editable);
    }

    @Test
    void createValidatesLoginAdminAndRequiredFields() {
        NoticeMapper noticeMapper = mock(NoticeMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        when(memberMapper.selectById(1L)).thenReturn(Optional.of(member(1L, "admin@example.com")));
        when(memberMapper.selectById(2L)).thenReturn(Optional.of(member(2L, "user@example.com")));
        NoticeService service = new NoticeService(noticeMapper, memberMapper, "admin@example.com");

        assertThatThrownBy(() -> service.create(null, new NoticeRequest("Title", "Content")))
                .isInstanceOf(NoticeException.class)
                .hasMessage("login required.");
        assertThatThrownBy(() -> service.create(2L, new NoticeRequest("Title", "Content")))
                .isInstanceOf(NoticeException.class)
                .hasMessage("admin permission is required.");
        assertThatThrownBy(() -> service.create(1L, new NoticeRequest(" ", "Content")))
                .isInstanceOf(NoticeException.class)
                .hasMessage("title is required.");
        assertThatThrownBy(() -> service.create(1L, new NoticeRequest("Title", "")))
                .isInstanceOf(NoticeException.class)
                .hasMessage("content is required.");
    }

    @Test
    void createInsertsAndReturnsCreatedNotice() {
        NoticeMapper noticeMapper = mock(NoticeMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        when(memberMapper.selectById(1L)).thenReturn(Optional.of(member(1L, "admin@example.com")));
        when(noticeMapper.insertNotice(any(NoticeInsertCommand.class))).thenAnswer(invocation -> {
            NoticeInsertCommand command = invocation.getArgument(0);
            java.lang.reflect.Field field = NoticeInsertCommand.class.getDeclaredField("noticeId");
            field.setAccessible(true);
            field.set(command, 3L);
            return 1;
        });
        when(noticeMapper.selectById(3L)).thenReturn(Optional.of(notice(3L, 1L, "Title")));
        NoticeService service = new NoticeService(noticeMapper, memberMapper, "admin@example.com");

        NoticeResponse response = service.create(1L, new NoticeRequest(" Title ", " Content "));

        assertThat(response.noticeId()).isEqualTo(3L);
        assertThat(response.title()).isEqualTo("Title");
        assertThat(response.editable()).isTrue();
    }

    @Test
    void updateAndDeleteRequireAdminNotice() {
        NoticeMapper noticeMapper = mock(NoticeMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        when(memberMapper.selectById(1L)).thenReturn(Optional.of(member(1L, "admin@example.com")));
        when(noticeMapper.updateNotice(eq(7L), eq("After"), eq("Body"))).thenReturn(1);
        when(noticeMapper.selectById(7L)).thenReturn(Optional.of(notice(7L, 1L, "After")));
        when(noticeMapper.deleteNotice(7L)).thenReturn(1);
        NoticeService service = new NoticeService(noticeMapper, memberMapper, "admin@example.com");

        NoticeResponse updated = service.update(1L, 7L, new NoticeRequest("After", "Body"));
        service.delete(1L, 7L);

        assertThat(updated.noticeId()).isEqualTo(7L);
        verify(noticeMapper).deleteNotice(7L);
    }

    @Test
    void updateReportsNotFoundWhenMapperDoesNotChangeRows() {
        NoticeMapper noticeMapper = mock(NoticeMapper.class);
        MemberMapper memberMapper = mock(MemberMapper.class);
        when(memberMapper.selectById(1L)).thenReturn(Optional.of(member(1L, "admin@example.com")));
        when(noticeMapper.updateNotice(eq(7L), eq("After"), eq("Body"))).thenReturn(0);
        NoticeService service = new NoticeService(noticeMapper, memberMapper, "admin@example.com");

        assertThatThrownBy(() -> service.update(1L, 7L, new NoticeRequest("After", "Body")))
                .isInstanceOf(NoticeException.class)
                .hasMessage("notice not found.");
    }

    private static Notice notice(Long noticeId, Long memberId, String title) {
        return new Notice(noticeId, memberId, title, "Content",
                LocalDateTime.of(2026, 6, 23, 10, 0),
                LocalDateTime.of(2026, 6, 23, 10, 0));
    }

    private static Member member(Long memberId, String email) {
        return new Member(memberId, email, "hash", "User", "010",
                LocalDateTime.of(2026, 6, 23, 10, 0),
                LocalDateTime.of(2026, 6, 23, 10, 0));
    }
}
