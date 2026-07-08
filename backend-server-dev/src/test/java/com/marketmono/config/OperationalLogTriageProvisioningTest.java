package com.marketmono.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalLogTriageProvisioningTest {

    private static final Path DASHBOARD_PATH = Path.of(
        "scripts/observability/central/grafana/provisioning/dashboards/logs/marketmono-operational-log-triage.json"
    );
    private static final Path ALERT_RULES_PATH = Path.of(
        "scripts/observability/central/grafana/provisioning/alerting/marketmono-alert-rules.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void errorBurstAlertShouldExcludeKnownProviderAndTokenNoise() throws Exception {
        String alertRulesYaml = Files.readString(ALERT_RULES_PATH);

        assertThat(alertRulesYaml)
            .contains("uid: mm_error_log_burst")
            .contains("|!~ \"(?i)(NewsAPI.*429|Marketaux.*402|MarketAux.*402|Received 429 rate limit response|Endpoint returned 422|Invalid refresh token|Refresh token is expired or revoked)\"");
    }

    @Test
    void operationalLogTriageShouldSeparateProviderQuotaAndPaymentNoise() throws Exception {
        String dashboardJson = Files.readString(DASHBOARD_PATH);
        JsonNode dashboard = objectMapper.readTree(dashboardJson);

        assertThat(targetExpressions(panelByTitle(dashboard, "Provider Quota / Payment Noise")))
            .containsExactly(
                "{host=~\"$host\", job=~\"$job\"} |~ \"(?i)(NewsAPI|Marketaux|MarketAux|upstream market data API|market data provider)\" |~ \"(?i)(429|402|422|rate limit|Payment Required|access blocked|validation error)\" |~ \"$search\""
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
