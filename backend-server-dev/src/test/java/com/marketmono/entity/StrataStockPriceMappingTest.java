package com.marketmono.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StrataStockPriceMappingTest {

    @Test
    void shouldBindRawResponseAsJsonForPostgresJsonbColumn() throws NoSuchFieldException {
        Field rawResponse = StrataStockPrice.class.getDeclaredField("rawResponse");

        JdbcTypeCode jdbcTypeCode = rawResponse.getAnnotation(JdbcTypeCode.class);

        assertThat(jdbcTypeCode).isNotNull();
        assertThat(jdbcTypeCode.value()).isEqualTo(SqlTypes.JSON);
    }

    @Test
    void shouldAllowRepeatedTapeTimeRowsSoEachSuccessfulPollCanBeStored() throws Exception {
        jakarta.persistence.Table table = StrataStockPrice.class.getAnnotation(jakarta.persistence.Table.class);

        assertThat(table.uniqueConstraints()).isEmpty();

        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/135-strata-stock-prices.yaml"
        ));
        assertThat(changelog).contains("DROP INDEX IF EXISTS uq_strata_stock_prices_display_source_tape");
    }
}
