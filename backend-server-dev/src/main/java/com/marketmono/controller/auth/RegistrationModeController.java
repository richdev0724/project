package com.marketmono.controller.auth;

import com.marketmono.dto.registration.RegistrationModeResponse;
import com.marketmono.dto.registration.UpdateRegistrationModeRequest;
import com.marketmono.service.auth.RegistrationModeService;
import com.marketmono.util.PrincipalUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public and admin endpoints for global registration availability.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class RegistrationModeController {

    private final RegistrationModeService registrationModeService;

    @GetMapping({"/api/v1/registration/mode", "/api/v1/registration-mode"})
    public ResponseEntity<RegistrationModeResponse> getRegistrationMode() {
        log.debug("GET /api/v1/registration/mode");
        return ResponseEntity.ok(registrationModeService.getRegistrationMode());
    }

    @GetMapping("/api/v1/admin/registration/mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistrationModeResponse> getAdminRegistrationMode() {
        log.debug("GET /api/v1/admin/registration/mode");
        return ResponseEntity.ok(registrationModeService.getRegistrationMode());
    }

    @PutMapping("/api/v1/admin/registration/mode")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RegistrationModeResponse> updateRegistrationMode(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateRegistrationModeRequest request) {
        UUID adminId = PrincipalUtils.requireRegistrationModeActorId(userDetails);

        log.info("PUT /api/v1/admin/registration/mode - adminId: {}, mode: {}", adminId, request.mode());
        return ResponseEntity.ok(registrationModeService.updateRegistrationMode(request.mode(), adminId));
    }

}
