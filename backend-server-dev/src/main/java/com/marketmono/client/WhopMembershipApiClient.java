package com.marketmono.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.marketmono.config.WhopCheckoutProperties;
import com.marketmono.exception.WhopCheckoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Component
@RequiredArgsConstructor
@Slf4j
public class WhopMembershipApiClient implements WhopMembershipClient {

    private final WebClient.Builder webClientBuilder;
    private final WhopCheckoutProperties properties;

    @Override
    public WhopMembershipResponse retrieveMembership(String membershipId) {
        if (!properties.isConfigured()) {
            throw new WhopCheckoutException("Whop checkout is not configured");
        }
        if (membershipId == null || membershipId.isBlank()) {
            throw new WhopCheckoutException("Whop membership ID is required");
        }

        try {
            JsonNode response = webClientBuilder
                .baseUrl(properties.getApiBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .build()
                .get()
                .uri("/memberships/{membershipId}", membershipId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block(properties.getRequestTimeout());

            String id = response != null ? text(response, "id") : null;
            String manageUrl = response != null ? text(response, "manage_url") : null;
            return new WhopMembershipResponse(id, manageUrl);
        } catch (WebClientResponseException e) {
            log.warn(
                "Whop membership retrieval failed with status {} body {}",
                e.getStatusCode().value(),
                responseBody(e)
            );
            throw new WhopCheckoutException("Whop membership retrieval failed", e);
        } catch (WhopCheckoutException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new WhopCheckoutException("Whop membership retrieval failed", e);
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
