package com.marketmono.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestParamUtilsTest {

    @Test
    void parseUpperCsvReturnsNullForEmptyOptionTradeInput() {
        assertThat(RequestParamUtils.parseUpperCsv(null)).isNull();
        assertThat(RequestParamUtils.parseUpperCsv("")).isNull();
        assertThat(RequestParamUtils.parseUpperCsv("   ")).isNull();
        assertThat(RequestParamUtils.parseUpperCsv(",")).isNull();
        assertThat(RequestParamUtils.parseUpperCsv(" , ")).isNull();
    }

    @Test
    void parseUpperCsvTrimsFiltersEmptyTokensAndUppercasesValues() {
        assertThat(RequestParamUtils.parseUpperCsv(" spy, , qqq,iwm "))
            .containsExactly("SPY", "QQQ", "IWM");
    }

    @Test
    void parseLowerCsvTrimsFiltersEmptyTokensAndLowercasesValues() {
        assertThat(RequestParamUtils.parseLowerCsv(" MID, ,Mega,small "))
            .containsExactly("mid", "mega", "small");
        assertThat(RequestParamUtils.parseLowerCsv(" , ")).isNull();
    }

    @Test
    void parseLocalDateCsvSkipsInvalidAndEmptyValues() {
        assertThat(RequestParamUtils.parseLocalDateCsv("2026-03-20, nope, , 2026-03-21"))
            .containsExactly(LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 21));
        assertThat(RequestParamUtils.parseLocalDateCsv("bad, , also-bad")).isNull();
    }

    @Test
    void normalizeSideReturnsUppercaseSideOrNull() {
        assertThat(RequestParamUtils.normalizeSide(" ask_side ")).isEqualTo("ASK_SIDE");
        assertThat(RequestParamUtils.normalizeSide(null)).isNull();
        assertThat(RequestParamUtils.normalizeSide("   ")).isNull();
    }

    @Test
    void parseUpperCsvPreservingEmptyTokensKeepsFlowAlertCsvParity() {
        assertThat(RequestParamUtils.parseUpperCsvPreservingEmptyTokens(null)).isNull();
        assertThat(RequestParamUtils.parseUpperCsvPreservingEmptyTokens("   ")).isNull();
        assertThat(RequestParamUtils.parseUpperCsvPreservingEmptyTokens(",")).isEqualTo(List.of());
        assertThat(RequestParamUtils.parseUpperCsvPreservingEmptyTokens(" spy, , qqq "))
            .containsExactly("SPY", "", "QQQ");
    }

    @Test
    void parseLowerCsvPreservingEmptyTokensKeepsFlowAlertCsvParity() {
        assertThat(RequestParamUtils.parseLowerCsvPreservingEmptyTokens(null)).isNull();
        assertThat(RequestParamUtils.parseLowerCsvPreservingEmptyTokens("   ")).isNull();
        assertThat(RequestParamUtils.parseLowerCsvPreservingEmptyTokens(",")).isEqualTo(List.of());
        assertThat(RequestParamUtils.parseLowerCsvPreservingEmptyTokens(" MID, ,Mega "))
            .containsExactly("mid", "", "mega");
    }
}
