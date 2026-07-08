package com.marketmono.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class SpotGexExposureRepositoryImplSqlTest {

    @Test
    void snapshotSqlShouldUseDynamicFiltersInsteadOfNullableParameterPredicates() throws Exception {
        Field sqlField = SpotGexExposureRepositoryImpl.class.getDeclaredField("FIND_SNAPSHOT_SQL");
        sqlField.setAccessible(true);

        String sql = (String) sqlField.get(null);

        assertThat(sql)
            .contains("FROM spot_gex_exposures")
            .contains("%s")
            .doesNotContain("IS NULL OR")
            .doesNotContain("? is null");
    }

    @Test
    void historySqlShouldNotRequireTimescaleFunctions() throws Exception {
        Field sqlField = SpotGexExposureRepositoryImpl.class.getDeclaredField("FIND_HISTORY_SQL");
        sqlField.setAccessible(true);

        String sql = (String) sqlField.get(null);

        assertThat(sql)
            .doesNotContain("time_bucket(")
            .doesNotContain("last(");
    }

    @Test
    void historySqlShouldReduceWideRowsAfterSelectingCandidateKeys() throws Exception {
        Field sqlField = SpotGexExposureRepositoryImpl.class.getDeclaredField("FIND_HISTORY_SQL");
        sqlField.setAccessible(true);

        String sql = (String) sqlField.get(null);

        assertThat(sql)
            .contains("candidate_keys")
            .contains("JOIN spot_gex_exposures s")
            .doesNotContain("ROW_NUMBER() OVER");
    }

    @Test
    void collectionStatusSqlShouldScopeWorkToRequestedTickers() throws Exception {
        Field sqlField = SpotGexExposureRepositoryImpl.class.getDeclaredField("FIND_COLLECTION_STATUS_SQL");
        sqlField.setAccessible(true);

        String sql = (String) sqlField.get(null);

        assertThat(sql)
            .contains("requested_tickers")
            .contains("unnest(?::text[])")
            .contains("WHERE e.ticker = rt.ticker")
            .contains("AND e.collected_at >= CURRENT_DATE")
            .contains("COUNT(DISTINCT e.collected_at)")
            .contains("LEFT JOIN LATERAL")
            .doesNotContain("GROUP BY ticker")
            .doesNotContain("distinct_cycles");
    }
}
