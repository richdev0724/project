package com.marketmono.controller;

import com.marketmono.dto.admin.InternalEmailRequest;
import com.marketmono.service.auth.EmailService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AdminEmailControllerTest {

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AdminEmailController adminEmailController;

    @Test
    void shouldSendInternalAdminEmail() {
        InternalEmailRequest request = new InternalEmailRequest(
            "admin@example.com",
            "Temporary password",
            "<p>Hello</p>",
            "Hello"
        );

        ResponseEntity<Map<String, String>> response = adminEmailController.sendInternalAdminEmail(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("message", "Email sent");
        verify(emailService).sendInternalAdminEmail(
            "admin@example.com",
            "Temporary password",
            "<p>Hello</p>",
            "Hello"
        );
    }

    @Test
    void shouldRejectMissingContent() {
        InternalEmailRequest request = new InternalEmailRequest("admin@example.com", "Subject", " ", "");

        ResponseEntity<Map<String, String>> response = adminEmailController.sendInternalAdminEmail(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("message", "Email content is required");
        verifyNoInteractions(emailService);
    }
}
