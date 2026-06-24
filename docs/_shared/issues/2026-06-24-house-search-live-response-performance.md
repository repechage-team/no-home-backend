---
title: 실거래가 미캐시 첫 조회 응답 지연
domain: _shared
type: issue
status: resolved
severity: high
author: 최민식
created: 2026-06-24
updated: 2026-06-24
related:
  - ../reports/2026-06-23-apt-rent-schema-migration.md
---

# ISSUE: 실거래가 미캐시 첫 조회 응답 지연

분류: 성능 개선 / 공공데이터 자동 수집 파이프라인  
영향 범위: `house` 검색 API, `publicdata` 매매/전월세 수집 서비스, 브라우저 검색 결과 렌더링

## 증상

아파트 실거래가 검색에서 DB에 아직 캐시되지 않은 지역/월을 조회할 때 응답 시간이 과도하게 길어졌다.

대표 사례:

- 조건: 서울특별시 강남구, 2019년 1월, 월세
- 기존 응답 시간: 약 17초
- 공공 API 원천 데이터 수: 전월세 1,532건
- 사용자 관점 문제: 한 달짜리 조회임에도 검색 결과를 보기까지 오래 대기해야 함

기존 캐시가 이미 존재하는 동일 조건 재조회는 1초 이내로 빠르게 응답했다. 따라서 병목은 DB 조회 자체보다 미캐시 첫 조회 시 수행되는 자동 수집 및 저장 과정에 있었다.

## 기존 처리 방식

기존 검색 흐름은 DB 캐시 우선 방식이었다.

```text
사용자 검색
-> DB coverage 확인
-> coverage가 없으면 공공 API 호출
-> XML 파싱
-> API 결과 전체를 DB에 저장
-> 저장 완료 후 DB에서 다시 검색
-> 브라우저 응답
```

이 방식은 응답 데이터가 항상 DB 기준으로 생성된다는 장점이 있었지만, 미캐시 첫 조회에서는 사용자가 DB 저장 완료까지 기다려야 했다.

특히 저장 과정이 거래 1건 단위로 반복되었다.

```text
거래 1건마다
-> 지역 upsert
-> 지역 ID select
-> 아파트 upsert
-> 아파트 ID select
-> 거래 insert
```

강남구 2019년 1월 전월세처럼 1,500건 이상의 데이터가 있는 경우, 거래 건수에 비례해 수천 번의 DB 왕복이 발생했다.

## 근본 원인

공공 API 호출 자체보다 서버의 DB 적재 방식이 응답 경로에 포함되어 있는 것이 핵심 병목이었다.

주요 원인:

1. 검색 응답 전에 공공 API 결과 전체를 DB에 저장해야 했다.
2. 저장 방식이 row-by-row 구조라 DB 왕복이 과도하게 많았다.
3. 저장이 완료된 뒤 다시 DB 검색을 수행했다.
4. `numOfRows=100`으로 호출해 데이터가 많은 월은 API page 호출 수도 증가했다.

따라서 실제 사용자 응답 시간은 다음 비용을 모두 포함했다.

```text
공공 API 조회 시간
+ XML 파싱 시간
+ row-by-row DB 저장 시간
+ DB 재조회 시간
```

## 수정 방향

검색 응답 경로와 DB 저장 경로를 분리했다.

개선 후 구조는 API 응답 우선 + DB 백그라운드 캐시 방식이다.

```text
사용자 검색
-> DB coverage 확인
-> coverage가 없으면 공공 API 호출
-> XML 파싱
-> 서버 메모리에서 검색 결과 DTO 생성
-> 서버 메모리에서 필터/정렬/페이징
-> 브라우저 응답
-> 백그라운드에서 DB batch 저장
```

서버 메모리는 Spring Boot 애플리케이션이 실행되는 JVM 메모리를 의미한다. API 응답을 Java 객체 리스트로 변환하고, 요청 처리 중 임시로 필터링/정렬/페이징을 수행한다. 해당 리스트는 요청 처리 후 더 이상 참조되지 않으면 GC 대상이 되며, 서버 메모리에 영구 캐시로 누적되지 않는다.

## 적용 내용

### 1. live 검색 경로 추가

`HouseService`에서 `autoImport=true`이고 coverage가 미완성인 경우 기존 동기 import를 수행하지 않고 live 검색 경로를 사용한다.

- coverage 완료: DB 조회
- coverage 미완성: 공공 API live 조회 후 즉시 응답
- live 응답 후 DB 저장은 비동기 처리

### 2. live 응답용 검색 결과 생성

공공 API 응답을 DB 저장 전에 `HouseSearchResultResponse`로 변환한다.

DB 저장 전에는 `dealId`, `houseId`가 없을 수 있으므로 응답 item에 `resultKey`, `apiRowHash`를 추가했다.

브라우저는 목록 렌더링 key를 다음 우선순위로 사용한다.

```text
resultKey -> apiRowHash -> dealId -> houseId/date/floor fallback
```

### 3. batch 저장 방식 도입

기존 row-by-row 저장을 batch 저장 방식으로 변경했다.

기존:

```text
거래 1건마다
지역 저장 -> 지역 ID 조회 -> 아파트 저장 -> 아파트 ID 조회 -> 거래 저장
```

개선:

```text
전체 API 결과 수집
-> 지역 중복 제거 후 batch upsert
-> 지역 ID batch 조회
-> 아파트 중복 제거 후 batch upsert
-> 아파트 ID batch 조회
-> 거래 batch INSERT IGNORE
```

이를 통해 DB 왕복 횟수를 크게 줄였다.

### 4. 공공 API page 크기 확대

공공 API 호출 시 `numOfRows` 기본값을 `100`에서 `1000`으로 확대했다.

예를 들어 1,532건 데이터는 기존에는 약 16페이지 호출이 필요했지만, 개선 후에는 약 2페이지 호출로 줄어든다.

## 영향 범위

영향을 받는 주요 경로:

- `GET /api/houses/search`
- `GET /api/houses/price-range`
- 매매 실거래가 자동 수집
- 전월세 실거래가 자동 수집
- 브라우저 검색 결과 목록 key 생성

기존 API 요청 파라미터는 유지했다. 외부 호출 방식은 바꾸지 않고, 서버 내부 조회/저장 파이프라인을 변경했다.

## 검증 결과

수정 후 다음 검증을 수행했다.

```text
backend: .\mvnw.cmd test
backend: .\mvnw.cmd -q -DskipTests package
frontend: npm.cmd test -- --run
frontend: npm.cmd run build
```

검증 결과:

- backend 테스트 126개 통과
- frontend 테스트 47개 통과
- backend package 성공
- frontend production build 성공

브라우저 수동 검증:

- DB 초기화 후 미캐시 조건으로 강남구 2019년 1월 월세 조회
- 검색 결과 표시 확인
- 기존처럼 DB 저장 완료를 기다리는 17초 수준의 응답 지연이 완화됨
- 같은 조건 재조회 시 DB 캐시 기반으로 더 빠르게 응답

## 주의사항

live 검색 결과는 요청 처리 중 서버 메모리에 임시로 적재된다. 영구 누적 캐시는 아니지만, 동시에 많은 사용자가 큰 범위의 live 조회를 수행하면 순간 메모리 사용량이 증가할 수 있다.

운영 안정성을 위해 다음 제한을 유지하거나 추가하는 것이 좋다.

1. 자동 live 조회 범위 제한
2. 동시 live 조회 개수 제한
3. 서울 전체와 같은 대량 범위 조회 제한
4. coverage 완료 데이터는 DB 조회 우선 사용
5. 브라우저 응답은 페이징 유지

## 관련 코드

- `house/service/HouseService`
- `house/dto/HouseSearchResultResponse`
- `publicdata/service/PublicDataLiveSearchService`
- `publicdata/service/PublicDataBatchPersistService`
- `publicdata/service/PublicDataImportService`
- `publicdata/service/PublicDataAptRentImportService`
- `publicdata/mapper/PublicDataImportMapper`
- `resources/mappers/publicdata/PublicDataImportMapper.xml`
- `resources/mappers/house/HouseMapper.xml`
