---
title: 공통 / 교차 문서 인덱스 (_shared)
domain: _shared
type: index
status: living
updated: 2026-06-21
---

# _shared — 공통 / 교차(cross-cutting) 문서

여러 도메인에 걸치거나 인프라·빌드 전반에 해당하는 문서. (한 도메인 한정 문서는 해당 도메인 폴더로)

## 메타 / 규칙
- [문서 작성 가이드](guide/documentation-guide.md)
- 템플릿: [document](templates/document-template.md) · [issue](templates/issue-template.md) · [troubleshooting](templates/troubleshooting-template.md) · [adr](templates/adr-template.md)
- [문서 규약/위치 전략 — 결정 대기](reports/2026-06-21-docs-strategy-and-onboarding-context.md) — `status: open` (새 팀 결정 안건)

## 이슈 (issues/)
- [2026-06-22 자동 임포트 오류 원인 미구분 (resultCode 미파싱)](issues/2026-06-22-autoimport-error-categorization.md) — `status: open`, `severity: medium` (publicdata/house 담당)
- [2026-06-21 Git 이력의 비밀정보 및 생성 파일 잔존](issues/2026-06-21-git-history-secret-exposure.md) — `status: in-progress`, `severity: high`
- [2026-06-21 운영 환경 보안 설정 fail-open (JWT/쿠키)](issues/2026-06-21-prod-jwt-fail-closed.md) — `status: open`, `severity: high`
- [2026-06-21 프론트 컴포넌트(DOM) 테스트 인프라 부재](issues/2026-06-21-frontend-component-test-infra.md) — `status: open`, `severity: low`
- [2026-06-21 시드 데이터 더블 인코딩](issues/2026-06-21-seed-data-double-encoding.md) — `status: resolved`

## 트러블슈팅 (troubleshooting/)
- [2026-06-22 로컬 full-stack · 디렉터리 · 한글 인코딩 함정](troubleshooting/2026-06-22-local-fullstack-and-encoding.md)
- [2026-06-21 Spring Boot 3.5 버전업](troubleshooting/2026-06-21-version-upgrade.md)

## 의사결정 (adr/)
- *(없음)* — 필요 시 `adr/0001-*.md` 로 추가 (예: Spring AI 도입 + Boot 버전업)
