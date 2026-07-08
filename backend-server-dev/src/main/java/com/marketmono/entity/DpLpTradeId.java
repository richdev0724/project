package com.marketmono.entity;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Composite primary key for DpLpTrade (id, ts).
 * Required by TimescaleDB: the time dimension must be part of any unique constraint/PK.
 */
public class DpLpTradeId implements Serializable {

    private Long id;
    private OffsetDateTime ts;

    public DpLpTradeId() {}

    public DpLpTradeId(Long id, OffsetDateTime ts) {
        this.id = id;
        this.ts = ts;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DpLpTradeId that = (DpLpTradeId) o;
        return Objects.equals(id, that.id) && Objects.equals(ts, that.ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, ts);
    }
}
