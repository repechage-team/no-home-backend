---
title: AI 챗봇 로그 및 개인정보 보호 정책
domain: ai-chatbot
type: report
status: done
author: 이정헌
created: 2026-06-21
updated: 2026-06-21
related:
  - 2026-06-21-implementation.md
  - 2026-06-21-backlog.md
  - 2026-06-21-usage-limiter-ux-scenarios.md
---

# AI 챗봇 로그 및 개인정보 보호 정책

## 1. 목적

AI 챗봇 장애를 진단할 수 있는 최소한의 운영 정보를 남기면서, 사용자의 질문·모델 답변·Tool 조회 결과·인증 정보가 애플리케이션 로그를 통해 노출되지 않도록 기준을 정의한다.

이 정책은 개발·테스트·운영 환경에 동일하게 적용한다. 개발 환경이라는 이유만으로 사용자 입력이나 모델 응답 원문을 기록하지 않는다.

## 2. 로그 데이터 분류

### 기록 금지

- 시스템 프롬프트와 사용자 질문 원문
- 모델 응답 원문
- Tool 입력값과 Tool 결과 원문
- 아파트명, 법정동, 상세 거래가격·거래일 등 개별 조회 결과
- 회원 ID, 이메일, 세션·쿠키·JWT
- API 키, DB 비밀번호 및 환경변수 값
- 공급자 오류 응답 본문과 예외 메시지·스택 트레이스의 무검토 출력
- Spring AI advisor context의 키와 값

### 기록 허용

- 요청에 포함된 메시지 개수
- 모델 응답 결과 개수와 Tool 호출 여부
- 공급자가 제공한 prompt·completion·total token 수
- Tool 조회의 전체 건수, 표본 건수, 가격 데이터 건수, 결과 잘림 여부
- HTTP 상태별 집계, 처리 시간, 오류 유형별 집계처럼 원문을 포함하지 않는 운영 지표

허용 항목도 다른 데이터와 결합해 사용자를 식별하는 용도로 사용하지 않는다.

## 3. 구현 결정

### `SimpleLoggerAdvisor`

- 기본값은 등록하지 않음(`AI_CHAT_DIAGNOSTIC_LOGGING_ENABLED=false`).
- 명시적으로 활성화해도 Spring AI 기본 문자열 변환기를 사용하지 않는다.
- 요청은 메시지 개수만, 응답은 결과 개수·Tool 호출 여부·token 사용량만 기록하는 별도 변환기를 사용한다.
- advisor logger도 기본 `OFF`이므로 advisor 등록과 DEBUG 레벨을 각각 명시해야 출력된다.

### `HouseTools`

- 기존의 조회 조건과 결과 요약 전체 DEBUG 로그를 제거했다.
- DEBUG 활성화 시에도 `totalCount`, `sampleCount`, `pricedCount`, `truncated`만 기록한다.
- 사용자 질문, 구·동·아파트명·거래연월, 개별 거래가격은 기록하지 않는다.

### 오류

- 사용자 응답에는 기존과 같이 내부 예외 내용을 포함하지 않는다.
- 현재 AI Controller는 공급자 예외 원문과 스택 트레이스를 로그에 남기지 않는다.
- 향후 오류 로그를 추가할 때는 오류 분류와 예외 클래스처럼 검토된 메타데이터만 기록하고, 원문 출력은 별도 보안 검토 없이 추가하지 않는다.

## 4. 환경별 설정

| 환경 | `AI_CHAT_DIAGNOSTIC_LOGGING_ENABLED` | `AI_CHAT_DIAGNOSTIC_LOG_LEVEL` | `AI_CHAT_LOG_LEVEL` | 결과 |
|---|---:|---|---|---|
| 로컬 기본 | `false` | `OFF` | `INFO` | AI 진단 DEBUG 로그 없음 |
| 테스트 | `false` | `OFF` | `INFO` | 테스트가 명시적으로 포착하는 로그 외에는 없음 |
| 운영 기본 | `false` | `OFF` | `INFO` | AI 진단 DEBUG 로그 없음 |
| 임시 로컬 진단 | `true` | `DEBUG` | `DEBUG` | 안전한 advisor·Tool 메타데이터만 출력 |
| 운영 장애 진단 | 승인 후 일시적으로 `true` | 일시적으로 `DEBUG` | 필요 시 일시적으로 `DEBUG` | 안전한 메타데이터만 제한 시간 동안 출력 |

설정 변경은 애플리케이션 재시작 후 적용된다. 운영에서 진단 로그를 활성화할 때는 시작 시각·담당자·종료 예정 시각을 기록하고, 확인 직후 기본값으로 복구한다.

## 5. 로컬 진단 절차

Git에서 제외된 로컬 `.env`에 다음 값을 임시로 적용한다.

```properties
AI_CHAT_DIAGNOSTIC_LOGGING_ENABLED=true
AI_CHAT_DIAGNOSTIC_LOG_LEVEL=DEBUG
AI_CHAT_LOG_LEVEL=DEBUG
```

진단 종료 후 아래 기본값으로 되돌린다.

```properties
AI_CHAT_DIAGNOSTIC_LOGGING_ENABLED=false
AI_CHAT_DIAGNOSTIC_LOG_LEVEL=OFF
AI_CHAT_LOG_LEVEL=INFO
```

로그를 이슈·메신저·문서에 첨부하기 전에도 원문이나 비밀정보가 섞이지 않았는지 다시 확인한다.

## 6. 보관과 접근

- 운영 로그 접근 권한은 장애 대응 담당자에게만 부여한다.
- AI 진단 로그의 권장 보관 기간은 최대 14일이며, 더 긴 보관이 필요하면 목적과 접근 범위를 별도로 승인한다.
- 애플리케이션은 로그 보관 기간과 저장소 접근 제어를 직접 강제하지 않는다. 배포·로그 수집 환경에서 설정해야 한다.
- 로그를 분석용 데이터셋이나 모델 학습 데이터로 재사용하지 않는다.

## 7. 검증 기준

- 진단 기능 기본값이 비활성화되어야 한다.
- 명시적 활성화 전에는 `SimpleLoggerAdvisor`가 `ChatClient`에 등록되지 않아야 한다.
- 활성화된 요청·응답 요약에도 테스트용 이메일, 질문, advisor context가 포함되지 않아야 한다.
- Tool DEBUG 로그에 구·동·아파트명·거래연월·개별 가격이 포함되지 않아야 한다.
- `.env.example`에는 안전한 운영 기본값만 있어야 한다.

자동화 검증은 `AiConfigTest`와 `HouseToolsTest`에서 수행한다. 전체 구현 현황은 [AI 챗봇 구현 & E2E 검증 정리](2026-06-21-implementation.md), 후속 운영 안전망은 [AI 챗봇 남은 작업 백로그](2026-06-21-backlog.md)를 참고한다.

검증 결과: 백엔드 전체 테스트 72건 통과, 실패·오류·건너뜀 0건.
