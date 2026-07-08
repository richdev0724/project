package com.marketmono.controller;

import com.marketmono.dto.InsiderTransactionsResponseDto;
import com.marketmono.exception.UnusualWhalesShedException;
import com.marketmono.service.InsiderTransactionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for insider transactions data
 * Endpoint: GET /api/v1/insider-trades
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class InsiderTransactionsController {

    private final InsiderTransactionsService insiderTransactionsService;

    /**
     * Get insider transactions with optional ticker filter
     * Returns the latest insider transactions from SEC filings
     *
     * @param tickerSymbol Optional ticker symbol to filter by
     * @param limit Number of results (default: 500, max: 500, min: 1)
     * @return Insider transactions response
     * 
     * Example response:
     * {
     *   "data": [
     *     {
     *       "amount": -35921,
     *       "date_excercisable": null,
     *       "director_indirect": null,
     *       "expiration_date": null,
     *       "filing_date": "2024-12-12",
     *       "formtype": "144",
     *       "id": "2662b73d-0ad6-4568-adb0-739b96c18090",
     *       "ids": ["26f2911d-8e40-4fd4-9b74-c450e203a0ad"],
     *       "is_10b5_1": true,
     *       "is_director": true,
     *       "is_officer": true,
     *       "is_s_p_500": true,
     *       "is_ten_percent_owner": true,
     *       "marketcap": "1375122749418",
     *       "natureofownership": null,
     *       "next_earnings_date": "2025-02-06",
     *       "officer_title": "COB and CEO",
     *       "owner_name": "ZUCKERBERG MARK",
     *       "price": "632",
     *       "price_excercisable": null,
     *       "sector": "Communication Services",
     *       "security_ad_code": null,
     *       "security_title": null,
     *       "shares_owned_after": null,
     *       "shares_owned_before": null,
     *       "stock_price": null,
     *       "ticker": "META",
     *       "transaction_code": "S",
     *       "transaction_date": "2024-12-12",
     *       "transactions": 1
     *     }
     *   ]
     * }
     */
    @GetMapping("/insider-trades")
    public ResponseEntity<InsiderTransactionsResponseDto> getInsiderTransactions(
            @RequestParam(name = "ticker_symbol", required = false) String tickerSymbol,
            @RequestParam(required = false, defaultValue = "500") Integer limit
    ) {
        log.info("GET /api/v1/insider-trades - ticker: {}, limit: {}", tickerSymbol, limit);

        try {
            if (tickerSymbol != null && !tickerSymbol.isBlank()) {
                tickerSymbol = tickerSymbol.toUpperCase();
            }
            
            InsiderTransactionsResponseDto response = insiderTransactionsService.getInsiderTransactions(
                tickerSymbol, limit
            );

            if (response != null && response.data() != null) {
                log.info("Returning {} insider transactions", response.data().size());
                return ResponseEntity.ok(response);
            } else {
                log.warn("No insider transactions available");
                return ResponseEntity.ok(new InsiderTransactionsResponseDto(java.util.List.of()));
            }
        } catch (UnusualWhalesShedException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error fetching insider transactions", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
