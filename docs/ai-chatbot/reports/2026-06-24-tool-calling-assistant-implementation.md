---
title: AI 어시스턴트 tool calling 혼합형 재설계 구현 & 검증
domain: ai-chatbot
type: report
status: done
author: 이정헌
created: 2026-06-24
updated: 2026-06-24
related:
  - ../proposals/2026-06-23-tool-calling-assistant.md
  - ../troubleshooting/2026-06-23-agent-intent-mapping-and-capability-drift.md
  - 2026-06-24-short-term-memory-implementation.md
  - ../handoff/2026-06-23-handoff.md
  - ../backlog/2026-06-24-backlog.md
---

# AI 어시스턴트 tool calling 혼합형 재설계 구현 & 검증

분리형(`/chat` 질문 + `/agent` 실행, 모드 토글)을 **단일 `POST /api/ai/assistant`에서 LLM이 tool calling으로 분기**하는 혼합형으로 재설계했다(backend [#29](https://github.com/repechage-team/no-home-backend/pull/29) / frontend [#13](https://github.com/repechage-team/no-home-frontend/pull/13)).

- **배경·동기**: [트러블슈팅 — 의도 매핑 오분류 + 전월세 capability drift](../troubleshooting/2026-06-23-agent-intent-mapping-and-capability-drift.md)
- **타당성·계획**: [proposal(implemented)](../proposals/2026-06-23-tool-calling-assistant.md)

이 문서는 그 위에 **무엇을 어떻게 구현했고, 구현 중 어떤 이슈를 어떻게 풀었는지**를 남긴다.

## 1. 핵심 설계 (요약)
- **데이터 조회 tool**(`returnDirect=false`): `HouseTools.searchSeoulAptDeals` → `HouseService`(DB 캐시/라이브) → LLM이 텍스트 답변.
- **프론트 액션 tool**(`@Tool(returnDirect=true)`): `PageActionTools`의 6개(applyFiltersAndSearch/setFilters/paginate/selectItem/mapFocus/reset) → 본문은 부작용 없이 `AgentCommand` 조립·가드 후 반환 → 프론트가 실행.
- **응답 계약** `AssistantResponse { type:"answer"|"command", answer, command, notice }`.

## 2. Phase 0~3 구현 내역

### Phase 0 — returnDirect 게이트 (실측)
PoC로 Spring AI 1.1.2 `@Tool(returnDirect=true)` 실거동을 측정해 A안을 확정했다.
- 액션 tool이 반환한 `AgentCommand`가 `.call().content()`에 **JSON으로** 실리고, `getResult().getMetadata().getFinishReason() == "returnDirect"`로 **결정적으로 식별**된다.
- 4발화 실측: 질문→데이터 tool→텍스트(STOP) / 검색·페이지 조작→액션 tool→명령(returnDirect) / 불만·모호→tool 미호출 텍스트(STOP, **억지 action 없음**).
- 결과: A안 확정, B안(수동 루프) 불필요. PoC 코드는 Phase 1에서 정식 구현으로 대체.

### Phase 1 — 백엔드 통합 엔드포인트
- `AiAssistantController`(`/api/ai/assistant`): `finishReason=="returnDirect"`면 content를 `ObjectMapper`로 `AgentCommand` 역직렬화(type=command), 아니면 텍스트(type=answer).
- `PageActionTools`(6 액션 tool): 본문에서 `AgentCommand` 조립 → `AgentCommandGuards.validate`(서울 지역·거래월·action·인덱스 가드, 위반 시 clarify) → 반환. 가드 호출 위치를 컨트롤러→tool 본문으로 이동.
- 공통 유틸 추출: `AiProviderErrors`(timeout/auth 분류) · `AiRequests`(memberId·conversationId) — 기존 `/chat`·`/agent`에 복사돼 있던 로직 일원화.
- `HouseTools` 전월세 확장: `dealMode`(sale/jeonse/monthly) 추가, 전월세는 보증금/월세로 통계.

### Phase 2 — 프론트 통합
- `parseAssistantResponse`(answer/command/error 분기) + `ChatWidget` 단일 호출 + App.vue `handleAgentCommand` 재사용.
- `filterSchema`에 전월세 키(dealMode·deposit·monthlyRent·전월세 sort) 추가 → `capabilities()`/`applyAgentFilters`로 자동 전파(**capability drift 해소**).
- **폼↔filterSchema 동기화 테스트** 추가 — `emptyFilters()` 키 ⊆ `filterSchema`를 강제해 drift 재발 차단.

### Phase 3 — 토글 제거 + 정리
- 레거시 `/chat`·`/agent` 컨트롤러·모드 토글·미사용 파서 제거, `/assistant` 기본 활성화.
- 단기기억은 같은 `ChatClient` 빈을 쓰므로 자동 승계([단기기억 리포트](2026-06-24-short-term-memory-implementation.md)).

## 3. 구현 중 이슈 · 결정 (트러블슈팅)

| 이슈 | 증상 | 해결 |
|---|---|---|
| **returnDirect 실거동 미지수** | 1.1.2에서 명령 회수 형태·분기 거동 불확실 | Phase 0 PoC 게이트로 실측 → `finishReason="returnDirect"` + content(JSON) 확정 |
| **setFilters 오분류** | "강남구 검색해줘"가 `search`가 아닌 `setFilters`(검색 미실행)로 분류 | tool description 정교화("검색해줘/찾아줘/보여줘는 applyFiltersAndSearch", setFilters는 '조건만') + 시스템 프롬프트 명시 → 재실측에서 `search`로 교정 |
| **거래월 LLM 판단 역효과** | 모델에 거래월 범위 판단을 시키면 정상 월(2025)도 거부하는 환각 | LLM 판단 대신 **서버 결정적 가드**(`HouseTools.dealYmdError`/`AgentCommandGuards`)에 위임. 거래월 프롬프트 패치 PR [#24](https://github.com/repechage-team/no-home-backend/pull/24)는 **CLOSED**(재설계로 대체) |
| **setFilters 후 패널 가림** | 검색 패널 자동 접힘(팀 #14) 상태에서 setFilters가 패널을 안 펼쳐 변경이 안 보임 | `handleAgentCommand` setFilters 분기에 `searchPanelCollapsed=false` 보정(FE [#15](https://github.com/repechage-team/no-home-frontend/pull/15)) |

> 참고: 이후 발생한 검색 화면 누락([FE #18](https://github.com/repechage-team/no-home-frontend/issues/18))·지도 재렌더([FE #20](https://github.com/repechage-team/no-home-frontend/pull/20))는 팀원 #16 머지(activePage v-if 탭 전환 도입)의 충돌 해결 사고/부작용으로, **본 재설계와는 독립**한 회귀다(별도 이슈로 처리·해결).

## 4. 검증
- 단위/통합: backend **144** / frontend **44** 그린(PageActionTools·AgentCommandGuards·AiProviderErrors·컨트롤러 분기·parseAssistantResponse·폼↔schema 동기화).
- docker·브라우저 E2E: 질문/검색/전세/월세/페이지/불만/비서울/멀티턴 분기 정확. "거기 같은 달로 검색해줘" 같은 멀티턴이 지역·월·거래유형을 단기기억으로 추론.

## 5. 후속 한계 (returnDirect 단일 액션 모델)
직접 사용 중 드러난 "맥락 이해 ↔ 동작" 괴리. 단일 동작은 개선됐으나 다음은 미해결([06-24 backlog](../backlog/2026-06-24-backlog.md) P1):
- **② 결과 기반 선택** — "면적 가장 작은 매물" 등은 현재 결과 목록이 모델에 전달되지 않아 환각. `AssistantRequest`에 결과 요약 동봉 + 면적 정렬 필요.
- **③ 다중 동작** — "3페이지 이동 후 2번째 선택"은 returnDirect가 1액션/호출이라 첫 동작만 실행. 수동 루프(`ToolCallingManager`) 또는 `commands[]` 배열 필요.
