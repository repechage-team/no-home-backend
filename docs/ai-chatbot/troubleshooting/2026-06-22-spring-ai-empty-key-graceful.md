---
title: "Spring AI — 빈/무효 OpenAI(GMS) 키로 인한 기동 실패와 graceful 비활성화"
domain: ai-chatbot
type: troubleshooting
status: resolved
author: 이정헌
created: 2026-06-22
updated: 2026-06-22
related:
  - ../../_shared/issues/2026-06-21-prod-jwt-fail-closed.md
---

# 트러블슈팅: 빈/무효 GMS 키로 인한 기동 실패와 graceful 비활성화

## 증상
`SSAFY_GMS_API_KEY`(= `spring.ai.openai.api-key`)가 비어 있으면 **백엔드 컨텍스트 자체가 기동 실패**한다(앱 전체 다운, 부동산 조회·회원 등 비-AI 기능까지). 예외 체인:
```
aiChatController → chatClient(AiConfig) → chatClientBuilder → openAiChatModel → openAiApi
  Failed to instantiate OpenAiApi: "OpenAI API key must be set."
```
(테스트가 `spring.ai.openai.api-key=context-test-key` 더미 키를 주입해야 컨텍스트가 뜨던 정황으로 확인.)

## 진단 / 함정 (시행착오로 확정)
1. **`spring.ai.model.chat=none`만으로는 부족.** OpenAI starter는 chat 외에 **embedding/image/audio.speech/audio.transcription/moderation** 자동구성도 각각 `OpenAiApi`를 만들며 키를 요구한다. chat만 끄면 다음으로 `openAiAudioSpeechModel`이 같은 이유로 실패한다. → **모든 모델 타입을 `none`** 으로 꺼야 한다.
2. **EnvironmentPostProcessor 등록·순서.** EPP는 `META-INF/spring/...EnvironmentPostProcessor.imports`가 아니라 **`META-INF/spring.factories`** (`org.springframework.boot.env.EnvironmentPostProcessor=...`)로 등록해야 동작했다. 또 `Ordered.LOWEST_PRECEDENCE`로 두어 **ConfigData(application.properties) 로드 이후 실행**되게 해야 resolved 키를 읽는다(이르게 실행되면 키를 못 읽어 항상 비활성).

## 해결 (graceful degradation)
키가 비면 앱은 정상 기동하고 `/api/ai/chat`만 친화 503을 반환하도록:
- `AiKeyEnvironmentPostProcessor`: 키가 비면 `spring.ai.model.{chat,embedding,image,audio.speech,audio.transcription,moderation}=none` + `app.ai.chat.available=false` 주입(+ 기동 WARN 로그).
- `AiConfig.chatClient`: `@ConditionalOnProperty(name="app.ai.chat.available", havingValue="true", matchIfMissing=true)` → 키 없으면 빈 미생성.
- `AiChatController`: `@Nullable ChatClient` → null이면 503(친화 메시지). **무효/만료 키는 startup이 아니라 런타임 401/403** 으로 오므로, 호출 catch에서 인증 실패로 분류해 친화 503 + WARN(secret·원문 미노출).

## 검증
- `AiChatDisabledWhenKeyMissingTest`: 빈 키로 컨텍스트 로드 성공 + `ChatClient` 빈 부재 확인.
- 컨트롤러 단위 테스트: 비활성 503 / 인증오류 503(메시지에 secret 미포함). 전체 백엔드 87건 통과.
- 구현/PR: [no-home-backend#7](https://github.com/repechage-team/no-home-backend/pull/7).

## 교훈
- 부가기능(AI)의 외부 키가 **핵심 가용성을 좌우하지 않게** graceful 비활성화한다(↔ 운영 보안값은 fail-closed가 맞음: [JWT 이슈](../../_shared/issues/2026-06-21-prod-jwt-fail-closed.md) 대비).
- Spring AI 모델 자동구성은 **모델 타입별로 키를 요구**하므로, 끌 때도 타입별로 모두 꺼야 한다.
- EPP는 `spring.factories` 등록 + 순서(Ordered)로 "설정 로드 후"를 보장하라.
