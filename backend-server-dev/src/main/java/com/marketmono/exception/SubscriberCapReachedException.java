package com.marketmono.exception;

public class SubscriberCapReachedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int capLimit;
    private final long paidSubscriberCount;
    private final long pendingReservationCount;

    public SubscriberCapReachedException(int capLimit, long paidSubscriberCount, long pendingReservationCount) {
        super("The paid subscriber cap has been reached");
        this.capLimit = capLimit;
        this.paidSubscriberCount = paidSubscriberCount;
        this.pendingReservationCount = pendingReservationCount;
    }

    public int capLimit() {
        return capLimit;
    }

    public long paidSubscriberCount() {
        return paidSubscriberCount;
    }

    public long pendingReservationCount() {
        return pendingReservationCount;
    }
}
