---
title: 운영 환경 보안 설정 fail-open (JWT secret / 쿠키 Secure)
domain: _shared
type: issue
status: resolved
severity: high
author: 이정헌
created: 2026-06-21
updated: 2026-06-22
related:
  - ../../ai-chatbot/2026-06-21-backlog.md
---

# ISSUE: 운영 환경 보안 설정 fail-open (JWT secret / 쿠키 Secure)

분류: 인증/보안(교차). **AI 챗봇 도메인·현재 브랜치(`feature/jeongheon-chatbot`)와 무관** — 인증/보안 담당 또는 별도 브랜치에서 처리.

> 본 문서에는 실제 secret 값이나 개발용 기본 문자열 원문을 기록하지 않는다.

## 증상
운영 의도 배포에서 `JWT_SECRET` 등 보안 환경변수가 누락돼도, **개발용 기본 placeholder secret**과 **`Secure=false` 쿠키** 설정으로 그대로 기동된다(fail-open). 즉 운영에서 안전하지 않은 기본값으로 동작할 수 있다.

## 재현 방법
1. 운영을 가정하고 `JWT_SECRET`, `JWT_COOKIE_SECURE`를 설정하지 않은 채 백엔드를 기동한다.
2. 애플리케이션이 정상 기동된다(현재 `application.properties`가 모든 보안값에 개발 기본 fallback을 제공).
3. 발급되는 인증 쿠키가 `Secure` 미설정이며, 토큰 서명 secret이 저장소에 공개된 예측 가능한 기본값이다.

## 근본 원인
- `application.properties`의 `auth.jwt.secret`/`auth.jwt.cookie-secure`가 개발 기본값 fallback을 포함한다.
- 운영 전용 프로필과 시작 시 보안 설정 검증이 없어, 누락/취약 설정을 차단하지 못한다.

## 영향 범위
### 영향 받는 것
- 예측 가능한 secret로 인한 **JWT 위조 위험**.
- 비-`Secure` 쿠키의 평문 채널 전송 위험.
### 영향 받지 않는 것
- 로컬 개발 편의(무설정 기동)는 유지되어야 함 — 수정 시에도 보존 대상.

## 제안 수정안 (설계 보존, 별도 브랜치에서 구현)
1. **`src/main/resources/application-prod.properties` 추가**
   - `auth.jwt.secret=${JWT_SECRET}` — fallback 제거 → 누락 시 placeholder 미해소로 **기동 실패**.
   - `auth.jwt.cookie-secure=${JWT_COOKIE_SECURE:true}` — 운영 기본 Secure.
2. **`common/config/ProductionSecurityValidator` 추가** (`@Profile("prod")`, `InitializingBean`)
   - 개발 기본값 패턴(예: `local-development`/`change-me` 포함) 또는 `cookie-secure=false`면 `IllegalStateException`으로 **기동 차단(fail-closed)**.
   - **예외 메시지에 secret 값을 포함하지 않는다**(사유 문자열만).
   - 기존 `AuthWebConfig`/`MyBatisConfig`와 같은 `common/config` 패키지에 배치, 기존 파일은 수정하지 않음.
3. **정적 `validate(secret, cookieSecure)` 단위 테스트 4케이스**
   - 개발 기본값 → 예외(+메시지에 secret 미포함 단언), `cookieSecure=false` → 예외, 강한 secret + `true` → 통과, 둘 다 위반 → 사유 2건.
- 기본 프로필 동작 무변경: `JwtTokenService`/`AuthCookieService`/base `application.properties`/스모크 테스트 그대로 → 로컬 무설정 기동 유지.

## 완료 기준
- prod 프로필 + 보안값 누락/취약 시 기동 실패, prod + 강한 secret + Secure=true 시 정상 기동.
- 기본 프로필(로컬/CI 스모크) 무영향, 전체 테스트 통과.
- 로그/예외 어디에도 secret 값이 노출되지 않음.

## ✅ 해결 (2026-06-22) — 브랜치 `feature/jeongheon-jwt-fail-closed`
이슈 설계대로 구현·검증 완료(로컬). 기본/로컬 무설정 기동은 보존.
- `src/main/resources/application-prod.properties`: `auth.jwt.secret=${JWT_SECRET}`(fallback 제거), `auth.jwt.cookie-secure=${JWT_COOKIE_SECURE:true}`.
- `common/config/ProductionSecurityValidator`(`@Profile("prod")`, `InitializingBean`): 개발 기본값(`local-development`/`change-me`)·32자 미만·`cookie-secure=false`면 `IllegalStateException`으로 기동 차단(fail-closed). 예외 메시지에 secret 미포함.
- `ProductionSecurityValidatorTest` 4케이스 통과(전체 백엔드 82건 그린).
- 검증: 정적 `validate()` 단위테스트 + 기본 프로필 컨텍스트 부팅 무영향(@Profile prod라 미적용).

## 상태 / 메모
- **상태:** resolved(구현·테스트 완료, 로컬). **push/머지는 보안 동결 해제 후**(별도 브랜치 `feature/jeongheon-jwt-fail-closed`).
- **범위 제외:** 키 교체(이번 세션 별도 완료), Git 이력 재작성/force push(별도 승인 필요).
- 키 노출 이력 관련은 [Git 이력 비밀정보 잔존](2026-06-21-git-history-secret-exposure.md) 참조.
