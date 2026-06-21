---
title: 환경 이슈 (Docker / 포트 / MySQL / 권한)
domain: _shared
type: troubleshooting
status: resolved
author: 이정헌
created: 2026-06-21
updated: 2026-06-21
---

# 트러블슈팅: 환경 이슈 (Docker / 포트 / MySQL / 권한)

E2E 검증 중 발생한 로컬 환경 문제와 해결책. 모두 AI 코드와 무관한 인프라 이슈.

## 1. 포트 3306을 기존 MySQL이 점유
- 증상: 호스트 3306에 Windows 서비스 `MySQL`(MySQL Server 8.0)이 떠 있고, 우리 `no_home` 계정이 없어 접속 거부(`Access denied`).
- 결정: 기존 서비스를 잠시 중지하고 Docker MySQL을 3306으로 기동(백엔드 기본 설정 유지).
- ⚠️ **환경 원상복구 필요**: 테스트 후 `Start-Service MySQL`로 기존 서비스 재시작.

## 2. Docker Desktop 엔진 미기동
- 증상: `docker ps` → `open //./pipe/dockerDesktopLinuxEngine: ... cannot find the file`.
- 해결: Docker Desktop 실행 후 `docker info`가 성공할 때까지 대기(폴링).

## 3. Windows 서비스 중지에 관리자 권한 필요
- 증상: `Stop-Service MySQL` → `Cannot open MySQL service`(권한 부족).
- 해결: UAC 권한 상승으로 실행 — `Start-Process powershell -Verb RunAs -ArgumentList '-Command','Stop-Service MySQL -Force'`. (사용자가 UAC 승인)

## 4. Docker MySQL이 한동안 `unhealthy`
- 증상: `docker compose up -d` 직후 `health=unhealthy`.
- 원인: 최초 기동 시 InnoDB 초기화 + `schema.sql`/`data.sql` 적재에 시간이 걸림(healthcheck `start_period` 초과 전).
- 해결: healthy 될 때까지 충분히 대기(폴링). 조급하게 판단하지 말 것.

## 5. 포트 8080을 Oracle TNS Listener가 점유
- 증상: 백엔드 기동 시 `Web server failed to start. Port 8080 was already in use`. 점유 프로세스 = `TNSLSNR`(Oracle).
- 해결: 백엔드를 다른 포트로 — `java -jar ... --server.port=8081`. (curl E2E엔 무관; 프론트 프록시 사용 시엔 8080 정리 또는 프록시 타깃 변경 필요)

## 6. 장기 실행 서버 기동 시 도구 호출 타임아웃
- 증상: `mvnw spring-boot:run`(안 끝나는 프로세스) + 긴 폴링 루프를 한 호출에 묶어 타임아웃.
- 해결 패턴(타임아웃-안전):
  1. 빌드는 유한 작업으로 분리: `mvnw -DskipTests package`
  2. 실행은 분리/즉시 반환: `Start-Process java -ArgumentList '-jar','target\...jar' -WorkingDirectory <dir> -RedirectStandardOutput target\app.log -WindowStyle Hidden`
  3. 헬스 폴링은 **별도의 짧은 호출**(`/api/health` 최대 ~60s)
- jar 직접 실행이라 recompile 모호성도 없음. 워킹디렉터리를 backend로 줘야 `.env` 자동 로드.

## 환경 원상복구 체크리스트
- [ ] 백엔드 java 프로세스 종료
- [ ] `docker compose down` (필요 시 `-v`)
- [ ] `Start-Service MySQL` (중지했던 기존 서비스 복구)
