package com.marketmono.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptionChainParserTest {

    @Test
    void shouldParseOccOptionSymbol() {
        var parsed = OptionChainParser.parse("AAPL260417C00195000");

        assertThat(parsed).isPresent();
        assertThat(parsed.get().underlyingSymbol()).isEqualTo("AAPL");
        assertThat(parsed.get().expiry()).isEqualTo(LocalDate.of(2026, 4, 17));
        assertThat(parsed.get().optionType()).isEqualTo('C');
        assertThat(parsed.get().strike()).isEqualByComparingTo(new BigDecimal("195.000"));
    }

    @Test
    void shouldExtractUniqueExpirationsAndIgnoreInvalidSymbols() {
        var expirations = OptionChainParser.extractUniqueExpirations(List.of(
            "SPY260417C00500000",
            "SPY260417P00500000",
            "SPY260425C00505000",
            "not-a-contract"
        ));

        assertThat(expirations).containsExactly(
            LocalDate.of(2026, 4, 17),
            LocalDate.of(2026, 4, 25)
        );
    }

    @Test
    void shouldReturnEmptyWhenSymbolIsInvalid() {
        assertThat(OptionChainParser.parse("bad-data")).isEmpty();
        assertThat(OptionChainParser.parse(null)).isEmpty();
        assertThat(OptionChainParser.extractUniqueExpirations(List.of())).isEmpty();
    }
}
