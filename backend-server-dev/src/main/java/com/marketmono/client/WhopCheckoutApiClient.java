package com.marketmono.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketmono.config.WhopCheckoutProperties;
import com.marketmono.exception.WhopCheckoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhopCheckoutApiClient implements WhopCheckoutClient {

    private final WebClient.Builder webClientBuilder;
    private final WhopCheckoutProperties properties;

    @Override
    public WhopCheckoutConfigurationResponse createCheckoutConfiguration(WhopCheckoutConfigurationRequest request) {
        if (!properties.isConfigured()) {
            throw new WhopCheckoutException("Whop checkout is not configured");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plan_id", request.planId());
        body.put("metadata", request.metadata());
        body.put("allow_promo_codes", request.allowPromoCodes());
        if (request.redirectUrl() != null && !request.redirectUrl().isBlank()) {
            body.put("redirect_url", request.redirectUrl());
        }
        if (request.sourceUrl() != null && !request.sourceUrl().isBlank()) {
            body.put("source_url", request.sourceUrl());
        }

        try {
            JsonNode response = webClientBuilder
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build()
                .post()
                .uri("/checkout_configurations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(properties.getRequestTimeout());

            String id = response != null ? text(response, "id") : null;
            String purchaseUrl = response != null ? text(response, "purchase_url") : null;
            if (id == null || purchaseUrl == null) {
                throw new WhopCheckoutException("Whop checkout configuration response was missing id or purchase_url");
            }
            return new WhopCheckoutConfigurationResponse(id, purchaseUrl);
        } catch (WebClientResponseException e) {
            log.warn(
                "Whop checkout configuration request failed with status {} body {}",
                e.getStatusCode().value(),
                responseBody(e)
            );
            throw new WhopCheckoutException("Whop checkout configuration request failed", e);
        } catch (WhopCheckoutException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new WhopCheckoutException("Whop checkout configuration request failed", e);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null || text.isBlank() ? null : text;
    }

    private String responseBody(WebClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        int limit = 500;
        return body.length() <= limit ? body : body.substring(0, limit) + "...";
    }
}
