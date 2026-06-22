package com.ssafy.home.ai.limit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AiChatRateLimiterTest {

    @Test
    void rejectsRequestsBeyondTheConfiguredWindowLimit() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        AiChatRateLimiter limiter = new AiChatRateLimiter(2, Duration.ofMinutes(1), clock);

        assertThat(limiter.acquire(1L).allowed()).isTrue();
        limiter.release(1L);
        assertThat(limiter.acquire(1L).allowed()).isTrue();
        limiter.release(1L);

        AiChatRateLimiter.Decision rejected = limiter.acquire(1L);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.reason()).isEqualTo(AiChatRateLimiter.RejectionReason.RATE_LIMIT);
        assertThat(rejected.retryAfterSeconds()).isEqualTo(30);
    }

    @Test
    void resetsLimitAfterWindowExpires() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        AiChatRateLimiter limiter = new AiChatRateLimiter(1, Duration.ofSeconds(10), clock);

        assertThat(limiter.acquire(1L).allowed()).isTrue();
        limiter.release(1L);
        assertThat(limiter.acquire(1L).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(10));

        assertThat(limiter.acquire(1L).allowed()).isTrue();
    }

    @Test
    void tracksMembersIndependently() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        AiChatRateLimiter limiter = new AiChatRateLimiter(1, Duration.ofMinutes(1), clock);

        assertThat(limiter.acquire(1L).allowed()).isTrue();
        limiter.release(1L);
        assertThat(limiter.acquire(1L).allowed()).isFalse();
        assertThat(limiter.acquire(2L).allowed()).isTrue();
    }

    @Test
    void bypassesAllLimitsWhenDisabled() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        AiChatRateLimiter limiter = new AiChatRateLimiter(false, 1, Duration.ofMinutes(1), clock);

        // release 없이 반복 호출해도 분당 한도(1개)·동시요청 제한에 걸리지 않는다.
        for (int i = 0; i < 5; i++) {
            AiChatRateLimiter.Decision decision = limiter.acquire(1L);
            assertThat(decision.allowed()).isTrue();
            assertThat(decision.reason()).isEqualTo(AiChatRateLimiter.RejectionReason.NONE);
        }

        // memberId가 없어도 비활성화 상태에서는 허용한다.
        assertThat(limiter.acquire(null).allowed()).isTrue();
    }

    @Test
    void rejectsConcurrentRequestWithoutConsumingAnotherToken() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-21T00:00:00Z"));
        AiChatRateLimiter limiter = new AiChatRateLimiter(2, Duration.ofMinutes(1), clock);

        assertThat(limiter.acquire(1L).allowed()).isTrue();

        AiChatRateLimiter.Decision concurrent = limiter.acquire(1L);
        assertThat(concurrent.allowed()).isFalse();
        assertThat(concurrent.reason()).isEqualTo(AiChatRateLimiter.RejectionReason.CONCURRENT_REQUEST);

        limiter.release(1L);
        assertThat(limiter.acquire(1L).allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
