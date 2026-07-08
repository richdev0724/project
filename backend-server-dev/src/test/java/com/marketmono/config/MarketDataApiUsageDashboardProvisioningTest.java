package com.marketmono.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MarketDataApiUsageDashboardProvisioningTest {

    private static final int GRAFANA_ALERT_RULE_UID_MAX_LENGTH = 40;
    private static final Pattern ALERT_RULE_UID_PATTERN = Pattern.compile("(?m)^\\s*-\\s*uid:\\s*(\\S+)\\s*$");

    private static final Path DASHBOARD_PATH = Path.of(
        "scripts/observability/central/grafana/provisioning/dashboards/json/marketmono-market-data-api-usage.json"
    );
    private static final Path ALERT_RULES_PATH = Path.of(
        "scripts/observability/central/grafana/provisioning/alerting/marketmono-market-data-api-alert-rules.yaml"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void marketDataDashboardShowsDailyQuotaUsageClimbingTowardDailyLimit() throws Exception {
        assertThat(DASHBOARD_PATH).exists();

        String dashboardJson = Files.readString(DASHBOARD_PATH);
        JsonNode dashboard = objectMapper.readTree(dashboardJson);
        JsonNode dailyQuotaPanel = panelByTitle(dashboard, "Market data daily quota");

        assertThat(dashboard.path("uid").asText()).isEqualTo("marketmono-market-data-api-usage");
        assertThat(dashboard.path("title").asText()).isEqualTo("MarketMono Market Data API Usage");
        assertThat(dailyQuotaPanel.path("type").asText()).isEqualTo("timeseries");
        assertThat(dailyQuotaPanel.path("gridPos").path("y").asInt()).isZero();
        assertThat(dailyQuotaPanel.path("gridPos").path("w").asInt()).isEqualTo(24);
        assertThat(dailyQuotaPanel.path("gridPos").path("h").asInt()).isGreaterThanOrEqualTo(8);
        assertThat(targetExpressions(dailyQuotaPanel))
            .containsExactly(
                "max(marketmono_market_data_budget_daily_usage)",
                "max(marketmono_market_data_budget_daily_projected_usage)",
                "max(marketmono_market_data_budget_daily_limit)"
            );
        assertThat(targetLegends(dailyQuotaPanel))
            .containsExactly("daily usage", "daily projected usage", "daily limit");
        assertThat(dailyQuotaPanel.toString())
            .doesNotContain("\"value\":90000", "\"value\":120000", "\"value\":150000");
        assertThat(dashboardJson).doesNotContain("marketmono_market_data_budget_daily_remaining");
    }

    @Test
    void marketDataDashboardContainsRolloutSourceTierAndCachePanels() throws Exception {
        String dashboardJson = Files.readString(DASHBOARD_PATH);
        JsonNode dashboard = objectMapper.readTree(dashboardJson);

        assertThat(targetExpressions(panelByTitle(dashboard, "Market data reservations by operation and source")))
            .containsExactly("sum by (operation, source) (rate(marketmono_market_data_requests_reserved_total[5m]))");
        assertThat(targetExpressions(panelByTitle(dashboard, "Market data outcomes by operation result and source")))
            .containsExactly("sum by (operation, result, source) (increase(marketmono_market_data_requests_completed_total[$__range]))");
        assertThat(targetExpressions(panelByTitle(dashboard, "Spot-GEX collection calls by ticker and tier")))
            .containsExactly("sum by (ticker, tier, result) (increase(marketmono_spot_gex_collection_calls_total[$__range]))");
        assertThat(targetExpressions(panelByTitle(dashboard, "Spot-GEX skipped collection by tier and reason")))
            .containsExactly("sum by (tier, reason) (increase(marketmono_spot_gex_collection_skipped_total[$__range]))");
        assertThat(targetExpressions(panelByTitle(dashboard, "Market data cache requests by cache and result")).get(0))
            .contains(
                "marketmono_cache_requests_total",
                "marketmono_market_data_news_cache_requests_total",
                "marketmono_spot_gex_expiration_cache_requests_total"
            );
    }

    @Test
    void marketDataAlertRulesCoverProjectionSpikeCacheAndExtendedTierGates() throws Exception {
        assertThat(ALERT_RULES_PATH).exists();

        String alertRulesYaml = Files.readString(ALERT_RULES_PATH);

        assertThat(alertRulesYaml)
            .contains(
                "uid: mm_market_data_daily_projection_warning",
                "uid: mm_market_data_daily_projection_stop",
                "uid: mm_market_data_spot_gex_spike",
                "uid: mm_market_data_cache_errors",
                "uid: mm_market_data_ext_tier_stop",
                "max(marketmono_market_data_budget_daily_projected_usage)",
                "marketmono_spot_gex_collection_calls_total{tier=\"extended\"}",
                "marketmono_market_data_news_cache_requests_total",
                "marketmono_spot_gex_expiration_cache_requests_total"
            );
    }

    @Test
    void marketDataAlertRuleUidsStayWithinGrafanaLimit() throws Exception {
        String alertRulesYaml = Files.readString(ALERT_RULES_PATH);

        assertThat(alertRuleUids(alertRulesYaml))
            .isNotEmpty()
            .allSatisfy(uid -> assertThat(uid).hasSizeLessThanOrEqualTo(GRAFANA_ALERT_RULE_UID_MAX_LENGTH));
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

    private static List<String> targetLegends(JsonNode panel) {
        List<String> legends = new ArrayList<>();
        for (JsonNode target : panel.path("targets")) {
            legends.add(target.path("legendFormat").asText());
        }
        return legends;
    }

    private static List<String> alertRuleUids(String alertRulesYaml) {
        List<String> uids = new ArrayList<>();
        Matcher matcher = ALERT_RULE_UID_PATTERN.matcher(alertRulesYaml);
        while (matcher.find()) {
            uids.add(matcher.group(1));
        }
        return uids;
    }
}
