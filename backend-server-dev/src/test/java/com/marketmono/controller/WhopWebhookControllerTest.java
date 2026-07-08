package com.marketmono.controller;

import com.marketmono.exception.WhopWebhookVerificationException;
import com.marketmono.service.WhopWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WhopWebhookControllerTest {

    private final WhopWebhookService webhookService = mock(WhopWebhookService.class);
    private final MockMvc mockMvc = MockMvcBuilders
        .standaloneSetup(new WhopWebhookController(webhookService))
        .build();

    @Test
    void shouldPassRawPayloadAndStandardWebhookHeadersToService() throws Exception {
        String payload = "{\"type\":\"payment_succeeded\"}";

        mockMvc.perform(post("/api/v1/whop/webhooks")
                .header("webhook-id", "msg_valid")
                .header("webhook-timestamp", "1800000000")
                .header("webhook-signature", "v1,signature")
                .contentType("application/json")
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(content().string("Webhook processed"));

        verify(webhookService).handleWebhook("msg_valid", "1800000000", "v1,signature", payload);
    }

    @Test
    void shouldReturnUnauthorizedForSignatureVerificationFailure() throws Exception {
        doThrow(new WhopWebhookVerificationException("Invalid Whop webhook signature"))
            .when(webhookService)
            .handleWebhook("msg_invalid", "1800000000", "v1,bad", "{}");

        mockMvc.perform(post("/api/v1/whop/webhooks")
                .header("webhook-id", "msg_invalid")
                .header("webhook-timestamp", "1800000000")
                .header("webhook-signature", "v1,bad")
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string("Invalid signature"));
    }
}
