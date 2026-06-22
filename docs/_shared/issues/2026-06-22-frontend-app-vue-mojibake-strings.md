---
title: 프론트 App.vue 한글 문자열 mojibake (소스 인코딩 손상)
domain: _shared          # 프론트 표시 계층(UI 문구). 챗봇/백엔드 기능과 무관
type: issue
status: open
severity: low
author: 이정헌
created: 2026-06-22
updated: 2026-06-22
related:
  - ../../ai-chatbot/2026-06-21-backlog.md
  - 2026-06-21-seed-data-double-encoding.md
  - 2026-06-21-frontend-component-test-infra.md
---

# ISSUE: 프론트 App.vue 한글 문자열 mojibake

분류: 선재(pre-existing) 프론트 인코딩 버그 · 영향: `App.vue`가 렌더하는 한글 안내 문구(기능 영향 없음)

## 증상
화면에 표시되는 일부 한글 안내가 깨져(mojibake) 나온다. 예) 지도 영역(`VITE_KAKAO_MAP_API_KEY` 미설정 시):

```
VITE_KAKAO_MAP_API_KEY媛 ?ㅼ젙?섏? ?딆븯?듬땲??
```
정상 문구는 `VITE_KAKAO_MAP_API_KEY가 설정되지 않았습니다.` 다. 회원(가입/로그인/수정/탈퇴) 안내와 검색 상태/오류 메시지에서도 동일하게 깨진다.

> 발견 경위(2026-06-22): 브라우저(http://localhost:5173)에서 지도 미표시 + 깨진 안내. 지도 미표시는 **Kakao 키 미설정**이 원인이었고 키 입력 후 정상화. 그러나 깨진 안내 문구는 **별개의 소스 인코딩 버그**로 확인됨.

## 범위
- **단일 파일: `Frontend/src/App.vue`** — mojibake 라인 **20개**.
  (스캔 기준: 한글 호환자모 `U+3131–U+318F` + CJK 한자 + 치환문자 `U+FFFD`)
  - 라인: 100, 104, 119, 224, 342, 363, 371, 427, 431, 449, 453, 469, 473, 489, 493, 500, 515, 519, 578, 600
  - 카테고리: **지도(Kakao) 오류 메시지**, **회원 인증 안내**(가입/로그인/로그아웃/수정/탈퇴), **검색 상태·오류 메시지**
- **영향 없음**: `src/chat/ChatWidget.vue`, `src/chat/chatClient.js` 등 **챗 모듈은 정상**(깨끗하게 새로 작성됨). 챗봇 기능/문구와 무관.
- 같은 `App.vue` 안에서도 일부 문자열(예: line 20·850)은 **정상 한글** → 파일 전체가 아니라 **일부 리터럴만** 손상.

## 근본 원인 (추정)
- 파일 자체는 현재 **UTF-8(BOM)·CRLF**.
- 깨진 리터럴은 *원래 한글이 EUC-KR/CP949로 저장돼 있다가 UTF-8로 잘못 변환(또는 그 반대)* 된 전형적 mojibake 패턴(`媛`,`釉`,`濡`,`?ㅼ젙` 등).
- 일부 바이트가 `?`(U+003F)로 **소실**되어 있어(예: `?ㅼ젙?섏? ?딆븯?듬땲??`) **자동 역변환만으로는 원문 복원이 불완전**하다. → **문맥 기반 수동 복원**이 정확.

## 영향 / 심각도
- **기능 영향 없음** — 동작은 정상이고 **렌더되는 안내 문구만** 깨짐. 사용자 신뢰/가독성 저하.
- severity: **low** (cosmetic). 단, 사용자 노출 텍스트라 배포 전 정리 권장.

## 제안 수정안 (후속 `fix(front): …`)
1. `App.vue`의 깨진 20개 문자열을 **문맥에 맞는 올바른 한글로 복원**.
   - 예: line 100 → `'VITE_KAKAO_MAP_API_KEY가 설정되지 않았습니다.'`
2. 파일을 **UTF-8로 재저장**하고 BOM/CRLF 일관성 확인(프로젝트 규약에 맞춤).
3. **재발 방지**: `.editorconfig`(charset=utf-8) 정비, 가능하면 lint/CI에서 비정상 인코딩(호환자모·치환문자) 감지 가드 검토.

## 검증 기준
- 스캔 0건:
  ```bash
  node -e 'const fs=require("fs");const re=/[ㄱ-㆏㐀-鿿�]/;const h=[];fs.readFileSync("src/App.vue","utf8").split(/\r?\n/).forEach((l,i)=>re.test(l)&&h.push(i+1));console.log(h.length, h.join(","))'
  ```
- 브라우저에서 지도/회원/검색 안내 문구가 **정상 한글**로 노출.

## 참고
- 연관(데이터 계층의 한글 인코딩): [시드 데이터 더블 인코딩](2026-06-21-seed-data-double-encoding.md) — 원인 계층은 다르지만(이쪽은 프론트 소스, 그쪽은 DB 적재) 같은 "한글 인코딩 손상" 범주.
- 현재 `App.vue`에는 자동화 컴포넌트 테스트가 없어(→ [프론트 컴포넌트 테스트 인프라](2026-06-21-frontend-component-test-infra.md)) 문구 회귀를 잡는 안전망이 약함.
