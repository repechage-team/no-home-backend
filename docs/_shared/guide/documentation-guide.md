---
title: 문서 작성 가이드 (구조·명명·메타데이터 규칙)
domain: _shared
type: guide
status: living          # 리빙 문서(상시 갱신)
author: 이정헌
created: 2026-06-21
updated: 2026-06-22
---

# 문서 작성 가이드

`docs/`의 단일 규칙 출처. 새 문서를 만들기 전에 이 문서를 먼저 읽고, [템플릿](../templates/)을 복사해 시작한다.

## 1. 디렉토리 구조 (기능 단위 + `_shared`)
```
docs/
  README.md                # 전체 인덱스(도메인 목록)
  _shared/                 # 교차(cross-cutting)·공통·메타 문서
    guide/                 # 이 가이드 등 규칙 문서
    templates/             # 복사용 양식
    issues/                # 여러 도메인에 걸친 이슈
    troubleshooting/       # 공통/인프라 트러블슈팅
    reports/               # 교차 보고서(예: 문서 전략 노트)
    adr/                   # (선택) 팀 의사결정 기록
  <domain>/                # 기능(도메인) 단위. 코드 패키지명과 맞춤
    README.md              # 도메인 문서 목록(인덱스)
    proposals/             # type: proposal
    handoff/               # 인계 / 재개 가이드
    backlog/               # 남은 작업 백로그(시간순)
    reports/               # type: report (구현·정책·시나리오 등)
    troubleshooting/       # 그 도메인 한정 트러블슈팅
```
- **도메인 디렉토리**는 백엔드 패키지명과 일치시킨다: `ai-chatbot`, `house`, `member`, `publicdata`, `common`.
- **소속 판단**: 한 도메인에만 관계되면 그 도메인 폴더, 두 개 이상/인프라/빌드 전반이면 `_shared`.
  - 예) AI 챗봇 구현/제안 → `ai-chatbot/`. 시드 인코딩 버그·Boot 버전업·로컬 환경 → `_shared/`.

## 2. 문서 종류: 기록형 vs 리빙
| 구분 | 예 | 파일명 | 변경 방식 |
|---|---|---|---|
| **기록형(시간순)** | proposal, report, issue, troubleshooting | **날짜 프리픽스** `YYYY-MM-DD-<slug>.md` | 사실 기록. 사후 수정보다 새 문서 추가 |
| **리빙(reference)** | README, 이 guide, templates | **고정명**(날짜 없음) | 상시 갱신, frontmatter `updated` 갱신 |
| **의사결정(ADR)** | 기술 선택 | **시퀀스** `adr/NNNN-<title>.md` | 불변, 바뀌면 새 ADR로 `superseded` |

- 같은 날 같은 종류가 여러 개면: `YYYY-MM-DD-NN-<slug>.md` (NN=01,02…).
- `slug`은 영문 소문자-하이픈 권장(예: `seed-data-double-encoding`).

## 3. Frontmatter (모든 문서 상단 필수)
```yaml
---
title: <문서 제목>
domain: <ai-chatbot | house | member | publicdata | common | _shared>
type: <proposal | report | issue | troubleshooting | adr | guide | template | index>
status: <draft | open | in-progress | done | resolved | superseded | living | template>
author: <작성자>
created: YYYY-MM-DD
updated: YYYY-MM-DD
related:                  # (선택) 상대경로 링크
  - ../_shared/issues/2026-06-21-xxx.md
---
```
- `status` 권장 매핑: 제안/보고=`done`, 이슈=`open`/`resolved`, 트러블슈팅=`resolved`, 리빙=`living`, 템플릿=`template`, ADR=`accepted`/`superseded`.

## 4. 새 문서 추가 절차
1. 종류 결정 → 소속(도메인 vs `_shared`) 결정.
2. [템플릿](../templates/)에서 맞는 양식 복사.
3. 파일명 규칙 적용(기록형=날짜 프리픽스) + **type별 폴더 배치**(proposal→`proposals/`, handoff→`handoff/`, backlog→`backlog/`, report→`reports/`, troubleshooting→`troubleshooting/`).
4. frontmatter 채우기(`created`=오늘, `updated`=오늘).
5. 관련 문서와 **상호 링크**(상대경로) 연결.
6. 해당 도메인 `README.md` 인덱스에 한 줄 추가(날짜 내림차순).

## 5. 새 도메인 추가
- `docs/<domain>/README.md` 생성(아래 인덱스 형식), 코드 패키지명과 일치.
- 최상위 `docs/README.md`의 도메인 목록에 추가.

## 6. 링크 규칙
- 모든 내부 링크는 **상대경로**. 파일 이동 시 링크 갱신 필수.
- 도메인↔`_shared` 교차 링크 예: `../_shared/issues/...`, `../../ai-chatbot/...`.
