package com.ssafy.home.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.Map;

/**
 * SSAFY_GMS_API_KEY(=spring.ai.openai.api-key) 미설정 시 AI 챗봇 자동구성을 비활성화해
 * 애플리케이션이 정상 기동되도록 한다.
 * <p>
 * 키가 비어 있으면 Spring AI OpenAI 자동구성이 "OpenAI API key must be set"으로 컨텍스트
 * 기동을 실패시킨다. 이때 {@code spring.ai.model.chat=none}으로 chat 모델 자동구성을 끄고
 * {@code app.ai.chat.available=false} 게이트를 내려, 부동산 등 핵심 기능은 정상 기동하고
 * {@code /api/ai/chat}만 비활성(503)이 되도록 한다. 키가 있으면 아무 것도 하지 않는다.
 */
public class AiKeyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(AiKeyEnvironmentPostProcessor.class);

    // application.properties(ConfigData) 로드 이후 실행되어야 resolved api-key를 읽을 수 있다.
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String key = environment.getProperty("spring.ai.openai.api-key");
        if (key != null && !key.isBlank()) {
            return; // 키 있음 → 정상 경로(자동구성 그대로)
        }
        // OpenAI starter는 chat 외에도 embedding/image/audio/moderation 자동구성이 각각 키를 요구하므로
        // 모든 모델 타입을 none으로 꺼야 빈 키로도 컨텍스트가 로드된다.
        Map<String, Object> overrides = Map.ofEntries(
                Map.entry("spring.ai.model.chat", "none"),
                Map.entry("spring.ai.model.embedding", "none"),
                Map.entry("spring.ai.model.image", "none"),
                Map.entry("spring.ai.model.audio.speech", "none"),
                Map.entry("spring.ai.model.audio.transcription", "none"),
                Map.entry("spring.ai.model.moderation", "none"),
                Map.entry("app.ai.chat.available", "false")  // ChatClient 빈/컨트롤러 게이트
        );
        environment.getPropertySources()
                .addFirst(new MapPropertySource("aiChatDisabledWhenKeyMissing", overrides));
        log.warn("SSAFY_GMS_API_KEY (spring.ai.openai.api-key) 미설정 — AI 챗봇을 비활성화하고 기동합니다. "
                + "핵심 기능은 정상이며 /api/ai/chat 은 503을 반환합니다.");
    }
}
