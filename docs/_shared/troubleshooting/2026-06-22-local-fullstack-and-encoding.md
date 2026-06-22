---
title: "로컬 full-stack 셋업 · 디렉터리 · 한글 인코딩 함정"
domain: _shared
type: troubleshooting
status: resolved
author: 이정헌
created: 2026-06-22
updated: 2026-06-22
related:
  - ../issues/2026-06-21-seed-data-double-encoding.md
  - ../issues/2026-06-22-frontend-app-vue-mojibake-strings.md
---

# 트러블슈팅: 로컬 full-stack · 디렉터리 · 한글 인코딩 함정

새 환경에서 반복되기 쉬운 함정 모음(온보딩용).

## (a) docker MySQL 포트 충돌 → 3307
- **증상**: `docker compose up -d mysql` 시 `ports are not available: ... 0.0.0.0:3306 ... bind` 실패.
- **원인**: 호스트에 로컬 MySQL 서비스(`mysqld.exe`)가 이미 3306 점유.
- **해결**: `Backend/.env`의 `MYSQL_PORT=3307`, `DB_URL=...localhost:3307...`로 변경(컨테이너 내부는 3306 유지). full-stack에선 백엔드가 docker 네트워크의 `mysql:3306`으로 붙으므로 호스트 포트(3307)는 외부 점검용.
- 한글 시드는 정상 UTF-8(검증: `HEX(LEFT(apt_nm,1))` → `EC../ED..` 3바이트, `bytes=chars×3`).

## (b) full-stack 디렉터리 표준화 + rename 잠금
- **증상**: `Artifact/docker-compose.yml`로 full-stack 기동 시 `../Backend`·`../Frontend` 경로 미해석(클론명이 `no-home-*`였음).
- **해결**: 클론 디렉터리를 **`Backend`/`Frontend`/`Artifact`** 로 표준화(compose가 이 이름 전제). 이후 `MYSQL_PORT=3307 docker compose up --build -d`로 mysql+backend+frontend 기동.
- **rename 잠금 함정**: 디렉터리 rename이 `Device or resource busy`/`Permission denied`로 실패 → **잔존 `java`/`node` 프로세스가 디렉터리를 잡고 있어서**다. 포트(8080/5173) 리스너 + 해당 PID를 종료한 뒤 rename 성공. 종료 직후 옛 경로에 **빈 `target/`** 잔재가 생길 수 있어 정리.
- 디렉터리 네이밍은 팀 합의 사항(클론명 표준화 vs compose 경로 변경) — 백로그 "문서/팀 결정" 참조.

## (c) 한글 인코딩 함정 3종
1. **bash/curl이 한글을 CP949로 인코딩**(`Invalid UTF-8 middle byte 0xd7`) → 한글 JSON 본문이 깨짐. → **Node `fetch`로 E2E**(UTF-8 네이티브)로 우회. 마찬가지로 한글 PR/이슈 본문은 `--body-file`(UTF-8 파일)로 전달.
2. **`docker exec mysql` 콘솔이 한글을 `?????`로 표시** → **데이터 손상 아님**(Windows 콘솔 코드페이지 문제). 진위는 `HEX(...)`/`LENGTH vs CHAR_LENGTH`로 판정.
3. **소스 파일의 mojibake 복원**: 깨진 문자열을 편집 입력으로 라운드트립하면 **또 변형**될 수 있다(매칭 실패). → **줄번호 기반 치환**(올바른 한글만 작성) + `node` 스캔(`[ㄱ-㆏㐀-鿿�]`)으로 잔존 0 검증. 사례: [App.vue mojibake](../issues/2026-06-22-frontend-app-vue-mojibake-strings.md).

## 교훈
- 로컬 포트 충돌·디렉터리 규약·콘솔 표시는 **"데이터/코드 문제"로 오인하기 쉽다** — 표시 계층과 실제 바이트를 분리해 확인하라.
- 한글이 섞인 셸 작업은 UTF-8 경로(Node/`--body-file`/줄번호 치환)를 우선한다.
