---
title: 프론트 컴포넌트(DOM) 테스트 인프라 부재 — ChatWidget UI 자동 회귀 공백
domain: _shared
type: issue
status: open
severity: low
author: 이정헌
created: 2026-06-21
updated: 2026-06-21
related:
  - ../../ai-chatbot/2026-06-21-backlog.md
  - ../../ai-chatbot/2026-06-21-implementation.md
---

# ISSUE: 프론트 컴포넌트(DOM) 테스트 인프라 부재

분류: 프론트 빌드/테스트 도구(교차). ChatWidget을 계기로 식별했으나 결정은 프론트 전반에 영향.

## 증상 / 배경
- `no-home-frontend`에는 컴포넌트(DOM) 테스트 도구가 없다. 테스트는 `node --test`로 순수 JS 모듈만 검증한다(`houseSearchParams.test.js`, `chat/chatClient.test.js`).
- 따라서 `ChatWidget`의 **DOM·템플릿 동작**(로딩 중 입력 잠금, 진행 문구 단계, `aria-live`/label/`aria-describedby` 접근성, 버튼 상태)은 자동 회귀 테스트로 고정되지 않고 Preview 브라우저 수동 검증에 의존한다.

## 현재 조치 (옵션 1, 완료)
- 버그 취약 로직을 순수 모듈로 분리해 `node --test`로 잠금:
  - `src/chat/chatClient.js`: `parseChatResponse`(401/409/429+Retry-After/503/504/`success:false` 매핑), `messageLength`/`clampToMaxLength`(code point 기준), `PROGRESS_STAGES`.
  - `src/chat/chatClient.test.js` 12건.
- 접근성·Unicode 길이 기준은 코드에 반영하고 Preview 브라우저로 확인(라벨/aria-describedby/answer live region, 카운터 code point).

## 제안 (옵션 2, 후속 — 팀 합의 필요)
- devDependency 추가: `vitest` + `@vue/test-utils` + `jsdom`(또는 `happy-dom`), `vite.config.js`에 test 환경 설정.
- `src/components/ChatWidget.spec.js`로 위젯 동작 자동 테스트: 로그인 전/후, fetch mock 기반 401/409/429/503/504, 입력 잠금·진행 문구, 접근성 속성(label/aria-live/aria-busy), 이모지 카운터.
- `package.json`에 `test:unit`(vitest) 추가, 완료 기준·CI 갱신.

## 트레이드오프 (요약)
- 옵션 1: 새 의존성 0·컨벤션 일치·빠름. DOM/접근성 자동 회귀는 공백.
- 옵션 2: DOM·접근성까지 자동 회귀. devDependency +3·설정·팀 합의 비용. jsdom과 실제 브라우저 미세 차이 가능.

## 완료 기준
- ChatWidget의 로딩/잠금/에러 흐름과 접근성 속성이 CI에서 자동 검증된다.
- `npm audit --omit=dev --audit-level=high` 0건(새 devDependency 포함).

## 상태 / 메모
- **상태:** open(후속). 현재 브랜치에서는 옵션 1로 마무리, 옵션 2는 의존성 도입 합의 후 진행.
