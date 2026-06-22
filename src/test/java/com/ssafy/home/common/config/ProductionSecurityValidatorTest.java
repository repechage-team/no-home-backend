package com.ssafy.home.common.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionSecurityValidatorTest {

    // application.properties의 개발용 기본 secret (local-development + change-me 포함).
    private static final String DEV_DEFAULT = "no-home-local-development-jwt-secret-change-me-32bytes";
    // 개발 마커가 없고 32자 이상인 강한 secret.
    private static final String STRONG_SECRET = "f3a9c1e7b52d4486a0c9d2e1f6b8740c5a1e9d3b2c4f6088";

    @Test
    void rejectsDevelopmentDefaultSecret() {
        assertThatThrownBy(() -> ProductionSecurityValidator.validate(DEV_DEFAULT, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.jwt.secret")
                // 예외 메시지에 secret 값이 노출되면 안 된다.
                .matches(e -> !e.getMessage().contains(DEV_DEFAULT), "message must not contain the secret value");
    }

    @Test
    void rejectsNonSecureCookie() {
        assertThatThrownBy(() -> ProductionSecurityValidator.validate(STRONG_SECRET, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cookie-secure");
    }

    @Test
    void passesWithStrongSecretAndSecureCookie() {
        assertThatCode(() -> ProductionSecurityValidator.validate(STRONG_SECRET, true))
                .doesNotThrowAnyException();
    }

    @Test
    void reportsBothViolationsWhenSecretWeakAndCookieInsecure() {
        assertThatThrownBy(() -> ProductionSecurityValidator.validate(DEV_DEFAULT, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.jwt.secret")
                .hasMessageContaining("cookie-secure");
    }
}
