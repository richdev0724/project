package com.marketmono.repository;

import com.marketmono.entity.DashboardContractSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DashboardContractSnapshotRepository extends JpaRepository<DashboardContractSnapshot, Long> {

    @Query("SELECT MAX(s.tradingDate) FROM DashboardContractSnapshot s WHERE s.widget = :widget")
    Optional<LocalDate> findLatestTradingDateByWidget(@Param("widget") String widget);

    List<DashboardContractSnapshot> findByWidgetAndTradingDate(String widget, LocalDate tradingDate);

    boolean existsByFetchedAtAfter(Instant cutoff);

    /**
     * Replace-on-write: each collection cycle overwrites the current trading
     * day's snapshot for the same (widget, sort order).
     */
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO dashboard_contract_snapshots (
                widget, sort_order, trading_date, data, fetched_at, created_at, updated_at
            ) VALUES (
                :widget, :sortOrder, :tradingDate, CAST(:data AS jsonb), :fetchedAt, NOW(), NOW()
            )
            ON CONFLICT (widget, sort_order, trading_date) DO UPDATE SET
                data       = EXCLUDED.data,
                fetched_at = EXCLUDED.fetched_at,
                updated_at = NOW()
            """,
        nativeQuery = true
    )
    void upsert(
        @Param("widget") String widget,
        @Param("sortOrder") String sortOrder,
        @Param("tradingDate") LocalDate tradingDate,
        @Param("data") String data,
        @Param("fetchedAt") Instant fetchedAt
    );

    @Transactional
    @Modifying
    @Query("DELETE FROM DashboardContractSnapshot s WHERE s.tradingDate < :cutoff")
    int deleteByTradingDateBefore(@Param("cutoff") LocalDate cutoff);
}
