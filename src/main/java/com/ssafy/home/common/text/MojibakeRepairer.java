package com.ssafy.home.common.text;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class MojibakeRepairer {

    private MojibakeRepairer() {
    }

    public static String repair(String value) {
        if (value == null || value.isBlank() || containsHangul(value) || !looksLikeMojibake(value)) {
            return value;
        }

        byte[] bytes = toOriginalBytes(value);
        if (bytes.length == 0) {
            return value;
        }

        try {
            String decoded = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            return containsHangul(decoded) ? decoded : value;
        } catch (CharacterCodingException exception) {
            return value;
        }
    }

    private static byte[] toOriginalBytes(String value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            int byteValue = toWindows1252Byte(codePoint);
            if (byteValue < 0) {
                return new byte[0];
            }
            bytes.write(byteValue);
            offset += Character.charCount(codePoint);
        }
        return bytes.toByteArray();
    }

    private static int toWindows1252Byte(int codePoint) {
        if (codePoint <= 0xff) {
            return codePoint;
        }
        return switch (codePoint) {
            case 0x20AC -> 0x80;
            case 0x201A -> 0x82;
            case 0x0192 -> 0x83;
            case 0x201E -> 0x84;
            case 0x2026 -> 0x85;
            case 0x2020 -> 0x86;
            case 0x2021 -> 0x87;
            case 0x02C6 -> 0x88;
            case 0x2030 -> 0x89;
            case 0x0160 -> 0x8a;
            case 0x2039 -> 0x8b;
            case 0x0152 -> 0x8c;
            case 0x017D -> 0x8e;
            case 0x2018 -> 0x91;
            case 0x2019 -> 0x92;
            case 0x201C -> 0x93;
            case 0x201D -> 0x94;
            case 0x2022 -> 0x95;
            case 0x2013 -> 0x96;
            case 0x2014 -> 0x97;
            case 0x02DC -> 0x98;
            case 0x2122 -> 0x99;
            case 0x0161 -> 0x9a;
            case 0x203A -> 0x9b;
            case 0x0153 -> 0x9c;
            case 0x017E -> 0x9e;
            case 0x0178 -> 0x9f;
            default -> -1;
        };
    }

    private static boolean looksLikeMojibake(String value) {
        return value.codePoints().anyMatch(codePoint ->
                (codePoint >= 0x80 && codePoint <= 0xff)
                        || toWindows1252Byte(codePoint) >= 0x80
        );
    }

    private static boolean containsHangul(String value) {
        return value.codePoints().anyMatch(codePoint -> codePoint >= 0xac00 && codePoint <= 0xd7a3);
    }
}
