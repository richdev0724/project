package com.marketmono.repository;

import com.marketmono.entity.StrataStockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
public interface StrataStockPriceRepository extends JpaRepository<StrataStockPrice, Long> {

    @Query(
        value = """
            SELECT *
            FROM (
                SELECT p.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY p.display_ticker
                           ORDER BY COALESCE(p.tape_time, p.fetched_at) DESC, p.fetched_at DESC, p.id DESC
                       ) AS row_number
                FROM strata_stock_prices p
                WHERE p.display_ticker IN (:displayTickers)
            ) ranked
            WHERE ranked.row_number = 1
            """,
        nativeQuery = true
    )
    List<StrataStockPrice> findLatestByDisplayTickers(@Param("displayTickers") List<String> displayTickers);

    @Modifying
    @Query("DELETE FROM StrataStockPrice p WHERE p.displayTicker IN :displayTickers")
    int deleteByDisplayTickerIn(@Param("displayTickers") Collection<String> displayTickers);

    @Modifying
    @Query("DELETE FROM StrataStockPrice p WHERE p.fetchedAt < :cutoff")
    int deleteByFetchedAtBefore(@Param("cutoff") Instant cutoff);
}
