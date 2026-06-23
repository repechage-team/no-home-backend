package com.ssafy.home.notice.mapper;

import com.ssafy.home.notice.dto.Notice;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface NoticeMapper {

    int insertNotice(NoticeInsertCommand command);

    Optional<Notice> selectById(@Param("noticeId") Long noticeId);

    List<Notice> selectRecent(@Param("limit") int limit);

    int updateNotice(
            @Param("noticeId") Long noticeId,
            @Param("title") String title,
            @Param("content") String content
    );

    int deleteNotice(@Param("noticeId") Long noticeId);
}
