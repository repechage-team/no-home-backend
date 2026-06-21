---
title: no-home 백엔드 문서 인덱스
domain: _shared
type: index
status: living
updated: 2026-06-21
---

# no-home 백엔드 문서

기능(도메인) 단위로 문서를 분리하고, 공통·교차 문서는 `_shared/`에 둔다.
**새 문서를 만들기 전에 [문서 작성 가이드](_shared/guide/documentation-guide.md)를 먼저 읽고 [템플릿](_shared/templates/)을 복사하세요.**

## 도메인
- [ai-chatbot](ai-chatbot/README.md) — AI 챗봇(서울 아파트 실거래가 질의)
- *(추후)* house · member · publicdata · common — 코드 패키지명과 동일하게 추가

## 공통 / 메타 (`_shared`)
- [문서 작성 가이드](_shared/guide/documentation-guide.md) — 구조·명명·frontmatter 규칙
- [템플릿](_shared/templates/) — document / issue / troubleshooting / adr
- [공통 문서 인덱스](_shared/README.md) — 교차 이슈·트러블슈팅

## 규칙 요약
- 기록형 문서: `YYYY-MM-DD-<slug>.md` (시간순). 리빙 문서(README·guide·templates): 고정명.
- 모든 문서 상단 frontmatter 필수. 내부 링크는 상대경로.
- 자세한 내용은 [가이드](_shared/guide/documentation-guide.md).
