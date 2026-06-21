---
title: AI 챗봇 Claude Code 작업 인계
domain: ai-chatbot
type: report
status: done
author: 이정헌
created: 2026-06-21
updated: 2026-06-21
related:
  - 2026-06-21-backlog.md
  - 2026-06-21-implementation.md
  - 2026-06-21-usage-limiter-ux-scenarios.md
  - 2026-06-21-ai-logging-privacy-policy.md
  - ../_shared/issues/2026-06-21-git-history-secret-exposure.md
---

# AI 챗봇 Claude Code 작업 인계

## 1. 목적

Codex에서 진행한 AI 챗봇 구현·안정화·문서화 작업을 Claude Code가 대화 기록 없이 이어받을 수 있도록 현재 저장소 상태, 설계 결정, 검증 결과, 잔여 위험과 다음 작업을 고정한 인계 스냅샷이다.

이 문서를 먼저 읽고 실제 `git status`와 대조한다. 차이가 있으면 저장소 상태를 우선하며, 차이와 예상 영향을 사용자에게 먼저 보고한다.

## 2. 저장소 구성

GitHub Organization은 다음 3개 저장소로 구성된다.

- `Artifact`: 전체 서비스 조립과 Docker Compose 등 통합 실행 구성
- `Frontend`: Vue/Vite 프론트엔드
- `Backend`: Spring Boot API 서버와 중앙 문서

현재 로컬 작업 경로는 다음과 같다.

```text
C:\SSAFY\workspace\no-home\
├─ no-home-backend
└─ no-home-frontend
```

`Artifact` 저장소 안내는 유효하므로 README에서 제거하지 않는다.

## 3. 반드시 읽을 문서

1. 이 문서
2. [남은 작업 백로그](2026-06-21-backlog.md)
3. [구현 및 E2E 검증 정리](2026-06-21-implementation.md)
4. [사용량 리미터 및 UX 사용자 시나리오](2026-06-21-usage-limiter-ux-scenarios.md)
5. [AI 로그 및 개인정보 보호 정책](2026-06-21-ai-logging-privacy-policy.md)
6. [Git 이력 비밀정보 잔존 이슈](../_shared/issues/2026-06-21-git-history-secret-exposure.md)

## 4. Git 상태 스냅샷

기준 시점: 2026-06-21, 인계 문서 작성 직전.

### Backend

- 브랜치: `feature/jeongheon-chatbot`
- 원격 대비: `ahead 1`
- 로컬 정리 커밋: `07f45f4 chore: stop tracking local secrets and build output`
- 수정된 추적 파일:
  - `.env.example`
  - `pom.xml`
  - `src/main/java/com/ssafy/home/common/config/AuthWebConfig.java`
  - `src/main/java/com/ssafy/home/member/auth/JwtTokenService.java`
  - `src/main/resources/application.properties`
  - `src/main/resources/data.sql`
- 미추적 구현:
  - `src/main/java/com/ssafy/home/ai/`
  - `src/test/java/com/ssafy/home/ai/`
  - `src/test/java/com/ssafy/home/HomeApplicationTest.java`
- `docs/` 전체가 미추적 상태다. 이 인계 문서를 포함하면 문서 파일은 19개다.

### Frontend

- 브랜치: `feature/jeongheon-chatbot`
- 원격 대비: `ahead 1`
- 로컬 정리 커밋: `ff334ea chore: stop tracking local secrets and generated files`
- 수정된 추적 파일: `src/App.vue`
- 미추적 구현: `src/components/ChatWidget.vue`

### Git 주의사항

- 두 정리 커밋은 로컬에만 있고 아직 원격에 push하지 않았다.
- `.env`, `target/`, `node_modules/`, `dist/` 등 금지 경로의 최신 추적 결과는 양쪽 모두 0건이다.
- 기능 변경과 문서를 분리 검토해야 하므로 `git add -A`를 사용하지 않는다.
- 기존 사용자 변경을 되돌리거나 `git reset --hard`, `git checkout --`를 실행하지 않는다.

## 5. 완료된 구현

### Backend AI

- Spring Boot `3.5.9`, Spring AI `1.1.2` 적용.
- `POST /api/ai/chat` 추가 및 `/api/ai/**` 로그인 인터셉터 보호.
- `HouseTools.searchSeoulAptDeals`가 기존 `HouseService.searchHouseDeals`를 재사용해 서울 아파트 실거래가를 조회한다.
- 시드에 없는 서울 구·연월은 기존 자동 임포트 경로를 사용할 수 있다.
- 모델 temperature `0`, Tool 조회 의무·추측 금지·고유명사 보존 시스템 프롬프트 적용.

### 오류·사용량 제한

- 빈 입력·500자 초과: 400
- 인증 사용자 식별 실패: 401
- 사용자별 동시 요청: 1건, 중복 요청은 409
- token 부족: 429와 동적 `Retry-After`
- 모델·Tool 장애와 빈 모델 응답: 503
- 중첩 타임아웃: 504
- token bucket: 사용자별 최대 10 token, 분당 10개 연속 보충
- HTTP 연결 3초, 개별 응답 25초, AI 최대 2회 시도
- limiter 획득 후 모든 종료 경로에서 `finally`로 `inFlight` 해제

### Frontend UX

- `App.vue`에 `ChatWidget` 연결.
- 로그인 전 안내와 로그인 후 질의 전송.
- 전송 직후 사용자 메시지 표시 및 입력창 잠금.
- 0초·3초·12초 단계별 진행 문구.
- 처리 중 전송 버튼을 `답변 중`으로 표시.
- 429 `Retry-After`를 읽어 재시도 가능 시간을 안내.
- 같은 페이지에서 패널을 닫았다 열어도 대화·로딩 상태 유지.
- `aria-busy`, `role="status"`, 로딩 상태 `aria-live` 적용.

### 로그·개인정보 보호

- `SimpleLoggerAdvisor`는 기본 미등록이며 logger 기본값은 `OFF`다.
- 명시적으로 활성화해도 질문·답변 원문 대신 메시지 수, 결과 수, Tool 호출 여부, token 사용량만 기록한다.
- `HouseTools` DEBUG 로그는 조회 조건과 결과 원문을 제거하고 집계 건수만 기록한다.
- 시스템 프롬프트, 사용자 질문, 모델 답변, Tool 입력·결과, 회원 정보와 키를 로그에 기록하지 않는다.

### 데이터·문서

- `data.sql`에 `SET NAMES utf8mb4;`를 적용해 한글 더블 인코딩을 해결했다.
- 아파트명과 구·동 필터가 정상화됐다.
- AI 챗봇 구현, 백로그, limiter UX 시나리오, 로그 정책, 트러블슈팅 문서를 작성했다.
- 문서 작성자는 `이정헌`으로 통일했다.

## 6. 확정된 결정

- `Artifact`·`Frontend`·`Backend` 3개 저장소 구조를 유지한다.
- 현재 범위에서는 키를 교체하지 않는다.
- 최신 Git 추적을 해제하고 전체 이력 재작성·접근 제한을 통해 추가 노출을 줄이는 방향이다.
- 이력 재작성과 원격 강제 갱신은 협업 영향이 크므로 사용자 승인과 팀 조율 없이 실행하지 않는다.
- 답변 생성 중 UX는 현재 `steering`이나 취소가 아닌 입력 잠금 방식이다.
- 브라우저 요청 중단만으로 서버·모델 작업 취소를 보장할 수 없으므로 가짜 취소 버튼을 추가하지 않는다.
- 503·504 요청은 모델·Tool 자원을 이미 사용했을 수 있어 limiter token을 환불하지 않는다.
- 개발 환경에서도 프롬프트·응답 원문 로그를 허용하지 않는다.

## 7. 최신 검증 결과

| 검증 | 결과 |
|---|---|
| Backend 전체 테스트 | 72건 통과, 실패·오류·건너뜀 0 |
| full-context 부팅 | H2·테스트용 AI 키로 성공 |
| Frontend 기존 테스트 | 3건 통과 |
| Frontend 프로덕션 빌드 | 성공 |
| `npm audit --omit=dev --audit-level=high` | 취약점 0건 |
| 비로그인 챗 API | 401 |
| 실서버 인증 E2E | 회원가입 201 → 로그인 200 → 회원 조회 200 |
| 실서버 AI E2E | 동작구 2024년 5월 질의 200, 답변 존재 |
| 감사용 임시 계정 | E2E 직후 삭제 200 |
| Frontend proxy | 화면 200, `/api/health` 200, 비로그인 챗 401 |
| 문서 상대 링크 | 깨진 링크 0건 |
| 금지 경로 최신 추적 | Backend·Frontend 모두 0건 |
| 새 GMS 키 커밋 흔적 | 확인 범위 0건 |

Frontend의 3개 테스트는 `ChatWidget` 테스트가 아니라 `houseSearchParams.test.js`의 검색 파라미터 테스트다. 위젯 로딩·오류·잠금·접근성은 자동화 공백으로 남아 있다.

현재 Backend와 Frontend 개발 서버는 중지 상태다. `no-home-mysql` 컨테이너는 3306 포트에서 `healthy` 상태로 실행 중이다.

## 8. 현재 판정과 잔여 위험

기능 기준선은 동작하지만 현재 push·merge·운영 배포 판정은 `NO-GO`다.

### P0 — 선행 필요

1. Backend·Frontend 과거 Git 이력에 `.env`가 남아 있다.
2. 운영 환경변수 누락 시 알려진 개발용 JWT secret과 `Secure=false` 쿠키 설정으로 기동할 수 있다. 운영 프로필은 필수 값 누락 시 실패하도록 바꾸는 것이 안전하다.
3. 기능 변경과 `docs/`가 아직 미커밋·미추적이며 커밋 단위 분리가 필요하다.

### P1 — 안정성·UX 보완

1. `ChatWidget` 자동 테스트가 없다.
2. HTML `maxlength`는 UTF-16 단위이고 화면 카운터·Backend는 code point 기준이어서 이모지 포함 시 500자 경계가 다르다.
3. 챗 입력에 명시적 접근 가능 label이 없고 일반 답변 영역에 `aria-live`가 없어 최종 답변 전달이 약하다.
4. limiter는 단일 인스턴스 인메모리 구조이며 사용자 상태 제거가 없어 장기 실행과 수평 확장에 한계가 있다.
5. 전체 요청 deadline, circuit breaker, 일일 한도와 운영 지표가 없다.
6. Tool의 `dealYmd`는 설명상 `YYYYMM`이지만 실행 시 형식을 검증하지 않는다.

### P2 — 문서·정합성

1. Backend·Frontend README에 AI API, `ChatWidget`, 저장소별 실행 흐름을 보강해야 한다.
2. 문서 인덱스와 백로그 추천 순서를 최종 갱신해야 한다.
3. `docs/`를 선별 추가하고 Git 추적 여부를 확인해야 한다.

## 9. 추천 재개 순서

1. 두 저장소의 실제 `git status`를 이 문서와 비교하고 차이를 보고한다.
2. 사용자가 다음 우선순위를 정하지 않았다면 P0 중 안전한 로컬 작업부터 제안한다.
3. 운영 JWT 설정을 fail-closed로 바꾸는 범위와 로컬 개발 편의 유지 방식을 합의한다.
4. `ChatWidget` 테스트·Unicode 길이 기준·접근성을 보완한다.
5. Backend·Frontend README와 문서 인덱스를 현행화한다.
6. 파일을 아래 커밋 단위로 선별 staging한다.
7. 전체 회귀 테스트와 로그인·비로그인 E2E를 다시 수행한다.
8. Git 전체 이력 재작성은 별도 승인과 팀 조율 후 마지막에 진행한다.

## 10. 권장 커밋 분리

### 이미 생성된 정리 커밋

- Backend `07f45f4`: 비밀 설정·빌드 산출물 추적 해제
- Frontend `ff334ea`: 비밀 설정·의존성·빌드 산출물 추적 해제

이 커밋에 기능 변경을 amend하지 않는다.

### Backend AI 및 안정화

```text
.env.example
pom.xml
src/main/java/com/ssafy/home/ai/**
src/main/java/com/ssafy/home/common/config/AuthWebConfig.java
src/main/java/com/ssafy/home/member/auth/JwtTokenService.java
src/main/resources/application.properties
src/test/java/com/ssafy/home/HomeApplicationTest.java
src/test/java/com/ssafy/home/ai/**
```

### 시드 인코딩 수정

```text
src/main/resources/data.sql
```

### Frontend 챗 위젯

```text
src/App.vue
src/components/ChatWidget.vue
```

### 문서

```text
docs/**
```

실제 staging 전에는 각 그룹의 `git diff`를 다시 확인한다. `git add -A` 대신 파일 또는 디렉터리를 명시한다.

## 11. 재검증 명령

### Backend

```powershell
.\mvnw.cmd test
```

완료 기준: 72건 이상, 실패·오류 0, `HomeApplicationTest` context 부팅 성공.

### Frontend

```powershell
npm.cmd run test:auto-import
npm.cmd run build
npm.cmd audit --omit=dev --audit-level=high
```

현재 `test:auto-import`는 챗 위젯을 검증하지 않으므로 `ChatWidget` 테스트 추가 후 테스트 스크립트와 완료 기준도 갱신한다.

### 문서·Git

```powershell
git status --short --branch
git diff --check
git ls-files .env target node_modules dist
```

## 12. Claude Code 재개 프롬프트

```text
C:\SSAFY\workspace\no-home 작업을 이어서 진행해줘.

먼저 다음 순서로 읽어:
1. no-home-backend/docs/ai-chatbot/2026-06-21-claude-code-handoff.md
2. no-home-backend/docs/ai-chatbot/2026-06-21-backlog.md
3. no-home-backend/docs/_shared/issues/2026-06-21-git-history-secret-exposure.md

그다음 Backend와 Frontend의 git status를 직접 확인하고 인계 문서와 다른 점을 먼저 보고해줘.

주의:
- 기존 사용자 변경을 되돌리지 말 것
- git add -A를 사용하지 말 것
- 키 교체는 현재 범위에서 하지 말 것
- 실제 비밀값을 출력하거나 문서에 기록하지 말 것
- Git 이력 재작성이나 force push는 별도 승인 없이 실행하지 말 것
- 변경 전에 다음 미완료 백로그 작업과 수정 대상 파일을 제안할 것
```

## 13. 인계 완료 기준

- Claude Code가 이 문서와 저장소만으로 현재 구현·결정·검증·잔여 위험을 설명할 수 있다.
- 실제 `git status`와 인계 스냅샷 차이를 식별한다.
- 비밀값을 출력하지 않고 다음 작업의 범위와 대상 파일을 제안한다.
- 사용자 승인 없이 이력 재작성·강제 push·키 교체를 실행하지 않는다.
