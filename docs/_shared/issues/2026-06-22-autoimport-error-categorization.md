---
title: 자동 임포트 오류 원인 미구분 (resultCode 미파싱 → 무효/만료 키가 "데이터 없음"으로 위장)
domain: _shared          # publicdata + house(+ai 소비) 교차
type: issue
status: open
severity: medium
author: 이정헌
created: 2026-06-22
updated: 2026-06-22
related:
  - ../../ai-chatbot/backlog/2026-06-22-backlog.md
---

# ISSUE: 자동 임포트 오류 원인 미구분

분류: 데이터 정확성/운영 관측성 (보안 아님) · 담당: **공공데이터 호출·조회(`publicdata`/`house`) 영역** (챗봇 `ai`는 소비자 측만)

## 증상
자동 임포트(`HouseService#ensureCoverage` → `PublicDataImportService` → `PublicDataAptTradeClient`) 실패가 **원인별로 구분되지 않는다.**
- 챗봇(`HouseTools`)은 모든 `AutoImportException`을 동일한 일반 폴백 문구로 안내.
- REST(`HouseController`)는 일괄 `502`로 응답.

## 근본 결함 (핵심)
`PublicDataAptTradeXmlParser`가 `totalCount`/`item`만 파싱하고 **`resultCode`/`resultMsg`(data.go.kr 표준 응답 헤더)를 읽지 않는다.**
data.go.kr은 **HTTP 200 본문의 결과코드**로 오류를 반환한다: `30`(서비스키 미등록), `31`(활용기간 만료), `22`(트래픽 초과), 게이트웨이 인증오류(`<cmmMsgHeader>` 봉투) 등.
→ 파서가 `<item>`을 못 찾아 `totalCount=0, items=[]`로 반환 → **무효/만료 키·쿼터 초과가 "거래 0건(데이터 없음)"으로 위장**된다.
결과: 사용자는 "그 달 거래 없음"으로 **오안내**, 운영자는 **키 만료/쿼터 초과를 인지하지 못함.**

## 영향 / 심각도
- severity: **medium** — 데이터 정확성, 운영 관측성 저하. 보안 노출 아님.

## 제안 수정안 (담당자용)
1. **`publicdata/client/PublicDataAptTradeXmlParser`**: 표준 헤더(`<header><resultCode>`)와 게이트웨이 오류 봉투(`<cmmMsgHeader><returnReasonCode>/<returnAuthMsg>`)에서 결과코드/메시지 파싱 → `AptTradeApiResponse`(`resultCode`/`resultMsg` 추가)에 포함.
2. **`publicdata/service/PublicDataImportService`**: 비성공 결과코드 → 코드별 분류 예외(`KEY_MISSING`/`KEY_INVALID`/`QUOTA`/`PROVIDER_ERROR`). **성공 + 0건은 정상 `NO_DATA`로 유지(예외 아님).**
3. **`house/service/AutoImportException`**: `Reason` enum 추가. `HouseService#ensureCoverage`에서 원인 분류 — 키 미설정(`IllegalStateException`), 타임아웃(`ResourceAccessException`/`SocketTimeoutException`), API 코드, 그 외 일반.
4. **소비 측 분기**: `house/controller/HouseController` 상태코드(KEY/QUOTA→503, TIMEOUT→504, PROVIDER_ERROR→502), `ai/tool/HouseTools` 원인별 친화 문구(secret·원문 미노출).

## 완료 기준
- 무효/만료 키·쿼터 초과가 "데이터 없음(정상 0건)"과 **구분되어** 사용자 안내 + 운영 로그에 드러남.
- 원인별 단위 테스트(파서/임포트/HouseTools/HouseController) 통과, 정상 임포트·정상 0건 회귀 없음.

## 참고 코드 경로
`publicdata/client/PublicDataAptTradeXmlParser`, `publicdata/client/PublicDataAptTradeClient`, `publicdata/service/PublicDataImportService`, `house/service/HouseService`(`ensureCoverage`), `house/service/AutoImportException`, `ai/tool/HouseTools`, `house/controller/HouseController`.

## 참고
- GitHub 이슈: (생성 후 링크 추가) — `repechage-team/no-home-backend`.
- 연관: 챗봇의 자동 임포트 폴백은 현재 친화 문자열로 처리됨([2026-06-22 백로그](../../ai-chatbot/backlog/2026-06-22-backlog.md) 참고).
