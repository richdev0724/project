package com.marketmono.controller;

import com.marketmono.dto.admin.AdminEconomicEventDto;
import com.marketmono.dto.admin.CreateEconomicEventRequest;
import com.marketmono.dto.admin.UpdateEconomicEventRequest;
import com.marketmono.service.EconomicEventAdminService;
import com.marketmono.util.PrincipalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/economic-events")
@RequiredArgsConstructor
@PreAuthorize("hasRole('INTERNAL_ADMIN')")
public class AdminEconomicEventController {

    private final EconomicEventAdminService economicEventAdminService;

    @GetMapping
    public ResponseEntity<List<AdminEconomicEventDto>> listEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "false") boolean includeRetired) {
        return ResponseEntity.ok(economicEventAdminService.listEvents(startDate, endDate, includeRetired));
    }

    @PostMapping
    public ResponseEntity<AdminEconomicEventDto> createEvent(
            @Valid @RequestBody CreateEconomicEventRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(economicEventAdminService.createEvent(request, actorId(userDetails)));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<AdminEconomicEventDto> updateEvent(
            @PathVariable Long eventId,
            @Valid @RequestBody UpdateEconomicEventRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(economicEventAdminService.updateEvent(eventId, request, actorId(userDetails)));
    }

    @PostMapping("/{eventId}/retire")
    public ResponseEntity<AdminEconomicEventDto> retireEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(economicEventAdminService.retireEvent(eventId, actorId(userDetails)));
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Map<String, String>> deleteEvent(
            @PathVariable Long eventId,
            @AuthenticationPrincipal UserDetails userDetails) {
        economicEventAdminService.deleteEvent(eventId, actorId(userDetails));
        return ResponseEntity.ok(Map.of("message", "Economic event deleted"));
    }

    private UUID actorId(UserDetails userDetails) {
        return PrincipalUtils.requireInternalAdminActorId(userDetails);
    }
}
