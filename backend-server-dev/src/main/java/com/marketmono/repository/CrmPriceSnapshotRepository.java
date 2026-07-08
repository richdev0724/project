package com.marketmono.repository;

import com.marketmono.entity.CrmPriceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Repository
public interface CrmPriceSnapshotRepository extends JpaRepository<CrmPriceSnapshot, Long> {

    /** Replace-on-write: only the newest tick per ticker is kept. */
    @Transactional
    @Modifying
    @Query(
        value = """
            INSERT INTO crm_price_snapshots (
                ticker, close, tick_time, volume, created_at, updated_at
            ) VALUES (
                :ticker, :close, :tickTime, :volume, NOW(), NOW()
            )
            ON CONFLICT (ticker) DO UPDATE SET
                close      = EXCLUDED.close,
                tick_time  = EXCLUDED.tick_time,
                volume     = EXCLUDED.volume,
                updated_at = NOW()
            """,
        nativeQuery = true
    )
    void upsert(
        @Param("ticker") String ticker,
        @Param("close") BigDecimal close,
        @Param("tickTime") Instant tickTime,
        @Param("volume") Long volume
    );
}
