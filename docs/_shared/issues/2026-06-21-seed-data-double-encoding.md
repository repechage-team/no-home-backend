---
title: 시드 데이터(data.sql) 한글 더블 인코딩
domain: _shared          # 교차(cross-cutting): 데이터/인프라 계층, 한글 반환 API 전반
type: issue
status: resolved
severity: medium
author: 이정헌
created: 2026-06-21
updated: 2026-06-21
related:
  - ../../ai-chatbot/troubleshooting/2026-06-21-ai-prompt-and-hallucination.md
---

# ISSUE: 시드 데이터(data.sql) 한글 더블 인코딩

분류: 선재(pre-existing) 데이터/인프라 버그 (AI 챗봇과 무관) · 영향: 한글 데이터를 반환하는 모든 API

## 증상
`house_deals`/`houses` 등 DB에 저장된 한글(아파트명·법정동명)이 깨져서 조회됨.
- `GET /api/houses/search` 결과의 `aptNm`, `sigungu`, `umdNm` 가 mojibake.
- `POST /api/ai/chat` 답변에서 아파트명이 엉뚱하게 나옴(모델이 깨진 입력을 받아 그럴듯한 이름으로 "복원").
- 숫자/영문/코드 내 정적 한글은 정상.

## 근본 원인: 더블 인코딩
```
SELECT apt_nm, HEX(apt_nm) FROM houses LIMIT 1;
-- 흑석한강센트레빌  C3ADC29DE28098C3AC...
```
`C3AD C29D E28098 ...` 는 한글 UTF-8 바이트가 **latin1로 오해석된 뒤 다시 utf8mb4로 저장**된 전형적 패턴.
- `@@character_set_database` / `@@character_set_server` = utf8mb4 (서버 설정은 정상)
- `data.sql` 파일 자체는 정상 UTF-8 (직접 열어 확인)
- 즉 **docker-entrypoint가 init 스크립트를 적재할 때 세션 `character_set_client`가 utf8mb4가 아니어서** 더블 인코딩으로 저장됨.

## 왜 헷갈렸나
`docker exec ... mysql`(기본 클라이언트)로 SELECT하면 정상처럼 보임 — 클라이언트 charset이 저장 시 오류를 우연히 되돌려 표시하기 때문. 반면 앱 JDBC(`characterEncoding=UTF-8`)는 저장된 깨진 바이트를 그대로 읽어 mojibake가 됨. (더블 인코딩의 전형적 함정)

## 영향 받지 않는 경로
실시간 자동 임포트(공공데이터 → JDBC `INSERT`)는 올바른 charset으로 저장되므로 정상일 가능성이 높음. **깨지는 것은 `data.sql` 시드 데이터.**

## 제안 수정안
1. `src/main/resources/data.sql` 최상단에 `SET NAMES utf8mb4;` 추가 (docker는 파일별로 별도 세션 실행 → 한글 포함 파일에 필요).
2. 또는 docker-compose의 init 적재가 utf8mb4를 쓰도록 보장.
3. **기존 손상 데이터는 `ON DUPLICATE KEY UPDATE`로는 안 고쳐짐** → DB 재초기화 필요: `docker compose down -v && docker compose up -d`.
4. 검증: `SELECT HEX(apt_nm)` 가 정상 UTF-8(예: 흑 = `ED9DBD`)인지, `/api/houses/search`·`/api/ai/chat`가 올바른 아파트명을 반환하는지.

## ✅ 해결 (2026-06-21)
- 수정: `src/main/resources/data.sql` 최상단에 `SET NAMES utf8mb4;` 추가.
- 재초기화: `docker compose down -v && docker compose up -d` (기존 손상 데이터는 `ON DUPLICATE KEY`로 안 고쳐지므로 볼륨 리셋 필요).
- 검증 결과:
  - `HEX(apt_nm)` = 정상 UTF-8 (예: 상도래미안 = `EC8381EB8F84EB9E98EBAFB8EC9588`, 동작구 = `EB8F99EC9E91EAB5AC`). 이전 `C3AD…` 더블인코딩 해소.
  - `GET /api/houses/search?sido=서울특별시&sigungu=동작구&dealYmd=202405` → `totalCount=3` (이전 0건). **구/동 검색 필터 복구**.
  - `POST /api/ai/chat` → 정확한 아파트명 반환(아크로리버하임/흑석한강센트레빌/상도래미안). LLM 이름 환각 소멸.
- 부수 효과: `HouseTools`는 `lawdCd` 우회 중이지만, 이제 `searchHouseDeals`의 `sido/sigungu/umdNm` 필터도 정상 매칭됨(P0-2 해소).

## 참고
- 진단 로그: `HouseTools`에 `log.debug`로 툴 결과 출력 추가됨(원인 특정에 사용).
- 상세 경위: [ai-prompt-and-hallucination](../../ai-chatbot/troubleshooting/2026-06-21-ai-prompt-and-hallucination.md)
