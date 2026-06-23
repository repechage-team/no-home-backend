---
title: "자동임포트 실패 — resultCode '000' 미처리 (+ 진단도구 UA 차단 오진단)"
domain: _shared
type: troubleshooting
status: resolved
author: 이정헌
created: 2026-06-23
updated: 2026-06-23
related:
  - ../issues/2026-06-22-autoimport-error-categorization.md
  - ../../ai-chatbot/reports/2026-06-23-agent-mode-phase2-implementation.md
---

# 트러블슈팅: 자동임포트 resultCode "000" 미처리 (+ UA 오진단)

## 증상
질문/에이전트 모드에서 미적재 월 질의가 전부 "조회할 수 없습니다"로 응답. 검색 API는
`Auto import failed for lawdCd=..., dealYmd=...`, 로그는 `reason=PROVIDER_ERROR`.

## 진단
| 호출 경로 | 결과 | 의미 |
|---|---|---|
| data.go.kr 콘솔(`getRTMSDataSvcAptTrade`) | `200 · resultCode=000 · OK` | 키·활용신청·API 정상 |
| 컨테이너에서 백엔드 UA(`Java/21.0.9`)로 호출 | `200 · resultCode=000 · totalCount=328` | **백엔드는 호출 성공** |
| curl·wget 기본 UA로 호출 | `400 Request Blocked` | 진단 도구 UA만 차단(WAF) |

## 근본 원인
- **`resultCode=000` 미처리**: 백엔드 `AptTradeApiResponse.isSuccess()`가 **"00"만** 성공으로 인정 →
  RTMS 정상 코드 **"000"** 을 실패로 보고 `PROVIDER_ERROR`로 오분류. 정상 데이터(예: 강남구 202405,
  totalCount 328)가 "조회 불가"로 위장됨.
- **첫 발생**: `e683784`(BE #13, 이슈 [#9](https://github.com/repechage-team/no-home-backend/issues/9) 구현,
  2026-06-22). resultCode 검증을 *새로 도입*하며 성공코드를 "00"으로 추정 하드코딩. 그 이전엔 resultCode를
  안 봐서 문제없었음(과거 실증이 통과한 이유).
- **왜 안 걸렸나**: 성공 케이스 테스트가 실 응답이 아닌 추정값 `<resultCode>00</resultCode>`로 작성됨
  (버그와 동일 가정 복제). 이슈 #9가 "비성공 코드"만 정의하고 "성공 코드"를 명시하지 않아 추정이 유입됨.

## 해결
1. **성공 판정을 명세 출처 기반 화이트리스트로**: `NORMAL_RESULT_CODES = {"00","000"}`.
   - 근거: data.go.kr 공통표준 정상 `00`(오류는 01/04/12/20/22/30/31/32/99 등 비-0) + RTMS resultCode
     정상 `000`(콘솔/실호출 실측). `0+` 같은 정규식 일반화는 **명세 근거 없어 배제**.
2. **테스트를 실측 계약으로**: 성공 케이스 `00→000` 교정 + `000`+item 정상 임포트 회귀 테스트 + `isSuccess` 단위테스트.
3. **실패 관측 강화**: 자동임포트 실패 WARN 로그에 `resultCode/resultMsg` 노출(secret 아님) → 재진단 가속.
4. (방어) `PublicDataAptTradeClient` 명시 User-Agent(`no-home/1.0`).

검증: `./mvnw test` 116 그린 + 재기동 후 강남구 202405 자동임포트 실증(타워팰리스3 등) + 챗봇 평균가 정상 응답.

## 교훈
- **오진단 주의**: "400 Request Blocked"를 네트워크/방화벽 차단으로 속단했으나, 실제는 *진단 도구(curl/wget) UA* 차단.
  → 외부 호출 디버깅 시 **운영 코드와 동일한 클라이언트/UA로 재현**해야 한다.
- **추정·휴리스틱 단정 금지**: 성공코드를 `0+` 정규식으로 일반화하려 했으나 명세 근거가 없었다. 외부 계약
  (응답코드·매직값·포맷)은 **1차 출처(공식 명세·실측 응답)로 검증**한 뒤 명시 상수+출처 주석으로 둔다.
  테스트도 추정값을 복제하지 말고 실측 계약값을 쓴다.

## 부수 메모 (조치 불요)
- data.go.kr 앞단 WAF가 curl/wget 기본 UA를 차단함. 운영(백엔드)엔 영향 없음. 수동 진단 시 `-A`/`--user-agent`로
  일반 UA 지정하면 통과.
