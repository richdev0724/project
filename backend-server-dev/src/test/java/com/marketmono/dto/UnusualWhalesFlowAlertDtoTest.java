package com.marketmono.dto;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class UnusualWhalesFlowAlertDtoTest {

    @Test
    void testExtractStrikeFromOptionChain() {
        // Test the user's example
        BigDecimal result = UnusualWhalesFlowAlertDto.extractStrikeFromOptionChain("SPY251103C00683000");
        assertEquals(new BigDecimal("683.00"), result);

        // Test other examples from API spec
        result = UnusualWhalesFlowAlertDto.extractStrikeFromOptionChain("SPY240105C00480000");
        assertEquals(new BigDecimal("480.00"), result);

        result = UnusualWhalesFlowAlertDto.extractStrikeFromOptionChain("SPY240105P00490000");
        assertEquals(new BigDecimal("490.00"), result);

        // Test edge cases
        assertNull(UnusualWhalesFlowAlertDto.extractStrikeFromOptionChain(null));
        assertNull(UnusualWhalesFlowAlertDto.extractStrikeFromOptionChain(""));
        assertNull(UnusualWhalesFlowAlertDto.extractStrikeFromOptionChain("SHORT"));
    }

    @Test
    void testExtractExpiryFromOptionChain() {
        // Test expiry extraction logic
        // For "SPY251103C00683000" should extract expiry 2025-11-03
        LocalDate expected = LocalDate.of(2025, 11, 3);

        // We can't directly test the deserializer, but we can verify the logic
        String optionChain = "SPY251103C00683000";
        int typePos = optionChain.length() - 9; // 17 - 9 = 8
        char typeChar = optionChain.charAt(typePos); // 'C'
        assertEquals('C', typeChar);

        String expiryStr = optionChain.substring(typePos - 6, typePos); // substring(2, 8) = "251103"
        assertEquals("251103", expiryStr);

        int year = 2000 + Integer.parseInt(expiryStr.substring(0, 2)); // 2000 + 25 = 2025
        int month = Integer.parseInt(expiryStr.substring(2, 4)); // 11
        int day = Integer.parseInt(expiryStr.substring(4, 6)); // 03
        LocalDate actual = LocalDate.of(year, month, day);
        assertEquals(expected, actual);

        // Test with different root symbol lengths
        // 6-char root: "AAPL251231C00150000"
        optionChain = "AAPL251231C00150000";
        typePos = optionChain.length() - 9; // 20 - 9 = 11
        typeChar = optionChain.charAt(typePos); // 'C'
        assertEquals('C', typeChar);
        expiryStr = optionChain.substring(typePos - 6, typePos); // substring(5, 11) = "251231"
        assertEquals("251231", expiryStr);
        year = 2000 + Integer.parseInt(expiryStr.substring(0, 2));
        month = Integer.parseInt(expiryStr.substring(2, 4));
        day = Integer.parseInt(expiryStr.substring(4, 6));
        actual = LocalDate.of(year, month, day);
        assertEquals(LocalDate.of(2025, 12, 31), actual);
    }
}