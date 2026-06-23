package com.ssafy.home.house.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ApiRowHashGenerator {

    private ApiRowHashGenerator() {
    }

    public static String generate(ApiRowHashInput input) {
        String canonical = input.monthlyRent() == null ? String.join("|",
                normalize(input.sourceApi()),
                normalize(input.lawdCd()),
                normalize(input.dealYmd()),
                normalize(input.umdNm()),
                normalize(input.jibun()),
                normalize(input.aptNm()),
                normalize(input.dealYear()),
                normalize(input.dealMonth()),
                normalize(input.dealDay()),
                normalizeAmount(input.dealAmount()),
                normalize(input.excluUseAr()),
                normalize(input.floor())
        ) : String.join("|",
                normalize(input.sourceApi()),
                normalize(input.lawdCd()),
                normalize(input.dealYmd()),
                normalize(input.umdNm()),
                normalize(input.jibun()),
                normalize(input.aptNm()),
                normalize(input.dealYear()),
                normalize(input.dealMonth()),
                normalize(input.dealDay()),
                normalizeAmount(input.dealAmount()),
                normalizeAmount(input.monthlyRent()),
                normalize(input.excluUseAr()),
                normalize(input.floor())
        );
        return sha256Hex(canonical);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeAmount(String value) {
        return normalize(value).replace(",", "");
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
