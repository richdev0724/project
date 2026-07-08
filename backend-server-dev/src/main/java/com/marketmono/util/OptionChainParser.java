package com.marketmono.util;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Utility methods for parsing OCC option symbols returned by upstream market data provider.
 */
public final class OptionChainParser {

    private static final Pattern OCC_PATTERN = Pattern.compile(
        "^(?<symbol>[\\w]*)(?<expiry>\\d{6})(?<type>[PC])(?<strike>\\d{8})$"
    );

    private OptionChainParser() {}

    public static Optional<ParsedOptionSymbol> parse(String optionSymbol) {
        if (optionSymbol == null || optionSymbol.isBlank()) {
            return Optional.empty();
        }

        var matcher = OCC_PATTERN.matcher(optionSymbol.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            LocalDate expiry = parseExpiry(matcher.group("expiry"));
            BigDecimal strike = new BigDecimal(matcher.group("strike")).movePointLeft(3);
            return Optional.of(new ParsedOptionSymbol(
                matcher.group("symbol"),
                expiry,
                matcher.group("type").charAt(0),
                strike
            ));
        } catch (DateTimeException | NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static SortedSet<LocalDate> extractUniqueExpirations(List<String> optionSymbols) {
        SortedSet<LocalDate> expirations = new TreeSet<>();
        if (optionSymbols == null || optionSymbols.isEmpty()) {
            return expirations;
        }

        for (String optionSymbol : optionSymbols) {
            parse(optionSymbol)
                .map(ParsedOptionSymbol::expiry)
                .ifPresent(expirations::add);
        }
        return expirations;
    }

    private static LocalDate parseExpiry(String yymmdd) {
        int year = 2000 + Integer.parseInt(yymmdd.substring(0, 2));
        int month = Integer.parseInt(yymmdd.substring(2, 4));
        int day = Integer.parseInt(yymmdd.substring(4, 6));
        return LocalDate.of(year, month, day);
    }

    public record ParsedOptionSymbol(
        String underlyingSymbol,
        LocalDate expiry,
        char optionType,
        BigDecimal strike
    ) {}
}
