package com.ssafy.home.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 운영(prod) 프로필에서 보안 설정을 fail-closed로 검증한다.
 * <p>
 * 개발용 기본 secret(예: {@code local-development}/{@code change-me} 포함)이나 너무 짧은 secret,
 * 또는 비-Secure 쿠키 설정이면 {@link IllegalStateException}으로 애플리케이션 기동을 차단한다.
 * 예외 메시지에는 secret 값 자체를 포함하지 않고 위반 사유만 남긴다.
 * <p>
 * 기본/로컬 프로필에서는 빈이 생성되지 않으므로(무설정 기동) 영향이 없다.
 */
@Component
@Profile("prod")
public class ProductionSecurityValidator implements InitializingBean {

    private final String jwtSecret;
    private final boolean cookieSecure;

    public ProductionSecurityValidator(
            @Value("${auth.jwt.secret:}") String jwtSecret,
            @Value("${auth.jwt.cookie-secure:false}") boolean cookieSecure
    ) {
        this.jwtSecret = jwtSecret;
        this.cookieSecure = cookieSecure;
    }

    @Override
    public void afterPropertiesSet() {
        validate(jwtSecret, cookieSecure);
    }

    /**
     * 운영 보안값을 검증한다. 위반 시 사유를 모아 예외를 던진다(secret 값은 메시지에 포함하지 않음).
     */
    static void validate(String secret, boolean cookieSecure) {
        List<String> violations = new ArrayList<>();
        if (isWeakSecret(secret)) {
            violations.add("auth.jwt.secret is missing or uses a known development default; set a strong JWT_SECRET (>= 32 chars).");
        }
        if (!cookieSecure) {
            violations.add("auth.jwt.cookie-secure must be true in production; set JWT_COOKIE_SECURE=true.");
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException(
                    "Insecure production security configuration: " + String.join(" ", violations));
        }
    }

    private static boolean isWeakSecret(String secret) {
        if (secret == null || secret.isBlank() || secret.length() < 32) {
            return true;
        }
        String lower = secret.toLowerCase();
        return lower.contains("local-development") || lower.contains("change-me");
    }
}
