---
title: ai-chatbot 도메인 문서 인덱스
domain: ai-chatbot
type: index
status: living
updated: 2026-06-21
---

# ai-chatbot — AI 챗봇 (서울 아파트 실거래가 질의)

`com.ssafy.home.ai` 패키지로 구현된 AI 챗봇 기능 문서. (날짜 내림차순)

## 작업 흐름 (요약)
도입 제안 → 구현 & E2E 검증 → 견고화(HouseTools `dealYmd` 검증·자동임포트 폴백·연월 없는 질의)와 프론트 위젯(접근성·Unicode·테스트) → 이슈 분리 → 백로그. **처음 읽는 사람은 제안 → 구현 → 백로그 순**으로 보면 전체 맥락과 남은 작업을 파악할 수 있다.

## 문서
- [2026-06-21 남은 작업 백로그](2026-06-21-backlog.md) — `type: report`, 진행 중
- [2026-06-21 AI 챗봇 로그 및 개인정보 보호 정책](2026-06-21-ai-logging-privacy-policy.md) — `type: report`
- [2026-06-21 사용량 리미터 및 UX 사용자 시나리오](2026-06-21-usage-limiter-ux-scenarios.md) — `type: report`
- [2026-06-21 구현 & E2E 검증 정리](2026-06-21-implementation.md) — `type: report`
- [2026-06-21 도입 제안 & 버전업 협의](2026-06-21-proposal.md) — `type: proposal`, 팀 합의 완료

## 트러블슈팅 (troubleshooting/)
- [2026-06-21 'AI 환각'처럼 보인 인코딩 문제](troubleshooting/2026-06-21-ai-prompt-and-hallucination.md)

## 관련 (교차)
- [ISSUE: 시드 데이터 더블 인코딩](../_shared/issues/2026-06-21-seed-data-double-encoding.md)
- [버전업 트러블슈팅](../_shared/troubleshooting/2026-06-21-version-upgrade.md)
