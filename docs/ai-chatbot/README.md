---
title: ai-chatbot 도메인 문서 인덱스
domain: ai-chatbot
type: index
status: living
updated: 2026-06-23
---

# ai-chatbot — AI 챗봇 (서울 아파트 실거래가 질의)

`com.ssafy.home.ai` 패키지로 구현된 AI 챗봇 기능 문서. (날짜 내림차순)

## 작업 흐름 (요약)
도입 제안 → 구현 & E2E 검증 → 견고화(HouseTools `dealYmd` 검증·자동임포트 폴백·연월 없는 질의)와 프론트 위젯(접근성·Unicode·테스트) → 이슈 분리 → 백로그 → **에이전트(실행) 모드 제안 → MVP 구현·검증**(질문 모드 유지 + 자연어로 검색 페이지 조작, capability-driven). **처음 읽는 사람은 제안 → 구현 → 백로그 순**으로 보면 전체 맥락과 남은 작업을 파악할 수 있다.

## 문서
- [2026-06-23 남은 작업 백로그 (재편성)](backlog/2026-06-23-backlog.md) — `type: report`, **진행 중 (남은 작업 단일 출처, 최신)**
- [2026-06-22 인계 / 재개 가이드 (당일 마감)](handoff/2026-06-22-handoff.md) — `type: report`, **이어받을 때 먼저 읽기**
- [2026-06-22 남은 작업 백로그 (재편성)](backlog/2026-06-22-backlog.md) — `type: report`, 2026-06-22 스냅샷 (superseded → 06-23)
- [2026-06-21 최종 인계 / 재개 가이드](handoff/2026-06-21-handoff.md) — `type: report`, 2026-06-21 스냅샷 (새 환경 셋업·작업 규칙)
- [2026-06-21 남은 작업 백로그](backlog/2026-06-21-backlog.md) — `type: report`, 2026-06-21 스냅샷 (superseded, 완료 이력)
- [2026-06-21 AI 챗봇 로그 및 개인정보 보호 정책](reports/2026-06-21-ai-logging-privacy-policy.md) — `type: report`
- [2026-06-21 사용량 리미터 및 UX 사용자 시나리오](reports/2026-06-21-usage-limiter-ux-scenarios.md) — `type: report`
- [2026-06-23 AI 에이전트 모드 Phase 2 구현 & 검증 정리 (필터+액션)](reports/2026-06-23-agent-mode-phase2-implementation.md) — `type: report`, **Phase 2 구현·검증 완료 (PR A 필터 / PR B 액션, 미머지)**
- [2026-06-22 AI 에이전트 모드 MVP 구현 & 검증 정리](reports/2026-06-22-agent-mode-mvp-implementation.md) — `type: report`, **MVP 구현·검증·머지 완료 (BE #16 / FE #5)**
- [2026-06-21 구현 & E2E 검증 정리](reports/2026-06-21-implementation.md) — `type: report`
- [2026-06-22 AI 에이전트 모드 제안 (페이지 조작)](proposals/2026-06-22-agent-mode.md) — `type: proposal`, `status: implemented-mvp` (MVP 구현됨 → 리포트 참조)
- [2026-06-21 도입 제안 & 버전업 협의](proposals/2026-06-21-proposal.md) — `type: proposal`, 팀 합의 완료

## 트러블슈팅 (troubleshooting/)
- [2026-06-22 Spring AI 빈/무효 GMS 키 기동 실패와 graceful 비활성화](troubleshooting/2026-06-22-spring-ai-empty-key-graceful.md)
- [2026-06-22 '서울' 미접두 자치구를 서울 외로 선판단해 거부](troubleshooting/2026-06-22-ai-region-prejudgment-refusal.md)
- [2026-06-21 'AI 환각'처럼 보인 인코딩 문제](troubleshooting/2026-06-21-ai-prompt-and-hallucination.md)

## 관련 (교차)
- [ISSUE: 시드 데이터 더블 인코딩](../_shared/issues/2026-06-21-seed-data-double-encoding.md)
- [ISSUE: 프론트 App.vue 한글 문자열 mojibake](../_shared/issues/2026-06-22-frontend-app-vue-mojibake-strings.md)
- [버전업 트러블슈팅](../_shared/troubleshooting/2026-06-21-version-upgrade.md)
