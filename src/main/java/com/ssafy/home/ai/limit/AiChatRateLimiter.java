package com.ssafy.home.ai.limit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AiChatRateLimiter {

    private final boolean enabled;
    private final int maxRequests;
    private final Duration windowDuration;
    private final Clock clock;
    private final ConcurrentHashMap<Long, RequestWindow> windows = new ConcurrentHashMap<>();

    @Autowired
    public AiChatRateLimiter(
            @Value("${ai.chat.rate-limit.enabled:true}") boolean enabled,
            @Value("${ai.chat.rate-limit.requests:10}") int maxRequests,
            @Value("${ai.chat.rate-limit.window:1m}") Duration windowDuration
    ) {
        this(enabled, maxRequests, windowDuration, Clock.systemUTC());
    }

    AiChatRateLimiter(int maxRequests, Duration windowDuration, Clock clock) {
        this(true, maxRequests, windowDuration, clock);
    }

    AiChatRateLimiter(boolean enabled, int maxRequests, Duration windowDuration, Clock clock) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("AI chat rate limit requests must be positive.");
        }
        if (windowDuration == null || windowDuration.isZero() || windowDuration.isNegative()) {
            throw new IllegalArgumentException("AI chat rate limit window must be positive.");
        }
        this.enabled = enabled;
        this.maxRequests = maxRequests;
        this.windowDuration = windowDuration;
        this.clock = clock;
    }

    public Decision acquire(Long memberId) {
        if (!enabled) {
            return Decision.granted();
        }
        if (memberId == null) {
            return Decision.rateLimited(1);
        }

        Instant now = clock.instant();
        AtomicReference<Decision> decision = new AtomicReference<>();
        windows.compute(memberId, (key, current) -> {
            RequestWindow updated = refill(current, now);
            if (updated.inFlight()) {
                decision.set(Decision.concurrentRequest());
                return updated;
            }
            if (updated.tokens() < 1.0) {
                decision.set(Decision.rateLimited(retryAfterSeconds(updated.tokens())));
                return updated;
            }
            decision.set(Decision.granted());
            return new RequestWindow(updated.tokens() - 1.0, now, true);
        });
        return decision.get();
    }

    public void release(Long memberId) {
        if (!enabled || memberId == null) {
            return;
        }
        windows.computeIfPresent(memberId, (key, current) ->
                new RequestWindow(current.tokens(), current.lastRefillAt(), false));
    }

    private RequestWindow refill(RequestWindow current, Instant now) {
        if (current == null) {
            return new RequestWindow(maxRequests, now, false);
        }
        long elapsedNanos = Math.max(0, Duration.between(current.lastRefillAt(), now).toNanos());
        double refillPerNano = (double) maxRequests / windowDuration.toNanos();
        double tokens = Math.min(maxRequests, current.tokens() + elapsedNanos * refillPerNano);
        return new RequestWindow(tokens, now, current.inFlight());
    }

    private long retryAfterSeconds(double availableTokens) {
        double missingTokens = Math.max(0, 1.0 - availableTokens);
        double seconds = missingTokens * windowDuration.toNanos() / maxRequests / 1_000_000_000.0;
        return Math.max(1, (long) Math.ceil(seconds));
    }

    private record RequestWindow(double tokens, Instant lastRefillAt, boolean inFlight) {
    }

    public enum RejectionReason {
        NONE,
        RATE_LIMIT,
        CONCURRENT_REQUEST
    }

    public record Decision(boolean allowed, long retryAfterSeconds, RejectionReason reason) {

        public static Decision granted() {
            return new Decision(true, 0, RejectionReason.NONE);
        }

        public static Decision rateLimited(long retryAfterSeconds) {
            return new Decision(false, Math.max(1, retryAfterSeconds), RejectionReason.RATE_LIMIT);
        }

        public static Decision concurrentRequest() {
            return new Decision(false, 0, RejectionReason.CONCURRENT_REQUEST);
        }
    }
}
