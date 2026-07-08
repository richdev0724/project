package com.marketmono.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for OptionTrade (trade_id, executed_at).
 * Required by TimescaleDB: the time dimension must be part of any unique constraint/PK.
 */
public class OptionTradeId implements Serializable {

    private UUID tradeId;
    private Instant executedAt;

    public OptionTradeId() {}

    public OptionTradeId(UUID tradeId, Instant executedAt) {
        this.tradeId = tradeId;
        this.executedAt = executedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OptionTradeId that = (OptionTradeId) o;
        return Objects.equals(tradeId, that.tradeId) && Objects.equals(executedAt, that.executedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId, executedAt);
    }
}
