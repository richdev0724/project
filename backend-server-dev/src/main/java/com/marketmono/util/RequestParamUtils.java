package com.marketmono.util;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class RequestParamUtils {

    private RequestParamUtils() {
    }

    public static List<String> parseUpperCsv(String value) {
        return parseCsv(value, token -> token.toUpperCase(Locale.ROOT), true);
    }

    public static List<String> parseLowerCsv(String value) {
        return parseCsv(value, token -> token.toLowerCase(Locale.ROOT), true);
    }

    public static List<String> parseUpperCsvPreservingEmptyTokens(String value) {
        return parseCsv(value, token -> token.toUpperCase(Locale.ROOT), false);
    }

    public static List<String> parseLowerCsvPreservingEmptyTokens(String value) {
        return parseCsv(value, token -> token.toLowerCase(Locale.ROOT), false);
    }

    public static List<LocalDate> parseLocalDateCsv(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        List<LocalDate> parsed = Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(token -> !token.isEmpty())
            .map(RequestParamUtils::tryParseLocalDate)
            .filter(Objects::nonNull)
            .toList();

        return parsed.isEmpty() ? null : parsed;
    }

    public static String normalizeSide(String side) {
        if (side == null || side.isBlank()) {
            return null;
        }

        return side.trim().toUpperCase(Locale.ROOT);
    }

    private static List<String> parseCsv(
        String value,
        UnaryOperator<String> normalizer,
        boolean nullWhenNoTokensRemain
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        var stream = Arrays.stream(value.split(","))
            .map(String::trim);

        if (nullWhenNoTokensRemain) {
            List<String> parsed = stream
                .filter(token -> !token.isEmpty())
                .map(normalizer)
                .toList();

            return parsed.isEmpty() ? null : parsed;
        }

        return stream
            .map(normalizer)
            .toList();
    }

    private static LocalDate tryParseLocalDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
