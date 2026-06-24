package com.ssafy.home.ai.support;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderErrorsTest {

    @Test
    void detectsTimeoutAcrossExceptionChain() {
        assertThat(AiProviderErrors.isTimeout(new TimeoutException())).isTrue();
        assertThat(AiProviderErrors.isTimeout(new SocketTimeoutException())).isTrue();
        assertThat(AiProviderErrors.isTimeout(new RuntimeException(new HttpTimeoutException("slow")))).isTrue();
        assertThat(AiProviderErrors.isTimeout(new RuntimeException("nope"))).isFalse();
        assertThat(AiProviderErrors.isTimeout(null)).isFalse();
    }

    @Test
    void detectsAuthFailureFromStatusOrMessage() {
        assertThat(AiProviderErrors.isAuthFailure(
                new HttpClientErrorException(HttpStatus.UNAUTHORIZED))).isTrue();
        assertThat(AiProviderErrors.isAuthFailure(
                new HttpClientErrorException(HttpStatus.FORBIDDEN))).isTrue();
        assertThat(AiProviderErrors.isAuthFailure(new RuntimeException("invalid_api_key"))).isTrue();
        assertThat(AiProviderErrors.isAuthFailure(new RuntimeException("HTTP 401 from provider"))).isTrue();
        assertThat(AiProviderErrors.isAuthFailure(new RuntimeException("expired token"))).isTrue();
        assertThat(AiProviderErrors.isAuthFailure(new RuntimeException("rate limited"))).isFalse();
        assertThat(AiProviderErrors.isAuthFailure(null)).isFalse();
    }
}
