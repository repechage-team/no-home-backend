package com.ssafy.home.ai.assistant;

import com.ssafy.home.ai.agent.AgentCommand;

/**
 * 통합 어시스턴트(/api/ai/assistant) 응답. 한 대화에서 LLM이 tool calling으로 분기한 결과를 담는다.
 * <ul>
 *   <li>{@code type="answer"}: 일반 질문/대화 → {@code answer}에 텍스트 답변.</li>
 *   <li>{@code type="command"}: 페이지 조작 의도 → {@code command}에 프론트가 실행할 {@link AgentCommand}.</li>
 * </ul>
 * {@code notice}는 선택적 부가 안내(예: 일부 조건 무시). 분기되지 않은 필드는 null.
 */
public record AssistantResponse(
        String type,
        String answer,
        AgentCommand command,
        String notice
) {

    public static AssistantResponse answer(String text) {
        return new AssistantResponse("answer", text, null, null);
    }

    public static AssistantResponse command(AgentCommand command) {
        return new AssistantResponse("command", null, command, null);
    }
}
