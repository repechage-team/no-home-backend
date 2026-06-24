# 회원 검색 권한 변경 요약

## 변경 내용

- `/api/members/search`는 로그인 사용자 중 관리자만 사용할 수 있게 제한했다.
- 관리자 여부는 기존 공지사항 관리자 설정인 `notice.admin-emails` 목록으로 판단한다.
- 일반 회원이 회원 검색 API를 호출하면 `403 Forbidden` 응답을 반환한다.
- 회원 검색 권한 실패를 표현하기 위해 `MemberErrorCode.FORBIDDEN`을 추가했다.

## 확인한 검증

- `MemberServiceTest`
- `MemberControllerTest`
