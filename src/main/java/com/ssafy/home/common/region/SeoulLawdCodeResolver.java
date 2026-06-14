package com.ssafy.home.common.region;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class SeoulLawdCodeResolver {

    public static final String SEOUL_SIDO_NAME = "\uC11C\uC6B8\uD2B9\uBCC4\uC2DC";

    private static final Map<String, String> LAWD_CD_BY_SIGUNGU = Map.ofEntries(
            Map.entry("\uC885\uB85C\uAD6C", "11110"),
            Map.entry("\uC911\uAD6C", "11140"),
            Map.entry("\uC6A9\uC0B0\uAD6C", "11170"),
            Map.entry("\uC131\uB3D9\uAD6C", "11200"),
            Map.entry("\uAD11\uC9C4\uAD6C", "11215"),
            Map.entry("\uB3D9\uB300\uBB38\uAD6C", "11230"),
            Map.entry("\uC911\uB791\uAD6C", "11260"),
            Map.entry("\uC131\uBD81\uAD6C", "11290"),
            Map.entry("\uAC15\uBD81\uAD6C", "11305"),
            Map.entry("\uB3C4\uBD09\uAD6C", "11320"),
            Map.entry("\uB178\uC6D0\uAD6C", "11350"),
            Map.entry("\uC740\uD3C9\uAD6C", "11380"),
            Map.entry("\uC11C\uB300\uBB38\uAD6C", "11410"),
            Map.entry("\uB9C8\uD3EC\uAD6C", "11440"),
            Map.entry("\uC591\uCC9C\uAD6C", "11470"),
            Map.entry("\uAC15\uC11C\uAD6C", "11500"),
            Map.entry("\uAD6C\uB85C\uAD6C", "11530"),
            Map.entry("\uAE08\uCC9C\uAD6C", "11545"),
            Map.entry("\uC601\uB4F1\uD3EC\uAD6C", "11560"),
            Map.entry("\uB3D9\uC791\uAD6C", "11590"),
            Map.entry("\uAD00\uC545\uAD6C", "11620"),
            Map.entry("\uC11C\uCD08\uAD6C", "11650"),
            Map.entry("\uAC15\uB0A8\uAD6C", "11680"),
            Map.entry("\uC1A1\uD30C\uAD6C", "11710"),
            Map.entry("\uAC15\uB3D9\uAD6C", "11740")
    );

    private static final Map<String, String> SIGUNGU_BY_LAWD_CD = LAWD_CD_BY_SIGUNGU.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

    public List<String> resolveLawdCds(String lawdCd, String sido, String sigungu) {
        String normalizedLawdCd = trimToNull(lawdCd);
        if (normalizedLawdCd != null) {
            return List.of(normalizedLawdCd);
        }

        if (!isSeoul(sido)) {
            return List.of();
        }

        String normalizedSigungu = trimToNull(sigungu);
        if (normalizedSigungu == null) {
            return List.copyOf(LAWD_CD_BY_SIGUNGU.values());
        }

        return Optional.ofNullable(LAWD_CD_BY_SIGUNGU.get(normalizedSigungu))
                .map(List::of)
                .orElse(List.of());
    }

    public Optional<String> sigunguName(String lawdCd) {
        return Optional.ofNullable(SIGUNGU_BY_LAWD_CD.get(trimToNull(lawdCd)));
    }

    public int seoulSigunguCount() {
        return LAWD_CD_BY_SIGUNGU.size();
    }

    private static boolean isSeoul(String sido) {
        String normalized = trimToNull(sido);
        if (normalized == null) {
            return false;
        }
        String compact = normalized.replace(" ", "").toLowerCase(Locale.ROOT);
        return SEOUL_SIDO_NAME.equals(normalized)
                || "\uC11C\uC6B8".equals(normalized)
                || "seoul".equals(compact);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
