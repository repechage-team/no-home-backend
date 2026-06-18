package com.ssafy.home.member.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.home.member.service.MemberErrorCode;
import com.ssafy.home.member.service.MemberException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final byte[] secret;
    private final long accessTokenSeconds;
    private final long refreshTokenSeconds;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JwtTokenService(
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.access-token-seconds}") long accessTokenSeconds,
            @Value("${auth.jwt.refresh-token-seconds}") long refreshTokenSeconds,
            ObjectMapper objectMapper
    ) {
        this(secret, accessTokenSeconds, refreshTokenSeconds, objectMapper, Clock.systemUTC());
    }

    public JwtTokenService(String secret, long accessTokenSeconds, long refreshTokenSeconds, ObjectMapper objectMapper, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes.");
        }
        if (accessTokenSeconds <= 0 || refreshTokenSeconds <= accessTokenSeconds) {
            throw new IllegalArgumentException("JWT expiration settings are invalid.");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessTokenSeconds = accessTokenSeconds;
        this.refreshTokenSeconds = refreshTokenSeconds;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public JwtTokenPair issue(Long memberId) {
        Instant now = clock.instant();
        Instant accessExpiresAt = now.plusSeconds(accessTokenSeconds);
        Instant refreshExpiresAt = now.plusSeconds(refreshTokenSeconds);
        return new JwtTokenPair(
                create(memberId, JwtTokenType.ACCESS, now, accessExpiresAt),
                accessExpiresAt,
                create(memberId, JwtTokenType.REFRESH, now, refreshExpiresAt),
                refreshExpiresAt
        );
    }

    public JwtClaims verify(String token, JwtTokenType expectedType) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) {
                throw invalidToken();
            }
            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            byte[] actualSignature = BASE64_URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw invalidToken();
            }

            JsonNode claims = objectMapper.readTree(BASE64_URL_DECODER.decode(parts[1]));
            Long memberId = Long.valueOf(requiredText(claims, "sub"));
            JwtTokenType type = JwtTokenType.valueOf(requiredText(claims, "typ"));
            String tokenId = requiredText(claims, "jti");
            Instant expiresAt = Instant.ofEpochSecond(claims.path("exp").asLong(0));
            if (type != expectedType || !expiresAt.isAfter(clock.instant())) {
                throw invalidToken();
            }
            return new JwtClaims(memberId, type, tokenId, expiresAt);
        } catch (MemberException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidToken();
        }
    }

    private String create(Long memberId, JwtTokenType type, Instant issuedAt, Instant expiresAt) {
        try {
            String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", memberId.toString());
            claims.put("typ", type.name());
            claims.put("jti", UUID.randomUUID().toString());
            claims.put("iat", issuedAt.getEpochSecond());
            claims.put("exp", expiresAt.getEpochSecond());
            String payload = encodeJson(claims);
            String unsignedToken = header + "." + payload;
            return unsignedToken + "." + BASE64_URL_ENCODER.encodeToString(sign(unsignedToken));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JWT serialization failed.", exception);
        }
    }

    private String encodeJson(Object value) throws JsonProcessingException {
        return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private byte[] sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT signing failed.", exception);
        }
    }

    private static String requiredText(JsonNode claims, String field) {
        String value = claims.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw invalidToken();
        }
        return value;
    }

    private static MemberException invalidToken() {
        return new MemberException(MemberErrorCode.UNAUTHENTICATED, "authentication token is invalid or expired.");
    }
}
