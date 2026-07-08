package com.marketmono.controller;

import com.marketmono.dto.InstitutionalOwnershipResponseDto;
import com.marketmono.service.InstitutionalOwnershipService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for institutional ownership data
 * Endpoint: GET /api/v1/institution/{ticker}/ownership
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class InstitutionalOwnershipController {

    private final InstitutionalOwnershipService institutionalOwnershipService;

    /**
     * Get institutional ownership data for a ticker
     *
     * @param ticker         Stock ticker symbol(s), comma-separated
     * @param date           Report date in YYYY-MM-DD format (optional)
     * @param startDate      Only holdings on or after this date (optional)
     * @param endDate        Only holdings on or before this date (optional)
     * @param limit          Number of results (default: 500, max: 500, min: 1)
     * @param page           Page number (starts at 0)
     * @param order          Column to sort by (optional)
     * @param orderDirection Sort direction: "asc" or "desc" (default: "desc")
     * @param tags           Array of institution tags (optional, e.g. "activist")
     * @return Institutional ownership response
     */
    @GetMapping("/institution/{ticker}/ownership")
    public ResponseEntity<InstitutionalOwnershipResponseDto> getInstitutionalOwnership(
            @PathVariable String ticker,
            @RequestParam(required = false) String date,
            @RequestParam(name = "start_date", required = false) String startDate,
            @RequestParam(name = "end_date", required = false) String endDate,
            @RequestParam(required = false, defaultValue = "500") Integer limit,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) String order,
            @RequestParam(name = "order_direction", required = false) String orderDirection,
            @RequestParam(name = "tags[]", required = false) List<String> tags
    ) {
        log.info("GET /api/v1/institution/{}/ownership - date: {}, limit: {}, page: {}, order: {}, orderDirection: {}",
                ticker, date, limit, page, order, orderDirection);

        try {
            ticker = ticker.toUpperCase();

            InstitutionalOwnershipResponseDto response = institutionalOwnershipService.getInstitutionalOwnership(
                    ticker, date, startDate, endDate, limit, page, order, orderDirection, tags
            );

            if (response != null && response.data() != null) {
                log.info("Returning {} institutional ownership records for {}", response.data().size(), ticker);
                return ResponseEntity.ok(response);
            } else {
                log.warn("No institutional ownership data available for {}", ticker);
                return ResponseEntity.ok(new InstitutionalOwnershipResponseDto(java.util.List.of()));
            }
        } catch (Exception e) {
            log.error("Error fetching institutional ownership for {}", ticker, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
