package com.marketmono.controller;

import com.marketmono.dto.admin.InternalEmailRequest;
import com.marketmono.service.auth.EmailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/email")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailController {

    private final EmailService emailService;

    @PostMapping("/send")
    public ResponseEntity<Map<String, String>> sendInternalAdminEmail(
            @Valid @RequestBody InternalEmailRequest request) {
        if (!StringUtils.hasText(request.htmlContent()) && !StringUtils.hasText(request.textContent())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "Email content is required"));
        }

        emailService.sendInternalAdminEmail(
            request.to(),
            request.subject(),
            request.htmlContent(),
            request.textContent()
        );
        return ResponseEntity.ok(Map.of("message", "Email sent"));
    }
}
