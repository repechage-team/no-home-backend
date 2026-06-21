package com.ssafy.home.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring AI 챗봇 기반 설정.
 * <p>
 * Spring AI 자동 구성이 제공하는 {@link ChatClient.Builder}(OpenAI/GMS 프록시 모델)를 받아
 * {@link ChatClient} 빈을 노출한다. 진단 advisor는 명시적으로 활성화한 경우에만 원문을 제외한
 * 메타데이터를 기록한다. 모델/키 설정은 application.properties의 {@code spring.ai.openai.*}
 * 값(환경변수 {@code SSAFY_GMS_API_KEY})에서 주입된다.
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(
            ChatClient.Builder builder,
            @Value("${ai.chat.logging.diagnostics-enabled:false}") boolean diagnosticsEnabled
    ) {
        if (diagnosticsEnabled) {
            builder.defaultAdvisors(new SimpleLoggerAdvisor(
                    AiConfig::summarizeRequest,
                    AiConfig::summarizeResponse,
                    Ordered.HIGHEST_PRECEDENCE));
        }
        return builder.build();
    }

    /**
     * 프롬프트 원문과 advisor context를 제외하고 메시지 개수만 기록한다.
     */
    static String summarizeRequest(ChatClientRequest request) {
        int messageCount = request == null || request.prompt() == null
                ? 0
                : request.prompt().getInstructions().size();
        return "messages=%d".formatted(messageCount);
    }

    /**
     * 모델 응답 원문을 제외하고 결과 개수와 공급자 token 사용량만 기록한다.
     */
    static String summarizeResponse(ChatResponse response) {
        if (response == null) {
            return "results=0, toolCalls=false, promptTokens=unknown, completionTokens=unknown, totalTokens=unknown";
        }

        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return "results=%d, toolCalls=%s, promptTokens=%s, completionTokens=%s, totalTokens=%s"
                .formatted(
                        response.getResults() == null ? 0 : response.getResults().size(),
                        response.hasToolCalls(),
                        tokenCount(usage == null ? null : usage.getPromptTokens()),
                        tokenCount(usage == null ? null : usage.getCompletionTokens()),
                        tokenCount(usage == null ? null : usage.getTotalTokens()));
    }

    private static String tokenCount(Integer count) {
        return count == null ? "unknown" : count.toString();
    }
}
