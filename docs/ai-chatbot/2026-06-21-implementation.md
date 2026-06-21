---
title: AI 챗봇 구현 & E2E 검증 정리
domain: ai-chatbot
type: report
status: done
author: 이정헌
created: 2026-06-21
updated: 2026-06-21
related:
  - 2026-06-21-proposal.md
  - 2026-06-21-backlog.md
  - 2026-06-21-ai-logging-privacy-policy.md
  - 2026-06-21-usage-limiter-ux-scenarios.md
  - ../_shared/issues/2026-06-21-seed-data-double-encoding.md
  - ../_shared/issues/2026-06-21-prod-jwt-fail-closed.md
  - ../_shared/issues/2026-06-21-frontend-component-test-infra.md
  - troubleshooting/2026-06-21-ai-prompt-and-hallucination.md
---

# AI 챗봇 구현 & E2E 검증 정리

관련: [도입 제안서](2026-06-21-proposal.md)

## 1. 무엇을 만들었나
서울 아파트 실거래가를 자연어로 묻는 AI 챗봇. 사용자가 "동작구 2024년 5월 실거래가" 같이 물으면
LLM이 `@Tool`로 기존 실거래가 DB를 조회해 답한다. 기반: 사내 `spring-ai-project_lab`의 Tool 호출 에이전트 구조.

### 백엔드 (`com.ssafy.home.ai` 신규 패키지)
| 파일 | 역할 |
|---|---|
| `pom.xml` | Spring Boot 3.3.5→3.5.9, mybatis 3.0.4→3.0.5, `spring-ai-bom 1.1.2` + `spring-ai-starter-model-openai` |
| `application.properties` + `.env`/`.env.example` | `spring.ai.openai.*`, 키는 `SSAFY_GMS_API_KEY` 환경변수 주입(하드코딩 금지) |
| `ai/config/AiConfig.java` | `ChatClient` 빈 + 명시적 활성화 시에만 안전한 메타데이터를 남기는 `SimpleLoggerAdvisor` |
| `ai/tool/HouseTools.java` | `@Tool searchSeoulAptDeals` — `SeoulLawdCodeResolver`로 구→lawdCd 변환 후 기존 `HouseService.searchHouseDeals()` 재사용, 건수·평균/최저/최고가·대표거래 요약. **dealYmd 형식·범위 검증(YYYYMM·2006~현재·미래 불가), 자동 임포트 실패 친화 폴백(503 아님), 연월 미지정 시 DB우선+되묻기** |
| `ai/controller/AiChatController.java` | `POST /api/ai/chat`, `ApiResponse<String>`, temperature 0 + 고유명사 보존 프롬프트 |
| `common/config/AuthWebConfig.java` | 인터셉터에 `/api/ai/**` 추가 → **로그인 사용자 전용** |
| `member/auth/JwtTokenService.java` | DI 생성자에 `@Autowired` 추가(버전업으로 드러난 잠복 버그, [트러블슈팅](../_shared/troubleshooting/2026-06-21-version-upgrade.md)) |

### 프론트엔드
| 파일 | 역할 |
|---|---|
| `src/chat/chatClient.js` | 순수 로직 모듈(프레임워크 무관) — 상태 매핑(`parseChatResponse` 401/409/429+Retry-After/503/504)·code point 길이(`messageLength`/`clampToMaxLength`)·진행 단계. `node --test`로 단위 테스트 |
| `src/components/ChatWidget.vue` | 플로팅 챗 위젯, `fetch('/api/ai/chat', credentials:'include')`. `chatClient.js` 사용, **Unicode code point 기준 길이(HTML maxlength UTF-16 제거)**, **접근성(label/aria-live 답변 영역/aria-describedby/aria-busy)** |
| `src/App.vue` | 컴포넌트 등록 + `<ChatWidget :logged-in="!!member" />` |

## 2. 설계 결정 (팀 확정)
- 통합 방식: 기존 백엔드에 통합 + Boot 버전업 (별도 서비스 X)
- 용도: A(부동산 질의) → 이후 C(대화기억) 단계적
- 인증: 로그인 전용
- 키: 기존 키를 `.env`로 이전
- **자동 임포트 실패(외부 공공데이터) = graceful 폴백 문자열(200)** — 데이터 소스 일시 문제로 보고 503으로 던지지 않음(진짜 결함은 503 유지).
- **연월 미지정 질의 = DB우선 + 되묻기** — 보유 데이터 있으면 "전체 기간 기준" 명시 요약, 없으면 연월 되묻기. '최근 N개월 자동 임포트'는 N회 외부 호출·불확실성으로 미채택.
- **프론트 위젯 테스트 = 경량(로직 추출 + `node:test`)** — 컴포넌트 테스트 인프라(Vitest) 도입은 후속 이슈로 분리.

## 3. E2E 검증 결과 (로컬, Docker MySQL + GMS 실호출)
| 항목 | 결과 |
|---|---|
| 테스트 | ✅ 백엔드 **78건** + 프론트 **15건**(`houseSearchParams` 3 + `chatClient` 12), 프론트 프로덕션 빌드 통과 |
| 챗 오류 응답 | ✅ 빈 입력 400 / 모델·Tool 장애 503 / 타임아웃 504, 내부 예외 메시지 비노출 |
| 입력·호출 제한 | ✅ 500자 / 연속 보충 token bucket 분당 10개 / 동시 요청 1개 / 429 동적 `Retry-After` / 연결 3초·응답 25초·재시도 2회 |
| 프론트 요청 상태 UX | ✅ 전송 완료·데이터 확인·지연 상태 표시, 답변 중 입력 잠금, 500자 카운터, 429 재시도 시간 안내 |
| 프론트 위젯 브라우저 E2E | ✅ FAB·패널·인사말 렌더, 로그인 상태 반영, 질의·답변 표시, 콘솔 에러 0 |
| AI 로그 개인정보 보호 | ✅ 질문·답변·Tool 결과 원문 비기록, 진단 기능 기본 OFF, 활성화 시 건수·token 사용량만 기록 |
| 비로그인 `/api/ai/chat` | ✅ 401 차단 |
| 로그인 후 툴 호출 → DB 조회 | ✅ `searchSeoulAptDeals` 실행, 정확한 통계(건수/평균/최저/최고가) 반환 |
| LLM 한국어 응답 | ✅ |
| 아파트명 정확도 | ✅ **정상**(2026-06-21 인코딩 수정 후) — 아크로리버하임/흑석한강센트레빌/상도래미안 정확 반환 |
| 구/동 검색 필터 | ✅ 정상(인코딩 수정 후) — `sigungu=동작구` 검색 `totalCount=3` |

검증 질의: "동작구 2024년 5월 아파트 실거래가" (시드 11590/202405, complete-coverage 배치 → 외부 임포트 없이 DB 응답).

입력·호출 제한의 전체 사용자 흐름과 token 차감 기준은 [사용량 리미터 및 UX 사용자 시나리오](2026-06-21-usage-limiter-ux-scenarios.md)에 별도로 정리했다.
AI 로그의 허용 데이터와 환경별 활성화 절차는 [AI 챗봇 로그 및 개인정보 보호 정책](2026-06-21-ai-logging-privacy-policy.md)을 따른다.

## 4. 알려진 이슈 / 남은 일
- ✅ [ISSUE: 시드 데이터 더블 인코딩](../_shared/issues/2026-06-21-seed-data-double-encoding.md) — **resolved**(`data.sql` `SET NAMES utf8mb4;` + DB 재초기화). 이름표시·구/동 검색 동시 정상화.
- ⚠️ [ISSUE: 운영 환경 보안 설정 fail-open (JWT/쿠키)](../_shared/issues/2026-06-21-prod-jwt-fail-closed.md) — `open`(보안, 챗봇 범위 밖). 운영 프로필 fail-closed 제안만 기록.
- ⚠️ [ISSUE: 프론트 컴포넌트(DOM) 테스트 인프라 부재](../_shared/issues/2026-06-21-frontend-component-test-infra.md) — `open`(후속, Vitest 도입 팀 합의 필요).
- ☐ Phase 5: 대화기억(용도 C) — `FWA_03_AI_lab`의 `MessageWindowChatMemory` 패턴 차용 예정.

## 5. 트러블슈팅 기록 (종류별)
- [환경 이슈](../_shared/troubleshooting/2026-06-21-environment.md) — Docker/포트/MySQL/권한
- [버전업 이슈](../_shared/troubleshooting/2026-06-21-version-upgrade.md) — Spring Boot 3.5 전환
- [AI 응답/프롬프트](troubleshooting/2026-06-21-ai-prompt-and-hallucination.md) — "환각"처럼 보인 인코딩 문제
