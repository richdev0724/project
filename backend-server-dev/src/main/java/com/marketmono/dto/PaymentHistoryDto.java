package com.marketmono.dto;

import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.PaymentHistory;
import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.StripeMode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * User-facing payment history DTO.
 * Exposes invoice history created from Stripe webhook events.
 */
public record PaymentHistoryDto(
    String stripeInvoiceId,
    BigDecimal amount,
    String currency,
    PaymentStatus status,
    LocalDateTime paidAt,
    LocalDateTime periodStart,
    LocalDateTime periodEnd,
    String paymentMethodLast4,
    Integer paymentMethodExpMonth,
    Integer paymentMethodExpYear,
    StripeMode stripeMode,
    BillingProvider billingProvider,
    String providerPaymentId,
    String providerStatus,
    LocalDateTime createdAt
) {
    public static PaymentHistoryDto from(PaymentHistory paymentHistory) {
        return new PaymentHistoryDto(
            paymentHistory.getStripeInvoiceId(),
            paymentHistory.getAmount(),
            paymentHistory.getCurrency(),
            paymentHistory.getStatus(),
            paymentHistory.getPaidAt(),
            paymentHistory.getPeriodStart(),
            paymentHistory.getPeriodEnd(),
            paymentHistory.getPaymentMethodLast4(),
            paymentHistory.getPaymentMethodExpMonth(),
            paymentHistory.getPaymentMethodExpYear(),
            paymentHistory.getStripeMode(),
            paymentHistory.getBillingProvider(),
            paymentHistory.getProviderPaymentId(),
            paymentHistory.getProviderStatus(),
            paymentHistory.getCreatedAt()
        );
    }
}
