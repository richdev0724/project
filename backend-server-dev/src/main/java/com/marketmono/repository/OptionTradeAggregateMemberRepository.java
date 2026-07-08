package com.marketmono.repository;

import com.marketmono.entity.OptionTradeAggregateMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OptionTradeAggregateMemberRepository extends JpaRepository<OptionTradeAggregateMember, Long> {

    List<OptionTradeAggregateMember> findByAggregateIdOrderByMemberOrderAsc(UUID aggregateId);

    List<OptionTradeAggregateMember> findByTradeIdIn(List<UUID> tradeIds);

    @Query("""
        select m.aggregateId as aggregateId, t.volume as volume
        from OptionTradeAggregateMember m
        join OptionTrade t on t.tradeId = m.tradeId and t.executedAt = m.executedAt
        where m.aggregateId in :aggregateIds
          and t.volume is not null
        """)
    List<AggregateFallbackVolume> findFallbackVolumesByAggregateIdIn(@Param("aggregateIds") List<UUID> aggregateIds);

    interface AggregateFallbackVolume {
        UUID getAggregateId();

        Long getVolume();
    }
}
