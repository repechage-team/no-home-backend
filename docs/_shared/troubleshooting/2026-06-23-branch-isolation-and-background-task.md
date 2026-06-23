---
title: "브랜치 격리 — master 직접 작업 + 백그라운드 작업이 같은 working copy를 건드림"
domain: _shared
type: troubleshooting
status: resolved
author: 이정헌
created: 2026-06-23
updated: 2026-06-23
related:
  - ../../ai-chatbot/reports/2026-06-23-agent-mode-phase2-implementation.md
---

# 트러블슈팅: 브랜치 격리 (master 직접 작업 + 백그라운드 작업 충돌)

여러 작업이 한 working copy에서 동시에 진행될 때 변경이 엉뚱한 브랜치에 얹히는 함정.

## 증상
- A 기능(에이전트 Phase 2)을 `master` 작업트리에서 **브랜치 없이** 편집하던 중,
- 무관한 B 수정(App.vue mojibake 1줄)을 **백그라운드 작업으로 위임**했더니,
- 그 백그라운드 작업이 **같은 working copy**에서 `master → feature/...-mojibake` 브랜치를 만들고 커밋함.
- 결과: A의 미커밋 변경이 working tree에 그대로 떠 있어 **mojibake 브랜치 위에 얹힘**.
  커밋 단계에서 `git status`의 현재 브랜치가 의도(master 기반)와 달라 발견됨.

## 원인
- **A. 착수 전 브랜치 미생성** — 코드 편집을 `master` 작업트리에서 바로 시작(브랜치 생성을 "구현 완료 후"로 미룸). master에 미커밋 변경이 떠 있는 상태.
- **B. 미커밋 중 백그라운드 작업** — 미커밋 변경이 있는 동안 같은 repo를 대상으로 백그라운드 작업을 띄웠고, 그 작업이 같은 working copy의 브랜치를 전환·커밋함(격리된 worktree가 보장되지 않음 — `git worktree list` 결과 worktree는 하나뿐).
- 두 원인이 겹쳐 무관한 두 변경(A/B)이 한 working tree에서 뒤섞임.

## 해결 (이번 사례)
미커밋 A 변경을 master 기반의 독립 브랜치로 분리:
```
git stash push -u                       # A의 미커밋 변경 + untracked 파일 격리
git switch master
git switch -c feature/<A>               # master 기반 새 브랜치
git stash pop                           # A 변경만 복원 (B의 커밋은 따라오지 않음)
git add <A의 파일들>                    # 선별 staging
git commit
```
B(mojibake)는 자신의 브랜치에 그대로 두어 독립 PR로 처리. 데이터 손실 없음.
> A·B가 같은 파일(App.vue)을 수정했지만 변경 영역이 달라 `stash pop`이 자동 병합됐고, 이후 두 PR도 충돌 없이 머지됨.

## 예방 (근본)
- **코드 편집 첫 단계 전에 feature 브랜치를 만든다.** `git switch -c feature/...` 후 작업. 기본 브랜치 작업트리에서 직접 편집·커밋하지 않는다. → 다른 작업이 master를 건드려도 내 작업은 격리된다.
- **미커밋 변경이 있는 동안 같은 repo에 백그라운드 작업을 던지지 않는다.** 먼저 커밋하거나 `git stash`로 격리한 뒤 위임한다. 무관한 즉석 수정은 메모/이슈로 기록만 하고 현재 흐름이 끝난 뒤 처리한다.
- 커밋은 `git add -A` 대신 그룹별 선별 staging으로 무관한 변경의 혼입을 막는다.

## 빠른 점검
- 작업 시작 시: `git rev-parse --abbrev-ref HEAD` 로 feature 브랜치인지 확인(기본 브랜치면 먼저 분기).
- 백그라운드 작업 위임 전: `git status --porcelain` 가 비어 있는지 확인.
