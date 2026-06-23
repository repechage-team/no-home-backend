package com.ssafy.home.notice.service;

import com.ssafy.home.member.dto.Member;
import com.ssafy.home.member.mapper.MemberMapper;
import com.ssafy.home.notice.dto.Notice;
import com.ssafy.home.notice.dto.NoticeRequest;
import com.ssafy.home.notice.dto.NoticeResponse;
import com.ssafy.home.notice.mapper.NoticeInsertCommand;
import com.ssafy.home.notice.mapper.NoticeMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class NoticeService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_TITLE_LENGTH = 200;

    private final NoticeMapper noticeMapper;
    private final MemberMapper memberMapper;
    private final Set<String> adminEmails;

    public NoticeService(
            NoticeMapper noticeMapper,
            MemberMapper memberMapper,
            @Value("${notice.admin-emails:}") String adminEmails
    ) {
        this.noticeMapper = noticeMapper;
        this.memberMapper = memberMapper;
        this.adminEmails = parseAdminEmails(adminEmails);
    }

    public List<NoticeResponse> findRecent(Integer limit, Long currentMemberId) {
        int normalizedLimit = normalizeLimit(limit);
        boolean editable = isAdmin(currentMemberId);
        return noticeMapper.selectRecent(normalizedLimit).stream()
                .map(notice -> NoticeResponse.from(notice, editable))
                .toList();
    }

    public NoticeResponse findById(Long noticeId, Long currentMemberId) {
        Notice notice = noticeMapper.selectById(requiredNoticeId(noticeId))
                .orElseThrow(() -> new NoticeException(NoticeErrorCode.NOT_FOUND, "notice not found."));
        return NoticeResponse.from(notice, isAdmin(currentMemberId));
    }

    @Transactional
    public NoticeResponse create(Long memberId, NoticeRequest request) {
        Long authorId = requiredAdminMemberId(memberId);
        String title = requiredTitle(request);
        String content = requiredContent(request);
        NoticeInsertCommand command = new NoticeInsertCommand(authorId, title, content);
        noticeMapper.insertNotice(command);
        return findById(command.getNoticeId(), authorId);
    }

    @Transactional
    public NoticeResponse update(Long memberId, Long noticeId, NoticeRequest request) {
        Long adminId = requiredAdminMemberId(memberId);
        Long targetId = requiredNoticeId(noticeId);
        String title = requiredTitle(request);
        String content = requiredContent(request);
        int updated = noticeMapper.updateNotice(targetId, title, content);
        if (updated == 0) {
            throw new NoticeException(NoticeErrorCode.NOT_FOUND, "notice not found.");
        }
        return findById(targetId, adminId);
    }

    @Transactional
    public void delete(Long memberId, Long noticeId) {
        requiredAdminMemberId(memberId);
        Long targetId = requiredNoticeId(noticeId);
        int deleted = noticeMapper.deleteNotice(targetId);
        if (deleted == 0) {
            throw new NoticeException(NoticeErrorCode.NOT_FOUND, "notice not found.");
        }
    }

    private static int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Long requiredAdminMemberId(Long memberId) {
        if (memberId == null) {
            throw new NoticeException(NoticeErrorCode.UNAUTHENTICATED, "login required.");
        }
        if (!isAdmin(memberId)) {
            throw new NoticeException(NoticeErrorCode.FORBIDDEN, "admin permission is required.");
        }
        return memberId;
    }

    private boolean isAdmin(Long memberId) {
        if (memberId == null || adminEmails.isEmpty()) {
            return false;
        }
        return memberMapper.selectById(memberId)
                .map(Member::email)
                .map(NoticeService::normalizeEmail)
                .filter(email -> !email.isBlank())
                .map(adminEmails::contains)
                .orElse(false);
    }

    private static Set<String> parseAdminEmails(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(NoticeService::normalizeEmail)
                .filter(email -> !email.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static Long requiredNoticeId(Long noticeId) {
        if (noticeId == null || noticeId < 1) {
            throw new NoticeException(NoticeErrorCode.VALIDATION, "valid notice id is required.");
        }
        return noticeId;
    }

    private static String requiredTitle(NoticeRequest request) {
        String title = trimToNull(request == null ? null : request.title());
        if (title == null) {
            throw new NoticeException(NoticeErrorCode.VALIDATION, "title is required.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new NoticeException(NoticeErrorCode.VALIDATION, "title must be 200 characters or less.");
        }
        return title;
    }

    private static String requiredContent(NoticeRequest request) {
        String content = trimToNull(request == null ? null : request.content());
        if (content == null) {
            throw new NoticeException(NoticeErrorCode.VALIDATION, "content is required.");
        }
        return content;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
