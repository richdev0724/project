package com.marketmono.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SupportTicketObservabilityProvisioningTest {

    private static final Path DASHBOARD_PATH = Path.of(
        "scripts/observability/central/grafana/provisioning/dashboards/json/marketmono-support-tickets.json"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void supportTicketDashboardIsProvisionedWithPrometheusAndLokiPanels() throws Exception {
        assertThat(DASHBOARD_PATH).exists();

        String dashboardJson = Files.readString(DASHBOARD_PATH);
        JsonNode dashboard = objectMapper.readTree(dashboardJson);

        assertThat(dashboard.path("uid").asText()).isEqualTo("marketmono-support-tickets");
        assertThat(dashboard.path("title").asText()).isEqualTo("MarketMono - Support Tickets");
        assertThat(dashboard.path("panels")).hasSizeGreaterThanOrEqualTo(10);
        assertThat(dashboardJson)
            .contains("marketmono_support_tickets_count")
            .contains("marketmono_support_ticket_notifications_count")
            .contains("marketmono_support_tickets_oldest_open_age_seconds")
            .contains("marketmono_support_ticket_notifications_oldest_pending_age_seconds")
            .contains("http_server_requests_seconds_count")
            .contains("Support ticket notification dispatch failed")
            .contains("job=\\\"backend-server\\\"")
            .contains("\"type\": \"prometheus\"")
            .contains("\"type\": \"loki\"")
            .doesNotContain("postgres-exporter");
    }
}
