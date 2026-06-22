---
title: AI 에이전트 모드 MVP 구현 & 검증 정리
domain: ai-chatbot
type: report
status: done
author: 이정헌
created: 2026-06-22
updated: 2026-06-22
related:
  - ../proposals/2026-06-22-agent-mode.md
  - ../backlog/2026-06-22-backlog.md
---

# AI 에이전트 모드 MVP 구현 & 검증 정리

관련: [제안서](../proposals/2026-06-22-agent-mode.md) · [백로그](../backlog/2026-06-22-backlog.md)

## 1. 무엇을 만들었나
기존 **질문(Q&A) 모드**(`POST /api/ai/chat`, 텍스트 답변)는 그대로 두고, **실행(에이전트) 모드**를 추가했다.
사용자는 챗봇과 **대화만** 하고, 백엔드가 자연어를 **구조화 명령(`AgentCommand`)** 으로 변환해 돌려주면 **프론트 액션 런타임**(ChatWidget → App.vue)이 그 명령으로 실제 검색 페이지를 조작한다(필터 채움 → 검색 실행 → 사후 요약). AI가 DOM을 직접 만지지 않고 프론트가 명령을 실행하는 구조라 안전·견고하다.

## 2. MVP 범위
- **액션(4종)**: `search`(필터 적용+검색) · `setFilters`(검색 미실행) · `reset`(초기화) · `clarify`(되묻기/안전 폴백).
- **필터 필드**: `sido` · `sigungu` · `startDealMonth` · `endDealMonth` · `aptName` (NL 빈도 최고 + 기존 지역·연월 가드 즉시 재사용).
- **단일 출처는 처음부터 완전**: 프론트 `filterSchema`/`capabilities`에는 **9필드 전부 선언**한다. MVP에서 적용 우선순위만 뒤로 미룬 필드도 capability에 노출되며, 들어와도 무시·보고로 안전하다.
- **Phase 2 잔여**: 액션 `paginate` · `mapFocus` · `selectItem` / 필터 `sort` · `umdNm` · `minPrice` · `maxPrice`.

> 범위 결정 근거(중요도·UI 결합도 표)는 [제안서](../proposals/2026-06-22-agent-mode.md) 및 승인된 구현 계획 참조. paginate·map·select는 "현재 결과/지도 상태" 의존성, 가격은 슬라이더·range 로딩 결합 때문에 Phase 2로 분리했다.

## 3. 백엔드 변경 (`com.ssafy.home.ai`)
| 파일 | 역할 |
|---|---|
| `ai/agent/AgentCommand.java` (신규) | 구조화 명령 record. `filters`는 의도적으로 **`Map<String,String>` 제네릭 맵**(고정 타입 record 금지) → 메인 필터가 추가돼도 record 무변경. `clarify(question)` 팩토리 포함 |
| `ai/agent/AgentCommandGuards.java` (신규) | `validate(command, resolver, now)` 정적 가드. 허용 action 검사 + 지역(`SeoulLawdCodeResolver.resolveLawdCds` empty→clarify) + 연월(`YYYY-MM`→`YYYYMM` 후 `HouseTools.dealYmdError`→clarify). Spring 컨텍스트 없이 단위테스트 가능하게 분리 |
| `ai/controller/AiAgentController.java` (신규) | `POST /api/ai/agent`. Spring AI **`.entity(AgentCommand.class)`** + `temperature 0`, capability 주입 시스템 프롬프트. `@Nullable ChatClient` 503 게이트·`AiChatRateLimiter`·auth/timeout 분류를 질문 모드와 동일하게 재사용. 구조화 출력/가드 실패는 500 대신 **200 `clarify`** 로 degrade. **검색 Tool 미주입**(의도→명령 변환만) |
| `ai/tool/HouseTools.java` (수정) | `dealYmdError(String, YearMonth)` 가시성 `package-private` → **`public`** 승격(순수 함수, 가드 재사용) |
| `ai/agent/AgentCommandGuardsTest.java` (신규 테스트) | 정상 search 통과 / 비서울·잘못된 연월·미래월·미지원 action·null → clarify / 미인식 키 서버 통과(프론트가 무시) / reset·clarify 통과 — 9건 |

신규 프로퍼티 없음(`ai.chat.max-message-length`·`ai.chat.rate-limit.*`·`app.ai.chat.available` 재사용).

## 4. 프론트엔드 변경 (`no-home-frontend/src`)
| 파일 | 역할 |
|---|---|
| `houseSearchParams.js` (수정) | **capability 단일 출처** 추가 — `filterSchema`(9필드), `capabilities()`, `applyAgentFilters(filters, incoming)`(인식 키만 적용, 미인식 키는 `ignored` 반환). 기존 `emptyFilters`/`buildHouseSearchRequests` 등은 불변(순수 추가) |
| `chat/agentClient.js` (신규) | `parseAgentResponse({status,ok,body,retryAfter})` → `{kind:'command'|'error', ...}`. 상태 매핑은 `parseChatResponse`와 동일 |
| `components/ChatWidget.vue` (수정) | 헤더에 **질문/실행 토글**, `emits:['agent-command']`, props `currentFilters/currentPage/totalPages/agentResult`. 실행 모드는 `/api/ai/agent` 호출 후 명령 emit, App.vue가 돌려준 권위 요약(`agentResult`)을 답변 버블로 표시. 질문 모드 경로 불변 |
| `App.vue` (수정) | `handleAgentCommand`(search/setFilters/reset/clarify), `normalizeAgentFilters`(서울 sido 보정·법정동 재로드·정렬 복원 = side-effect 미러), `buildAgentSummary`(권위 요약 + 미인식 키 보고), `<ChatWidget>` 바인딩 |
| `agentMapping.test.js` · `chat/agentClient.test.js` (신규 테스트) | capabilities 9키 / applyAgentFilters 적용·무시 / 기존 빌더 라운드트립(lawdCd·dealYmd) / parseAgentResponse 상태 매핑 |
| `package.json` (수정) | `test` 스크립트에 신규 테스트 2개 추가 + `test:agent` |

## 5. API 계약 (`POST /api/ai/agent`, 로그인 전용)
**요청**
```json
{ "message": "마포구 2024년 3월로 검색해줘",
  "capabilities": ["sido","sigungu","umdNm","aptName","startDealMonth","endDealMonth","sort","minPrice","maxPrice"],
  "currentFilters": {}, "currentPage": 1, "totalPages": 1 }
```
**응답** `ApiResponse<AgentCommand>` — `data = { action, filters{}, page, direction, itemIndex, summary, clarify }`
**상태코드**: 400(빈/초과 message) · 401(비로그인) · 409(동시요청) · 429(rate limit, `Retry-After`) · 503(키 미설정/인증실패) · 504(타임아웃). **구조화 출력·가드 실패는 200 + `action:"clarify"`** (앱 안정).

## 6. ★ capability-driven 동작 (메인 필터 변경에 견고)
1. 프론트 `filterSchema` = **단일 출처**(검색 폼·에이전트가 공유).
2. 요청에 `capabilities()`(지원 키 목록) + 현재 UI 상태 동봉(서버 무상태).
3. 백엔드는 capability를 **프롬프트 allow-list 힌트**로만 주입 — 최종 강제는 프론트.
4. 프론트 `applyAgentFilters`가 **인식 키만 적용, 모르는 키는 무시 + 사후 보고**.
→ 메인 페이지가 필터를 추가/변경해도 **프론트 `filterSchema` 한 곳만 갱신**되면 에이전트가 자동 적응(에이전트·백엔드 코드 무변경). 신/구 버전 간에도 깨지지 않는다.

## 7. 검증 결과
| 항목 | 결과 |
|---|---|
| 백엔드 `./mvnw test` | **105건 그린** (신규 `AgentCommandGuardsTest` 9건 포함, 회귀 없음) |
| 프론트 `npm test` | **28건 그린** (신규 agentMapping·agentClient 포함) |
| 프론트 `npm run build` | 성공 (App.vue·ChatWidget SFC 컴파일 검증) |
| API E2E (full-stack 8080/5173/3307) | **9/9** — 비로그인→401 · search→`{서울특별시·강남구·2024-05}`+요약 · 비서울→clarify · reset · **질문 모드 회귀 정상** |
| 브라우저 스크린샷 테스트 | Claude Preview MCP 자체 브라우저(dev 5180→백엔드 8080): ① 질문/실행 토글 ② "마포구 2024년 3월로 검색해줘"→메인 필터 자동입력(서울특별시·마포구·2024-03·최신순)+검색 실행+요약 **"마포구·2024-03로 검색했어요."** ③ "부산 해운대 아파트 검색해줘"→**clarify**(필터 불변) |

## 8. 알려진 메모 (에이전트 동작과 무관)
- 검색 결과 0건/"검색 실패"는 **공공데이터 키 미적재**로 데이터 미존재 — 질문 모드와 동일한 데이터 계층 결과. 에이전트는 필터 채움+검색 실행까지 정확히 수행.
- Kakao 지도 SDK 로드 실패는 네이티브 dev 실행에 지도 키가 없어서(페이지 구조 유지). full-stack 도커 프론트(5173)에는 영향 없음.
- Chrome MCP가 조직 정책으로 `localhost`/`127.0.0.1` 차단 → **Claude Preview MCP 자체 브라우저**로 실제 화면 검증.

## 9. 현재 상태 / 후속
- **머지 완료** — backend [#16](https://github.com/repechage-team/no-home-backend/pull/16) · frontend [#5](https://github.com/repechage-team/no-home-frontend/pull/5) (양 레포 master 반영, fast-forward).
- 후속: ① **Phase 2**(paginate·mapFocus·selectItem + sort·umdNm·price) ② (선택) `isTimeout`/`isAuthFailure` 중복을 `AiProviderErrors`로 추출 ③ (선택) `ai.agent.enabled` 독립 kill-switch.
