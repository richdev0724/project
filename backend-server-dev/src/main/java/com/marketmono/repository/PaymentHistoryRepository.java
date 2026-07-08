package com.marketmono.repository;

import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.PaymentHistory;
import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, String> {
    
    /**
     * Find all payment history for a specific user, ordered by payment date (newest first).
     */
    @Query("SELECT ph FROM PaymentHistory ph WHERE ph.user = :user ORDER BY ph.paidAt DESC")
    List<PaymentHistory> findByUserOrderByPaidAtDesc(@Param("user") User user);
    
    /**
     * Find payment history by Stripe invoice ID.
     */
    Optional<PaymentHistory> findByStripeInvoiceId(String stripeInvoiceId);
    
    /**
     * Check if payment history exists for a Stripe invoice ID.
     */
    boolean existsByStripeInvoiceId(String stripeInvoiceId);

    boolean existsByBillingProviderAndProviderPaymentId(
        BillingProvider billingProvider,
        String providerPaymentId
    );

    /**
     * Count successful payments for a user.
     */
    long countByUserIdAndStatus(UUID userId, PaymentStatus status);

    /**
     * Count payment-history records by Stripe environment and payment status for observability.
     */
    long countByStripeModeAndStatus(StripeMode stripeMode, PaymentStatus status);

    /**
     * Sum USD payment-history amounts by Stripe environment and payment status for observability.
     */
    @Query("""
        SELECT SUM(ph.amount)
        FROM PaymentHistory ph
        WHERE ph.stripeMode = :stripeMode
        AND ph.status = :status
        AND UPPER(ph.currency) = 'USD'
        """)
    BigDecimal sumUsdAmountByStripeModeAndStatus(
        @Param("stripeMode") StripeMode stripeMode,
        @Param("status") PaymentStatus status
    );
}
