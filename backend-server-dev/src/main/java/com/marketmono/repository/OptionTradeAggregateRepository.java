package com.marketmono.repository;

import com.marketmono.entity.OptionTradeAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface OptionTradeAggregateRepository extends JpaRepository<OptionTradeAggregate, UUID>,
    JpaSpecificationExecutor<OptionTradeAggregate> {

    @Query("select max(a.lastExecutedAt) from OptionTradeAggregate a where a.lastExecutedAt >= :since")
    Instant findMaxLastExecutedAtSince(@Param("since") Instant since);

    boolean existsByLastExecutedAtGreaterThanEqualAndLastExecutedAtLessThan(
        Instant since,
        Instant until
    );
}
