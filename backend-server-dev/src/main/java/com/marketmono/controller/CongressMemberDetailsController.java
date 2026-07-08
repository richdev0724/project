package com.marketmono.controller;

import com.marketmono.dto.CongressMemberDetailsDto;
import com.marketmono.service.CongressMemberDetailsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for congress member details (bio + image URL)
 * Data loaded from representatives.json at startup
 *
 * Endpoint: GET /api/v1/congress-member-details
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CongressMemberDetailsController {

    private final CongressMemberDetailsService congressMemberDetailsService;

    /**
     * Get congress member details with optional filters.
     * With no params, returns all 538 members.
     *
     * @param name    Search by name (partial, case-insensitive)
     * @param id      Lookup by bioguideId or senator id
     * @param state   Filter by state code (e.g. "CA") or state name
     * @param chamber Filter by "House" or "Senate"
     * @return List of congress member details with image URLs
     */
    @GetMapping("/congress-member-details")
    public ResponseEntity<List<CongressMemberDetailsDto>> getCongressMemberDetails(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String id,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String chamber
    ) {
        log.info("GET /api/v1/congress-member-details - name: {}, id: {}, state: {}, chamber: {}",
                name, id, state, chamber);

        if (id != null && !id.isBlank()) {
            CongressMemberDetailsDto member = congressMemberDetailsService.findById(id);
            if (member != null) {
                return ResponseEntity.ok(List.of(member));
            }
            return ResponseEntity.ok(List.of());
        }

        List<CongressMemberDetailsDto> results;

        if (name != null && !name.isBlank()) {
            results = congressMemberDetailsService.searchByName(name);
        } else if (state != null && !state.isBlank()) {
            results = congressMemberDetailsService.findByState(state);
        } else if (chamber != null && !chamber.isBlank()) {
            results = congressMemberDetailsService.findByChamber(chamber);
        } else {
            results = congressMemberDetailsService.getAllMembers();
        }

        log.info("Returning {} congress member details", results.size());
        return ResponseEntity.ok(results);
    }
}
