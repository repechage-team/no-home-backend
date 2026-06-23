---
title: 에이전트 의도 매핑 오분류 + 신규 필터(전월세) capability 미동기화
domain: ai-chatbot
type: troubleshooting
status: investigating
author: 이정헌
created: 2026-06-23
updated: 2026-06-23
related:
  - ../proposals/2026-06-23-tool-calling-assistant.md
  - ../backlog/2026-06-23-backlog.md
  - ../reports/2026-06-23-agent-mode-phase2-implementation.md
---

# 트러블슈팅: 에이전트 의도 매핑 오분류 + 전월세 capability 미동기화

에이전트(실행) 모드 재검토에서 드러난 두 부류의 구조적 문제. 개별 프롬프트 패치로는 재발하므로
근본 재설계([proposal](../proposals/2026-06-23-tool-calling-assistant.md))의 동기로 기록한다.

## 문제 A — 자연어 의도가 엉뚱한 action으로 매핑됨

### 증상 (재현)
| 발화 | 실제 결과 | 기대 |
|---|---|---|
| "이건 가장 싼 매물이 아닌데"(불만) | `paginate/next` (4/4 결정적) | clarify(되묻기) |
| "용산구 2024년 7월 검색" | 종종 `setFilters`(검색 미실행) | search |
| "용산구 2025년 7월 검색" | `clarify` "2024년까지 지원"(환각) | search |
| "가장 싼 매물 보여줘" | 종종 `paginate`(비결정) | search + sort=priceAsc |

### 원인
- `AiAgentController`가 `.entity(AgentCommand.class)`로 **모델에게 action enum을 강제로 생성**시킨다.
  모델은 "아무 것도 하지 않음/일반 답변"이라는 선택지가 없어, 모호·불만·평가성 발화도 **억지로 어떤 action**
  (특히 fallback처럼 `paginate`)으로 채운다.
- 거래월 환각("2024년까지")은 프롬프트에 현재 날짜·범위 사실이 없어 약한 모델(GMS)이 상한을 추정.
- 검색/조작/질문 분기 정확도가 전적으로 단일 약한 모델에 의존.

### 현재 대응의 한계
- 프롬프트에 규칙을 누적(거래월 판단 금지·search 기본·paginate 엄격화 등)했으나 **비결정적·회귀**.
  날짜 비교를 모델에 시키면 정상 월(2025)을 거부하는 등 역효과도 발생(서버 결정적 검증으로 우회 필요).
- 가드(`AgentCommandGuards`)는 안전 불변식(지역·연월·인덱스 하한)만 강제하고 **"올바른 action 선택"은 강제 못 함**.

## 문제 B — 신규 필터(전월세)가 AI capability에 미동기화

### 증상
- 팀원이 **아파트 전월세 검색**(매매 sale + 전세 jeonse/월세 monthly)을 추가:
  백엔드 `HouseSearchCondition`에 `dealMode·minDeposit·maxDeposit·minMonthlyRent·maxMonthlyRent`,
  프론트 `App.vue`에 dealMode 토글·보증금/월세 슬라이더까지 완전 구현.
- 그러나 `no-home-frontend/src/houseSearchParams.js`의 `filterSchema`/`capabilities`에 전월세 키가 **누락** →
  **AI 에이전트·챗은 전월세를 전혀 모름**(매매만 지원). 에이전트로 "전세 검색"이 불가.

### 원인 (근본)
- MVP 설계의 약속은 "메인 필터 추가 시 `filterSchema`(capability 단일출처) 한 곳만 갱신하면 에이전트가 자동 적응"이었다.
  그러나 **단일출처가 강제(코드/테스트)되지 않는 관례**라, 신규 기능 추가자가 `filterSchema` 갱신을 누락 → drift 발생.
- 즉 "검색 폼이 실제 쓰는 키"와 "AI에 노출하는 capability"가 **별도로 관리**되어 동기화가 깨졌다.

### 현재 대응의 한계
- 누락 시 조용히 실패(에이전트가 키를 무시·보고)하나, 사용자에겐 "전월세 검색 안 됨"으로 보임. 컴파일·테스트로 안 걸림.

## 근본 방향 (별도 proposal)
1. **의도 매핑을 tool calling으로 전환** — "아무 tool도 안 부름=일반 답변"이 1급 선택지가 되어 불만·질문이 억지 action으로
   새지 않음. 거래월 범위 등은 tool 본문의 서버 가드(결정적)로 유지. → 문제 A의 구조적 완화.
2. **capability 동기화 강제** — 검색 폼이 실제 사용하는 필터에서 capability를 파생하거나, 폼↔schema 동기화 테스트를
   두어 drift를 차단. 전월세를 포함해 통합. → 문제 B 차단.

상세 설계·타당성·실행 계획: [tool calling assistant proposal](../proposals/2026-06-23-tool-calling-assistant.md).

## 빠른 점검
- 에이전트가 특정 필터를 무시하면 먼저 `houseSearchParams.js`의 `filterSchema`에 그 키가 있는지 확인(capability drift).
- 모호/불만 발화가 엉뚱한 action으로 가면 `.entity` 강제 생성 구조의 한계 — 프롬프트 미세조정보다 tool calling 전환이 근본.
