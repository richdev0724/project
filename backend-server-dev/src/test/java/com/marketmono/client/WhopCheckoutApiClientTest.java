package com.marketmono.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marketmono.config.WhopCheckoutProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WhopCheckoutApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldCreateCheckoutConfigurationUsingExistingPlanIdBodyShape() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/checkout_configurations", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, """
                {
                  "id": "ch_test",
                  "purchase_url": "https://whop.com/checkout/plan_test?session=ch_test"
                }
                """);
        });
        server.start();

        try {
            WhopCheckoutProperties properties = new WhopCheckoutProperties();
            properties.setApiKey("whop_key");
            properties.setCompanyId("biz_test");
            properties.setApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");

            WhopCheckoutApiClient client = new WhopCheckoutApiClient(WebClient.builder(), properties);

            WhopCheckoutConfigurationResponse response = client.createCheckoutConfiguration(
                new WhopCheckoutConfigurationRequest(
                    "biz_test",
                    "plan_existing",
                    Map.of("marketmono_checkout_attempt_id", "attempt_123"),
                    "https://marketmono.com/",
                    "https://marketmono.com/subscribe",
                    true
                )
            );

            assertThat(response.id()).isEqualTo("ch_test");
            assertThat(response.purchaseUrl()).isEqualTo("https://whop.com/checkout/plan_test?session=ch_test");
            assertThat(method.get()).isEqualTo("POST");
            assertThat(path.get()).isEqualTo("/api/v1/checkout_configurations");
            assertThat(authorization.get()).isEqualTo("Bearer whop_key");

            JsonNode body = objectMapper.readTree(requestBody.get());
            assertThat(body.path("plan_id").asText()).isEqualTo("plan_existing");
            assertThat(body.has("plan")).isFalse();
            assertThat(body.path("metadata").path("marketmono_checkout_attempt_id").asText()).isEqualTo("attempt_123");
            assertThat(body.path("allow_promo_codes").asBoolean()).isTrue();
            assertThat(body.path("redirect_url").asText()).isEqualTo("https://marketmono.com/");
            assertThat(body.path("source_url").asText()).isEqualTo("https://marketmono.com/subscribe");
        } finally {
            server.stop(0);
        }
    }

    private static void writeJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
