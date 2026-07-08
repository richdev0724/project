package com.marketmono.repository;

import com.marketmono.entity.OptionTrade;
import com.marketmono.entity.OptionTradeId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OptionTradeRepository extends JpaRepository<OptionTrade, OptionTradeId>,
        JpaSpecificationExecutor<OptionTrade> {

    List<OptionTrade> findByTickerAndExecutedAtBetweenOrderByExecutedAtDesc(
        String ticker, Instant start, Instant end
    );

    List<OptionTrade> findByOptionChainAndExecutedAtAfterOrderByExecutedAtDesc(
        String optionChain, Instant since
    );

    Page<OptionTrade> findByTickerOrderByExecutedAtDesc(String ticker, Pageable pageable);

    List<OptionTrade> findByTradeIdIn(List<UUID> tradeIds);

    @Query("select max(t.executedAt) from OptionTrade t where t.executedAt >= :since")
    Instant findMaxExecutedAtSince(@Param("since") Instant since);
}
