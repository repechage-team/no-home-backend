package com.ssafy.home.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SSAFY_GMS_API_KEY(spring.ai.openai.api-key) 미설정 시에도 앱이 정상 기동되는지 검증한다.
 * AiKeyEnvironmentPostProcessor가 OpenAI 모델 자동구성을 비활성화하므로 ChatClient 빈은 없고
 * (= /api/ai/assistant 는 503), 컨트롤러를 포함한 나머지 컨텍스트는 정상 로드된다.
 */
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.url=jdbc:h2:mem:ai_disabled;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=never",
        "spring.ai.openai.api-key="
})
class AiChatDisabledWhenKeyMissingTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsAndAiChatIsDisabled() {
        // 키가 없으면 ChatClient 빈은 생성되지 않는다(챗봇 비활성).
        assertThat(context.getBeanNamesForType(ChatClient.class)).isEmpty();
        // 그러나 컨트롤러는 존재해 요청을 받아 503으로 안내한다(앱은 정상 기동).
        assertThat(context.containsBean("aiAssistantController")).isTrue();
    }
}
