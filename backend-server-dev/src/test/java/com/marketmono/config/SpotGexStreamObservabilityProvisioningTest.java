package com.marketmono.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexStreamObservabilityProvisioningTest {

    private static final Path DASHBOARD_PATH = Path.of(
        "scripts/observability/central/grafana/provisioning/dashboards/json/marketmono-spot-gex-stream.json"
    );
    private static final Path ALERT_RULES_PATH = Path.of(
        "scripts/observability/central/grafana/provisioning/alerting/marketmono-spot-gex-stream-alert-rules.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void streamDisconnectedAlertShouldBeOwnerAware() throws Exception {
        String alertRulesYaml = Files.readString(ALERT_RULES_PATH);

        assertThat(alertRulesYaml)
            .contains("uid: mm_spot_gex_stream_disconnected")
            .contains("max(spot_gex_stream_owner{job=\"backend-server\"} * spot_gex_stream_connected{job=\"backend-server\"})")
            .doesNotContain("min(spot_gex_stream_connected{job=\"backend-server\"}) by (host)");
    }

    @Test
    void restFallbackAlertShouldIgnoreExpectedOutsideStreamCoverage() throws Exception {
        String alertRulesYaml = Files.readString(ALERT_RULES_PATH);

        assertThat(alertRulesYaml)
            .contains("uid: mm_spot_gex_rest_calls_stream_mode")
            .contains("marketmono_spot_gex_latest_requests_total")
            .contains("coverage_class=\"live_fallback\"")
            .contains("fallback_reason!=\"outside_stream_coverage\"")
            .doesNotContain("marketmono_spot_gex_rest_requests_total{source_mode=\"stream\"}");
    }

    @Test
    void dashboardShowsReasonAwareLatestSnapshotCoverage() throws Exception {
        String dashboardJson = Files.readString(DASHBOARD_PATH);
        JsonNode dashboard = objectMapper.readTree(dashboardJson);

        assertThat(dashboard.path("uid").asText()).isEqualTo("spot-gex-stream");
        assertThat(targetExpressions(panelByTitle(dashboard, "Latest Snapshot Coverage")))
            .containsExactly(
                "sum by (coverage_class, fallback_reason, result) (increase(marketmono_spot_gex_latest_requests_total{host=~\"$host\"}[$__range]))"
            );
        assertThat(targetExpressions(panelByTitle(dashboard, "Expected Outside-Stream Fallbacks")))
            .containsExactly(
                "sum(increase(marketmono_spot_gex_latest_requests_total{host=~\"$host\",coverage_class=\"live_fallback\",fallback_reason=\"outside_stream_coverage\"}[$__range]))"
            );
        assertThat(targetExpressions(panelByTitle(dashboard, "Decoded Charm OI Presence")))
            .containsExactly(
                "sum by (host, charm_oi) (rate(spot_gex_live_gex_records_decoded_total{host=~\"$host\"}[$__rate_interval]))"
            );
    }

    private static JsonNode panelByTitle(JsonNode dashboard, String title) {
        for (JsonNode panel : dashboard.path("panels")) {
            if (title.equals(panel.path("title").asText())) {
                return panel;
            }
        }
        throw new AssertionError("Panel not found: " + title);
    }

    private static List<String> targetExpressions(JsonNode panel) {
        List<String> expressions = new ArrayList<>();
        for (JsonNode target : panel.path("targets")) {
            expressions.add(target.path("expr").asText());
        }
        return expressions;
    }
}
