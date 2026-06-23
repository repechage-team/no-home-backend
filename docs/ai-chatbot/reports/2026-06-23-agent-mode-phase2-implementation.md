---
title: AI 에이전트 모드 Phase 2 구현 & 검증 정리 (필터 + 액션)
domain: ai-chatbot
type: report
status: done
author: 이정헌
created: 2026-06-23
updated: 2026-06-23
related:
  - 2026-06-22-agent-mode-mvp-implementation.md
  - ../proposals/2026-06-22-agent-mode.md
  - ../backlog/2026-06-23-backlog.md
---

# AI 에이전트 모드 Phase 2 구현 & 검증 정리

관련: [MVP 리포트](2026-06-22-agent-mode-mvp-implementation.md) · [제안서](../proposals/2026-06-22-agent-mode.md) · [백로그](../backlog/2026-06-23-backlog.md)

## 1. 무엇을 만들었나
[MVP](2026-06-22-agent-mode-mvp-implementation.md)(액션 `search·setFilters·reset·clarify` + 필터 `지역·날짜·아파트명`)에 이어 Phase 2 잔여를 두 슬라이스로 구현했다.
- **PR A — 필터 4종**: `sort` · `umdNm`(읍면동) · `minPrice` · `maxPrice`.
- **PR B — 액션 3종**: `paginate` · `mapFocus` · `selectItem`(예약 필드 `page·direction·itemIndex` 첫 사용).

설계 원칙은 MVP 계승: **capability = 단일 출처, 프론트가 최종 강제자.** 백엔드 프롬프트는 보조,
백엔드 가드는 레포 독립·안전 불변식만 강제한다.

## 2. PR A — 필터 (sort / umdNm / minPrice / maxPrice)

### 핵심: apply 경로는 이미 동작
프론트 `filterSchema`(9필드 전부 선언)·generic `applyAgentFilters`·`buildHouseSearchRequests`가
이미 4필드를 적용·전송하고, 백엔드 검색(`HouseSearchCondition`/`HouseController`/`HouseService`)도
4필드를 지원한다. → **백엔드 검색 코드 변경 0.**

### 백엔드 (`com.ssafy.home.ai`)
| 파일 | 변경 |
|---|---|
| `controller/AiAgentController.java` | `SYSTEM_PROMPT_TEMPLATE`에 값 포맷 안내 추가: `sort`=`latest\|oldest\|priceDesc\|priceAsc`(기본 latest, 지역 있을 때만), `minPrice/maxPrice`=만원 정수 문자열(`'50000'`=5억, min≤max), `umdNm`=자치구 내 법정동. 키 목록은 기존 capability allow-list(`%s`) 재사용 |
| `agent/AgentCommandGuardsTest.java` | sort/price/umdNm 포함 `search`가 명령 변경 없이 통과함을 고정(값 가드 무추가 원칙) — 1건 |

**필터 값 가드 무추가(의도).** sort enum·price 숫자·umdNm 유효성은 프론트가 강제(silently drop/normalize).
서버 가드는 하드 백엔드 제약(서울 지역·연월 범위)에만 둔다.

### 프론트엔드 (`no-home-frontend/src`)
| 파일 | 변경 |
|---|---|
| `App.vue` | `buildAgentSummary`에 가격대(`describeAgentPrice`, `displayManwon` 재사용)·실효 정렬(`describeAgentSort`, `SORT_OPTIONS` 재사용) 표기 추가. `normalizeAgentFilters`는 무변경(이미 sort↔지역·umdNm 보존 처리) |
| `agentMapping.test.js` | 4키 apply / 빌더 round-trip(umdNm·sort·price) / 지역 없으면 sort 미전송 — 3건 추가 |

### UI 결합 처리(코드 변경 없이 정합)
- **sort without region**: `normalizeAgentFilters`가 `!sido`면 `sort='latest'` 다운그레이드(기존). 요약은
  **normalize 이후 실효값**을 읽어 무시된 정렬을 거짓 주장하지 않음.
- **price before range loaded**: `filters.minPrice/maxPrice`는 슬라이더와 독립 데이터 → `search` 시 정확히
  전송. min>max는 백엔드가 거부(기존 에러 경로 보고).
- **umdNm before legalDongs loaded**: 검색 요청은 passthrough라 dong 로드 전에도 정확. select 표시만
  로드 후 hydrate. `normalizeAgentFilters`가 `'umdNm' in applied`면 보존.

## 3. PR B — 액션 (paginate / mapFocus / selectItem)

### 인덱싱 규약
**에이전트 경계는 1-based(1=첫 번째), 프론트에서 0-based 변환.** `page`는 전 구간 1-based.
off-by-one을 단일 변환 지점(`resolveItemTarget`)에 가둠.

### 백엔드
| 파일 | 변경 |
|---|---|
| `agent/AgentCommandGuards.java` | `ALLOWED_ACTIONS`에 paginate/mapFocus/selectItem 추가. `validate`에 액션 가드: paginate=`page≥1` 또는 `direction∈{next,prev}` 중 하나(아니면 clarify), mapFocus·selectItem=`itemIndex≥1`(아니면 clarify). **상한(totalPages·결과건수)은 서버 stateless·stale 가능 → 미검사**, 프론트가 라이브 clamp |
| `controller/AiAgentController.java` | 프롬프트에 액션별 설명 + `totalPages`를 상태 줄에 주입(`buildSystemPrompt`가 `request.totalPages()` 포맷, 기존 수신만 하던 값 활용) |
| `agent/AgentCommandGuardsTest.java` | paginate(page/direction 유효·둘다없음·비양수)·item(index 유효·null/0) — 7건 추가 |

### 프론트엔드
| 파일 | 변경 |
|---|---|
| `chat/agentActions.js` (신규) | 순수 결정 로직 `resolvePaginateTarget(command, state)`·`resolveItemTarget(itemIndex, itemCount)` → `{ok, targetPage/index, message}`. DOM/지도/검색 호출은 App.vue가 수행, 여기선 계산·검증만(단위 테스트 가능) |
| `App.vue` | `handleAgentCommand`에 3 case 추가. paginate→`resolvePaginateTarget`+`searchHouses(targetPage)`, selectItem→`resolveItemTarget`+`selectItem(item)`, mapFocus→`focusMapItem(item)`(지도 미준비 시 graceful). 모두 **기존 메서드 재사용** |
| `chat/agentActions.test.js` (신규) | clamp(마지막/범위밖/동일/첫페이지)·direction·display 'all'·미검색 / 1-based 변환·범위밖·빈 목록 — 13건 |
| `package.json` | `test`·`test:agent`에 agentActions.test.js 추가 |

## 4. API 계약 (변경 없음, 필드 활용 확대)
`POST /api/ai/agent` 요청/응답 record 그대로. PR B가 응답 `AgentCommand`의 예약 필드를 사용:
`paginate`→`page`/`direction`, `mapFocus·selectItem`→`itemIndex`(1부터). 상태코드 매핑·degrade-to-clarify 동일.

## 5. 검증 결과
| 항목 | 결과 |
|---|---|
| 백엔드 `./mvnw test` | **113건 그린**(`AgentCommandGuardsTest` 9→17, 회귀 없음) |
| 프론트 `npm test` | **44건 그린**(agentMapping +3, agentActions +13) |
| 프론트 `npm run build` | 성공(App.vue SFC 컴파일) |

> 지도 focus(`mapFocus`)의 실제 center/zoom과 selectItem 상세 표시는 Kakao 지도·geocoding 의존이라
> 브라우저 E2E 영역(후속 수동 검증). 경계·인덱스 로직은 `agentActions` 순수 테스트로 커버.

## 6. 결정 / 후속
- **필터 값 가드 무추가**(프론트 최종 강제) · **1-based 항목 인덱스** · **상한 프론트 clamp**(서버 하한만) 확정.
- **defer**: `isTimeout`/`isAuthFailure` 중복 → `AiProviderErrors` 추출 · `ai.agent.enabled` kill-switch. 별도 후속 소형 PR.
- 머지 순서: 백엔드(allow-list·프롬프트) → 프론트(액션 case). PR A 프론트는 capability에 키가 이미 있어 선행 가능.
