package com.marketmono.entity;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key for FlowAlert (alert_id, trade_start_time).
 * Required by TimescaleDB: the time dimension must be part of any unique constraint/PK.
 */
public class FlowAlertId implements Serializable {

    private UUID alertId;
    private Instant tradeStartTime;

    public FlowAlertId() {}

    public FlowAlertId(UUID alertId, Instant tradeStartTime) {
        this.alertId = alertId;
        this.tradeStartTime = tradeStartTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlowAlertId that = (FlowAlertId) o;
        return Objects.equals(alertId, that.alertId) && Objects.equals(tradeStartTime, that.tradeStartTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alertId, tradeStartTime);
    }
}
