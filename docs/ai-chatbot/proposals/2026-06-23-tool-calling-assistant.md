---
title: AI 어시스턴트 tool calling 혼합형 재설계 — 타당성 조사 & 전체 계획
domain: ai-chatbot
type: proposal
status: implemented
author: 이정헌
created: 2026-06-23
updated: 2026-06-24
related:
  - ../troubleshooting/2026-06-23-agent-intent-mapping-and-capability-drift.md
  - ../reports/2026-06-23-agent-mode-phase2-implementation.md
  - 2026-06-22-agent-mode.md
  - ../backlog/2026-06-23-backlog.md
---

# AI 어시스턴트 tool calling 혼합형 재설계

분리형(질문 모드 `/chat` + 실행 모드 `/agent`, 모드 토글)을 **단일 대화에서 LLM이 tool calling으로 분기**하는
혼합형으로 재설계한다. 일반 질문이면 텍스트 답변, 페이지 조작이면 프론트 액션 tool 호출.

## 1. 배경 / 문제 (재설계 동기)
[트러블슈팅](../troubleshooting/2026-06-23-agent-intent-mapping-and-capability-drift.md) 참조.
- **의도 매핑 오분류**: `.entity(AgentCommand.class)`가 모델에게 action enum을 *강제 생성*시켜, 모호·불만 발화도
  억지 action(주로 paginate)으로 샌다. 대응이 프롬프트 땜질 → 비결정·회귀.
- **모드 토글 UX 부담**: 사용자가 한 대화에서 묻고 조작하려는데 질문/실행을 수동 전환해야 함.
- **capability drift**: 신규 전월세 필터가 검색 UI/백엔드엔 있으나 `filterSchema`(단일출처) 미갱신 → AI 미반영.

## 2. 제안 구조
단일 엔드포인트 `POST /api/ai/assistant`에서 LLM이 두 종류 tool을 가짐:
- **데이터 조회 tool**(서버 실행, `returnDirect=false`): `searchSeoulAptDeals`(+전월세 확장) → `HouseService.searchHouseDeals`(DB 캐시 또는 라이브 공공데이터 조회, BE #27) 후 LLM 텍스트 답변.
- **프론트 액션 tool**(`@Tool(returnDirect=true)`): `applyFiltersAndSearch·setFilters·paginate·selectItem·mapFocus·reset`
  → 메서드 본문은 **부작용 없이** 인자를 `AgentCommand`로 조립·가드 후 반환 → 프론트가 실행.

동작: 사용자 발화 → 모델 판단 → (질문) 데이터 tool→텍스트 / (조작) 액션 tool→명령. **모드 토글 제거.**

> **선행 머지 반영(2026-06-23)**: 단기 대화기억(Phase 5, BE #28/FE #12)이 중앙 `ChatClient` 빈에 `MessageChatMemoryAdvisor`로 부착되어 머지됨 → 통합 `/assistant`도 **같은 빈을 쓰므로 멀티턴 기억이 자동 승계**(추가 작업 없음). 라이브 검색 파이프라인(BE #27)으로 데이터 tool 경로에 외부 API 호출이 끼어들 수 있으므로 **tool/요청 timeout 정책에 라이브 지연을 반영**(Phase 1).

## 3. 타당성 조사

### 3.1 오분류가 구조적으로 개선되는 이유
- tool calling은 **"아무 tool도 안 부르고 일반 답변"이 1급 선택지** → 불만("이건 아닌데")·평가·질문이 억지 action으로
  새지 않고 텍스트로 빠진다. (현재 `.entity`는 무조건 action 생성이 근본 원인.)
- 각 의도가 독립 함수 시그니처(이름+설명+파라미터)로 제시 → 모델 정렬이 더 강함. "검색 실행(applyFiltersAndSearch)"과
  "조건만(setFilters)"을 별 함수로 분리해 신호가 명확.
- 거래월 범위·서울 지역은 tool 본문의 서버 가드(`AgentCommandGuards`/`HouseTools.dealYmdError`, now 기준)가
  **결정적**으로 유지 → 약한 모델이 무엇을 호출하든 최종 방어선 보존.

### 3.2 기술 실현 (Spring AI 1.1.2, 확인됨)
- **(A) `@Tool(returnDirect=true)`** — 서버가 tool 메서드 실행 후 결과를 LLM 후처리 없이 호출자로 반환. 액션 tool 본문이
  명령 객체를 반환하면 그게 클라이언트로 전달. 데이터 tool은 `returnDirect=false`로 LLM 답변. **권장**(기존 자산 최대
  보존, 안정 공개 API, 본 서비스의 배타적 분기에 최적).
- **(B) `internalToolExecutionEnabled(false)` + `ToolCallingManager`** — 수동 루프로 tool call을 가로채 분류·실행.
  유연(명령+텍스트 동시·멀티 tool)하나 복잡, 내부 API 결합. **A의 PoC 실패 시 폴백.**
- **최대 미지수**: 1.1.2에서 returnDirect 결과의 직렬화 형태·멀티 tool·명령+텍스트 동시 거동 → **PoC로 실측**(추정 금지).

### 3.3 혼합 응답 계약
`ApiResponse<AssistantResponse>` — `AssistantResponse { type: "answer"|"command", answer, command(AgentCommand), notice }`.
- 액션 tool 산출(직렬화 AgentCommand)이면 `type=command`(+가드), 그 외 텍스트면 `type=answer`.
- 확인 문구는 `command.summary`(모델 채움, fallback) + 프론트 재생성(실제 적용 결과, 1차)으로 처리.

### 3.4 기존 자산 재사용 / capability 동기화
- 재사용: `AgentCommand`(명령 계약), `AgentCommandGuards`(+19 테스트), `HouseTools.dealYmdError`, `SeoulLawdCodeResolver`,
  프론트 `handleAgentCommand`/`applyAgentFilters`/`resolvePaginateTarget`/`resolveItemTarget`, rate limiter/인증.
  가드 호출 위치만 컨트롤러→tool 본문으로 이동.
- **capability drift 차단**: 액션 tool 인자를 Map으로 묶어 `AgentCommand.filters`에 넣어 프론트 단일출처 유지. 단
  전월세처럼 필터가 늘면 평탄화 파라미터가 비대 → **filters Map 단일 파라미터 또는 핵심 키 평탄화+`extraFilters` Map**
  절충. + **폼↔filterSchema 동기화 테스트**로 누락(전월세 같은)을 강제 검출.

### 3.5 장단점 요약
| | 장점 | 단점/리스크 |
|---|---|---|
| tool calling 혼합 | 오분류 구조 개선·토글 제거·전월세 통합·자산 재사용 | returnDirect 실거동 미지수·over-trigger(질문인데 액션 호출) 잔존·평탄화 결합 |
| 현행 유지(.entity) | 변경 없음 | 의도 매핑 취약·프롬프트 땜질·drift 반복 |

## 4. 전월세 통합 (이번 재설계에 포함)
- `filterSchema`/`capabilities`에 `dealMode·minDeposit·maxDeposit·minMonthlyRent·maxMonthlyRent` 추가(단일출처 복구).
- 액션 tool(applyFiltersAndSearch/setFilters)에 전월세 파라미터 반영(또는 filters Map 경유).
- 데이터 조회 tool에 전월세 조회 확장(챗에서 "전세 평균 보증금" 등).
- 가드: dealMode 허용값(sale/jeonse/monthly/rent/all) 검증(선택).

## 5. 전체 실행 계획 (Phase 0 → 3)

### Phase 0 — PoC (게이트, 가장 중요)
- `searchSeoulAptDeals`(returnDirect=false) + 액션 tool 1~2개(applyFiltersAndSearch·paginate)를 returnDirect=true로 임시 구성.
- **실측**: returnDirect 결과 직렬화 형태, 질문→텍스트/조작→명령 분기가 한 호출에서 되는지, 멀티/동시 거동.
- 게이트: 안 되면 (B) 수동 루프로 경로 전환.

### Phase 1 — 백엔드 통합 엔드포인트
- `PageActionTools`(6 tool, 가드 내장) + `AssistantResponse` + `POST /api/ai/assistant`(피처 플래그 `ai.assistant.enabled`).
- 횡단 관심사(rate limiter/인증/timeout·auth 오류 분류) 공통화(`AiProviderErrors` 추출 — 기존 TODO).
- 전월세 capability/tool 반영.
- 테스트: tool 시그니처 단위(가드 포함) + 컨트롤러 분기(answer/command) + NL round-trip(S1~S12 + 전월세 + 불만/모호).

### Phase 2 — 프론트 통합
- `/assistant` 클라이언트 추가, `type`으로 텍스트/명령 분기. 기존 핸들러 재사용. 모드 토글은 플래그로 유지.
- filterSchema 전월세 키 추가(단일출처) + 폼↔schema 동기화 테스트.
- 브라우저 시나리오 전수(매매+전월세+탐색+불만/모호) 검증.

### Phase 3 — 토글 제거 + 정리
- 모드 토글 제거(단일 입력). 안정화 후 `/agent` 제거, `/chat` 레거시 보존 후 제거.
- 회귀 통과율 모니터링 + 롤백 플래그 상시 유지.

## 6. 검증 / 롤백
- 단위(tool·가드) + NL round-trip(다회 샘플링·임계 통과율) + 브라우저 E2E.
- 피처 플래그로 즉시 구 경로 복귀. `AgentCommand`·프론트 실행 런타임 무변경(어댑터만).

## 7. 리스크 / 미지수
- returnDirect 실거동(1.1.2) — Phase 0 게이트.
- over-trigger(질문↔조작 오분류)는 tool description + PoC 측정으로 완화, 가드 영역 밖 잔존.
- 모델 비결정성 — 결정적 가드 + 다회 검증으로 상쇄.

## 8. 권장
**(A) returnDirect 혼합형 + 전월세 통합 + capability 동기화 강제.** Phase 0 PoC를 관문으로 진행.
