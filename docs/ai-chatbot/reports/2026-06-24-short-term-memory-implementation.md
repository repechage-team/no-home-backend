---
title: 단기 대화기억(Phase 5) 구현 & 저장소 선택 근거
domain: ai-chatbot
type: report
status: done
author: 이정헌
created: 2026-06-24
updated: 2026-06-24
related:
  - 2026-06-24-tool-calling-assistant-implementation.md
  - ../handoff/2026-06-23-handoff.md
  - 2026-06-21-ai-logging-privacy-policy.md
  - ../backlog/2026-06-24-backlog.md
---

# 단기 대화기억(Phase 5) 구현 & 저장소 선택 근거

AI 챗봇에 **멀티턴 맥락**을 부여하는 단기 대화기억을 **세션 휘발(InMemory)** 방식으로 구현했다(backend [#28](https://github.com/repechage-team/no-home-backend/pull/28) / frontend [#12](https://github.com/repechage-team/no-home-frontend/pull/12)). 이 문서는 **구현 구조**와, 여러 저장 후보(InMemory/localStorage/RDB/Redis) 중 InMemory를 택한 **근거·트레이드오프**를 남긴다.

## 1. 구현 요약
- 중앙 `ChatClient` 빈에 `MessageWindowChatMemory`(**InMemory**, `maxMessages=10`, SystemMessage 보존) + `MessageChatMemoryAdvisor`를 `defaultAdvisors`로 부착 → `/chat`·`/agent`(현재는 단일 `/assistant`) 모든 호출에 자동 적용. ([AiConfig.java](../../../src/main/java/com/ssafy/home/ai/config/AiConfig.java))
- 대화 키 `conversationId = memberId:<프론트 세션 UUID>` — memberId로 **사용자 격리**, UUID로 **세션 분리**(누락 시 `default`). ([AiRequests.resolveConversationId](../../../src/main/java/com/ssafy/home/ai/support/AiRequests.java))
- 프론트는 `getConversationId()`로 sessionStorage UUID를 생성·공유. ([chatClient.js](../../../../no-home-frontend/src/chat/chatClient.js))
- `MessageChatMemoryAdvisor`가 매 호출에 conversationId별 최근 대화를 모델 입력에 주입/갱신.

## 2. 저장 레이어 (컨텍스트가 실제로 사는 곳)

```
[브라우저]                     [HTTP]               [백엔드 JVM 힙]                 [DB]
sessionStorage           ── 요청 body ──►   InMemoryChatMemoryRepository          ✗ 미저장
no-home.ai.conversation-id                 (Map<conversationId, List<Message>>,
= UUID (키만, 내용 X)                        ConcurrentHashMap) — 대화 원문 최근 10개
```

- **실체는 백엔드 JVM 힙**(InMemory)에만 있다. 브라우저엔 그것을 가리키는 **세션 UUID(키)** 만 있고 대화 내용은 없다.
- DB(MySQL)에 질문/답변 원문을 **영속하지 않는다** → 개인정보 보관 부담 없음([로깅·프라이버시 정책](2026-06-21-ai-logging-privacy-policy.md)과 일관).

## 3. 저장소 선택 — 기술스택 비교 & 트레이드오프

초기에는 영속(JDBC) 방식도 검토했으나, "브라우저 챗을 닫으면 초기화되어야 안정적"이라는 요구와 원문 비영속(프라이버시) 원칙에 따라 InMemory로 결정했다. 4개 후보의 비교는 다음과 같다.

| 기준 | **InMemory(채택)** | 브라우저 localStorage | RDB(MySQL/JDBC) | Redis |
|---|---|---|---|---|
| 대화 원문 위치 | 서버 메모리 | 클라이언트 | 서버 디스크(영속) | 서버 메모리(+옵션 영속) |
| 영속성 | ✗ 재기동 시 소멸 | △ 브라우저별 영구 | ✓ 영구 | ✓ TTL/스냅샷 |
| 다중 인스턴스(스케일아웃) | ✗ 인스턴스별 분리 | ✓(서버 무상태) | ✓ 공유 | ✓ 공유 |
| 프라이버시(원문 보관) | **낮음(휘발)** | 클라 보관 | **높음(영속 책임)** | 중(TTL 완화) |
| 지연/성능 | **최상**(힙) | 네트워크 전송↑ | 디스크 I/O | 메모리 + 1홉 |
| 운영 복잡도/비용 | **없음** | 없음(서버) | 스키마·정리 배치 | 인프라 추가 |
| 재기동 생존 | ✗ | ✓ | ✓ | ✓(설정) |

### 후보별 장단점
- **InMemory(채택)** — 의존성 0·최속·**원문 영속 없음(프라이버시✓)**, "닫으면 초기화·재기동 시 소멸" 요구에 정확히 부합. 단 **단일 인스턴스 한정**, 재기동 시 진행 중 대화 유실(→ window 10 + 휘발로 관리).
- **localStorage(대화 원문을 클라에 보관 시)** — 서버 완전 무상태. 그러나 **매 요청에 전체 히스토리 전송 → 토큰·대역폭 비용↑**, 클라 변조/인젝션 표면↑·XSS 노출, 기기 간 동기화 불가. → 대화 원문 보관처로 **부적합**(현재는 conversationId 키와 UI 선호[창 크기]에만 sessionStorage/localStorage 사용).
- **RDB(JDBC)** — 영구 보존·감사·교차기기 복원. 그러나 **질문/답변 원문 영속 = 개인정보 보관 책임**(보존기간·삭제·암호화 정책 필요), 쓰기 I/O, 정리 배치 필요. → 본 프로젝트 프라이버시 정책과 **충돌해 기각**.
- **Redis** — 메모리 속도 + 다중 인스턴스 공유 + **TTL 자동 만료**(휘발/프라이버시·정리 동시 해결) + 재기동 생존. 그러나 **인프라 1개 추가**(운영·장애점). 단일 인스턴스엔 과투자.

### 결론
현 요구 = **단일 backend 컨테이너 + "닫으면 초기화" + 원문 비영속(프라이버시)**. 이 조합에선 **InMemory가 최적**이다(추가 인프라 0, 프라이버시 부담 최저).

**전환 시점**: 스케일아웃(backend 2+ 인스턴스) 또는 재기동에도 대화 유지가 필요해지면 → **Redis + TTL**(예 30분~24h)이 휘발의 장점(자동 정리·프라이버시)을 유지하면서 공유·생존을 얻는 균형점. 영구 보존·감사가 *제품 요구*가 되면 → RDB(단 보존·삭제·암호화 정책 동반).

**전환 비용은 낮다**: Spring AI의 `ChatMemoryRepository` 추상화 덕에 `InMemoryChatMemoryRepository` → `RedisChatMemoryRepository`/`JdbcChatMemoryRepository`로 [AiConfig.java](../../../src/main/java/com/ssafy/home/ai/config/AiConfig.java)의 빈 한 곳만 교체하면 되고, 컨트롤러·프론트(conversationId 전달)는 무변경.

## 4. 검증
- **멀티턴 유지**: 같은 세션에서 "마포구 시세" → "방금 그 지역?" → "마포구"로 맥락 응답.
- **세션 격리**: 다른 conversationId로 물으면 "물어본 적 없음"(컨텍스트 분리).
- **세션 종료 초기화**: sessionStorage UUID 소멸 → 새 세션 새 UUID → 옛 대화 도달 불가.
- **재기동 휘발**: 백엔드 재기동 시 힙 소멸로 전체 초기화.
- 단위/통합 테스트 그린(BE·FE), 브라우저 실측.
- 재설계(`/assistant`)에도 같은 빈으로 **자동 승계** — [재설계 구현 리포트](2026-06-24-tool-calling-assistant-implementation.md) 참조.
