package com.ssafy.home.ai.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiRequestsTest {

    @Test
    void resolveConversationIdNamespacesByMember() {
        assertThat(AiRequests.resolveConversationId("abc", 7L)).isEqualTo("7:abc");
        assertThat(AiRequests.resolveConversationId("  abc  ", 7L)).isEqualTo("7:abc");
    }

    @Test
    void resolveConversationIdFallsBackToDefaultWhenMissing() {
        assertThat(AiRequests.resolveConversationId(null, 7L)).isEqualTo("7:default");
        assertThat(AiRequests.resolveConversationId("   ", 7L)).isEqualTo("7:default");
    }

    @Test
    void resolveConversationIdTruncatesOverlongClientId() {
        String longId = "x".repeat(200);
        String resolved = AiRequests.resolveConversationId(longId, 7L);
        assertThat(resolved).startsWith("7:");
        // memberId 접두 + ':' + 최대 64자
        assertThat(resolved.length()).isLessThanOrEqualTo(("7:").length() + 64);
    }
}
