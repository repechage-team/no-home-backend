package com.ssafy.home.ai.support;

import com.ssafy.home.member.auth.AuthenticatedMember;
import jakarta.servlet.http.HttpServletRequest;

/**
 * AI 컨트롤러 공통 요청 헬퍼 — 인증 회원 추출과 대화기억 키 합성.
 * <p>
 * 질문/에이전트/어시스턴트 컨트롤러가 동일 규칙을 쓰도록 추출했다.
 */
public final class AiRequests {

    /** conversationId 최대 길이(과도한 키 방지). */
    private static final int MAX_CONVERSATION_ID = 64;

    private AiRequests() {
    }

    /** 인터셉터가 심어둔 인증 회원 id를 꺼낸다. 미인증이면 null. */
    public static Long currentMemberId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        Object value = request.getAttribute(AuthenticatedMember.REQUEST_ATTRIBUTE);
        return value instanceof Long memberId ? memberId : null;
    }

    /**
     * 대화기억 키. memberId 네임스페이스(사용자 격리) + 프론트 세션 conversationId(세션 분리, 누락 시 'default').
     * 휘발성 저장소라 세션 종료 시 새 conversationId가 되어 초기화된다.
     */
    public static String resolveConversationId(String clientConversationId, Long memberId) {
        String base = (clientConversationId == null || clientConversationId.isBlank())
                ? "default"
                : clientConversationId.trim();
        if (base.length() > MAX_CONVERSATION_ID) {
            base = base.substring(0, MAX_CONVERSATION_ID);
        }
        return memberId + ":" + base;
    }
}
