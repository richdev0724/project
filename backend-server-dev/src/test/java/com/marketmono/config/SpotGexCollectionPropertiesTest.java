package com.marketmono.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexCollectionPropertiesTest {

    @Test
    void shouldDisableSpotGexCollectionByDefault() {
        SpotGexCollectionProperties properties = new SpotGexCollectionProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getRestOnlyIntervalMs()).isEqualTo(180_000L);
    }

    @Test
    void applicationConfigShouldDefaultSpotGexCollectionToDisabled() throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            assertThat(stream).isNotNull();
            String applicationYaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(applicationYaml).contains("enabled: ${SPOT_GEX_ENABLED:false}");
            assertThat(applicationYaml).contains("rest-only-interval-ms: ${SPOT_GEX_REST_ONLY_INTERVAL_MS:180000}");
            assertThat(applicationYaml)
                .contains("group-id: ${SPOT_GEX_KAFKA_GROUP_ID:${KAFKA_GROUP_ID:marketmono_farrukh}}");
        }
    }

    @Test
    void productionDeployShouldEnableSpotGexByDefaultWithVariableOverride() throws IOException {
        String deployWorkflow = Files.readString(
            Path.of(".github", "workflows", "deploy-backend.yml"),
            StandardCharsets.UTF_8
        );

        assertThat(deployWorkflow).contains("SPOT_GEX_ENABLED=${{ vars.SPOT_GEX_ENABLED || 'true' }}");
        assertThat(deployWorkflow).contains(
            "SPOT_GEX_KAFKA_GROUP_ID=${{ vars.SPOT_GEX_KAFKA_GROUP_ID || vars.KAFKA_GROUP_ID || 'marketmono_farrukh' }}"
        );
    }
}
