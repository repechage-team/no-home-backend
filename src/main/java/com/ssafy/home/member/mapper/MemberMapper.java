package com.ssafy.home.member.mapper;

import com.ssafy.home.member.dto.Member;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

public interface MemberMapper {

    int insertMember(MemberInsertCommand command);

    Optional<Member> selectById(@Param("memberId") Long memberId);

    Optional<Member> selectByEmail(@Param("email") String email);

    List<Member> searchMembers(@Param("keyword") String keyword);

    int updateCurrentMember(
            @Param("memberId") Long memberId,
            @Param("name") String name,
            @Param("phone") String phone
    );

    int updatePassword(
            @Param("memberId") Long memberId,
            @Param("passwordHash") String passwordHash
    );

    int deleteById(@Param("memberId") Long memberId);
}
