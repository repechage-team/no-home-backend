package com.ssafy.home.ai.config;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConfigTest {

    private final AiConfig aiConfig = new AiConfig();

    @Test
    void keepsDiagnosticAdvisorDisabledByDefault() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        ChatClient result = aiConfig.chatClient(builder, chatMemory, false);

        assertThat(result).isSameAs(chatClient);
        // 대화기억 advisor는 항상 부착, 진단 advisor는 기본 비활성.
        org.mockito.ArgumentCaptor<Advisor[]> captor = org.mockito.ArgumentCaptor.forClass(Advisor[].class);
        verify(builder).defaultAdvisors(captor.capture());
        assertThat(captor.getValue()).noneMatch(advisor -> advisor instanceof SimpleLoggerAdvisor);
    }

    @Test
    void registersSanitizedDiagnosticAdvisorOnlyWhenExplicitlyEnabled() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(builder.defaultAdvisors(any(Advisor[].class))).thenReturn(builder);
        when(builder.build()).thenReturn(chatClient);

        ChatClient result = aiConfig.chatClient(builder, chatMemory, true);

        assertThat(result).isSameAs(chatClient);
        org.mockito.ArgumentCaptor<Advisor[]> captor = org.mockito.ArgumentCaptor.forClass(Advisor[].class);
        verify(builder).defaultAdvisors(captor.capture());
        // 진단 활성화 시 SimpleLoggerAdvisor가 함께 부착된다(대화기억 advisor와 공존).
        assertThat(captor.getValue()).anyMatch(advisor -> advisor instanceof SimpleLoggerAdvisor);
    }

    @Test
    void diagnosticSummariesExcludePromptResponseAndContextContents() {
        String secretPrompt = "내 이메일 user@example.com과 비밀 질문";
        ChatClientRequest request = new ChatClientRequest(
                new Prompt(secretPrompt),
                Map.of("private-context", secretPrompt));

        Generation generation = mock(Generation.class);
        ChatResponse response = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(response.getResults()).thenReturn(List.of(generation));
        when(response.hasToolCalls()).thenReturn(true);
        when(response.getMetadata()).thenReturn(metadata);
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(12);
        when(usage.getCompletionTokens()).thenReturn(8);
        when(usage.getTotalTokens()).thenReturn(20);

        String requestSummary = AiConfig.summarizeRequest(request);
        String responseSummary = AiConfig.summarizeResponse(response);

        assertThat(requestSummary)
                .isEqualTo("messages=1")
                .doesNotContain(secretPrompt, "user@example.com", "private-context");
        assertThat(responseSummary)
                .isEqualTo("results=1, toolCalls=true, promptTokens=12, completionTokens=8, totalTokens=20")
                .doesNotContain(secretPrompt, "user@example.com");
    }
}
