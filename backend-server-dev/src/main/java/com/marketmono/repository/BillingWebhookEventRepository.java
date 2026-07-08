package com.marketmono.repository;

import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.BillingWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingWebhookEventRepository extends JpaRepository<BillingWebhookEvent, UUID> {

    Optional<BillingWebhookEvent> findByBillingProviderAndExternalEventId(
        BillingProvider billingProvider,
        String externalEventId
    );
}
