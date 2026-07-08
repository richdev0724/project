package com.marketmono.service;

import com.marketmono.entity.PaymentHistory;
import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.auth.UserRepository;
import com.stripe.exception.StripeException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentMethod;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Service for processing Stripe webhook events.
 * Handles subscription lifecycle events and invoice events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {
    
    private final StripeConfigService stripeConfigService;
    private final ProductSyncService productSyncService;
    private final SubscriptionSyncService subscriptionSyncService;
    private final ReferralService referralService;
    private final PaymentHistoryRepository paymentHistoryRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    
    /**
     * Process a Stripe webhook event.
     * Verifies signature and routes to appropriate handler based on event type.
     * 
     * @param payload The raw JSON payload from Stripe
     * @param signatureHeader The Stripe-Signature header
     * @throws SignatureVerificationException if signature verification fails
     */
    @Transactional
    public void handleWebhook(String payload, String signatureHeader) throws SignatureVerificationException {
        Event event = verifyAndConstructEvent(payload, signatureHeader);
        
        log.info("Processing Stripe webhook: {} ({})", event.getType(), event.getId());
        
        // Route to handler based on event type
        switch (event.getType()) {
            // Product events (for syncing product catalog)
            case "product.created" -> handleProductCreated(event);
            case "product.updated" -> handleProductUpdated(event);
            case "product.deleted" -> handleProductDeleted(event);
            
            // Price events (for syncing pricing information)
            case "price.created" -> handlePriceCreated(event);
            case "price.updated" -> handlePriceUpdated(event);

            // Checkout events
            case "checkout.session.completed" -> handleCheckoutSessionCompleted(
                (Session) getEventDataObject(event),
                event.getLivemode()
            );
            
            // Subscription lifecycle events
            case "customer.subscription.created" -> handleSubscriptionCreated(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "customer.subscription.trial_will_end" -> handleSubscriptionTrialWillEnd(event);
            
            // Invoice events
            case "invoice.paid" -> handleInvoicePaid(event);
            case "invoice.payment_failed" -> handleInvoicePaymentFailed(event);
            case "invoice.payment_action_required" -> handleInvoicePaymentActionRequired(event);
            
            // Log unhandled events
            default -> log.debug("Unhandled webhook event type: {}", event.getType());
        }
    }
    
    /**
     * Verify webhook signature and construct Event object.
     * IMPORTANT: Uses getWebhookSecret() which returns UNMASKED secrets,
     * NOT getConfig() which returns MASKED secrets for admin display.
     */
    private Event verifyAndConstructEvent(String payload, String signatureHeader) throws SignatureVerificationException {
        // Try TEST mode webhook secret first
        try {
            String testSecret = stripeConfigService.getWebhookSecret(StripeMode.TEST);
            if (testSecret != null && !testSecret.isBlank()) {
                return Webhook.constructEvent(payload, signatureHeader, testSecret, 300);
            }
        } catch (SignatureVerificationException e) {
            log.debug("TEST mode webhook signature verification failed, trying PROD mode");
        }
        
        // Try PROD mode webhook secret
        String prodSecret = stripeConfigService.getWebhookSecret(StripeMode.PROD);
        if (prodSecret != null && !prodSecret.isBlank()) {
            return Webhook.constructEvent(payload, signatureHeader, prodSecret, 300);
        }
        
        throw new SignatureVerificationException(
            "No webhook secret configured or signature verification failed", 
            signatureHeader
        );
    }
    
    /**
     * Extract and deserialize event data object.
     */
    private StripeObject getEventDataObject(Event event) {
        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isEmpty()) {
            throw new IllegalStateException("Event data object is missing");
        }
        return dataObjectDeserializer.getObject().get();
    }
    
    // ============================================
    // Product & Price Event Handlers
    // ============================================
    
    private void handleProductCreated(Event event) {
        com.stripe.model.Product product = (com.stripe.model.Product) getEventDataObject(event);
        log.info("Product created: {} - {}", product.getId(), product.getName());
        
        productSyncService.syncProduct(product);
    }
    
    private void handleProductUpdated(Event event) {
        com.stripe.model.Product product = (com.stripe.model.Product) getEventDataObject(event);
        log.info("Product updated: {} - {}", product.getId(), product.getName());
        
        productSyncService.syncProduct(product);
    }
    
    private void handleProductDeleted(Event event) {
        com.stripe.model.Product product = (com.stripe.model.Product) getEventDataObject(event);
        log.info("Product deleted: {}", product.getId());

        productSyncService.archiveProduct(product.getId());
    }
    
    private void handlePriceCreated(Event event) {
        com.stripe.model.Price price = (com.stripe.model.Price) getEventDataObject(event);
        log.info("Price created: {} for product: {} - {}/{}", 
            price.getId(), price.getProduct(), 
            price.getUnitAmount(), price.getCurrency());
        
        productSyncService.syncPrice(price);
    }
    
    private void handlePriceUpdated(Event event) {
        com.stripe.model.Price price = (com.stripe.model.Price) getEventDataObject(event);
        log.info("Price updated: {} for product: {}", 
            price.getId(), price.getProduct());
        
        productSyncService.syncPrice(price);
    }
    
    // ============================================
    // Subscription Event Handlers
    // ============================================
    
    private void handleSubscriptionCreated(Event event) {
        Subscription eventSubscription = (Subscription) getEventDataObject(event);
        Subscription subscription = retrieveSubscription(eventSubscription.getId(), event.getLivemode());
        log.info("Subscription created: {} for customer: {}", 
            subscription.getId(), subscription.getCustomer());
        
        subscriptionSyncService.syncSubscription(subscription);
    }
    
    private void handleSubscriptionUpdated(Event event) {
        Subscription eventSubscription = (Subscription) getEventDataObject(event);
        Subscription subscription = retrieveSubscription(eventSubscription.getId(), event.getLivemode());
        log.info("Subscription updated: {} - status: {}", 
            subscription.getId(), subscription.getStatus());
        
        subscriptionSyncService.syncSubscription(subscription);
    }
    
    private void handleSubscriptionDeleted(Event event) {
        Subscription subscription = (Subscription) getEventDataObject(event);
        log.info("Subscription deleted: {} for customer: {}", 
            subscription.getId(), subscription.getCustomer());
        
        subscriptionSyncService.cancelSubscription(subscription.getId());
    }
    
    private void handleSubscriptionTrialWillEnd(Event event) {
        Subscription subscription = (Subscription) getEventDataObject(event);
        log.info("Subscription trial ending soon: {} for customer: {}", 
            subscription.getId(), subscription.getCustomer());
        
        // TODO: Send notification to user about trial ending
        // - Find user by stripe_customer_id
        // - Send email notification
        // - Update subscription trial_end date
    }
    
    // ============================================
    // Invoice Event Handlers
    // ============================================
    
    private void handleInvoicePaid(Event event) {
        Invoice eventInvoice = (Invoice) getEventDataObject(event);
        Invoice invoice = retrieveInvoice(eventInvoice.getId(), event.getLivemode());
        log.info("Invoice paid: {} for customer: {} - amount: {}", 
            invoice.getId(), invoice.getCustomer(), invoice.getAmountPaid());
        
        ensureSubscriptionSyncedFromInvoice(invoice, event.getLivemode());

        // Update subscription status
        subscriptionSyncService.updateInvoiceStatus(invoice);
        
        // Create payment history record
        createPaymentHistory(invoice, PaymentStatus.PAID, event.getLivemode());
    }
    
    private void handleInvoicePaymentFailed(Event event) {
        Invoice eventInvoice = (Invoice) getEventDataObject(event);
        Invoice invoice = retrieveInvoice(eventInvoice.getId(), event.getLivemode());
        log.warn("Invoice payment failed: {} for customer: {} - attempt: {}", 
            invoice.getId(), invoice.getCustomer(), invoice.getAttemptCount());
        
        ensureSubscriptionSyncedFromInvoice(invoice, event.getLivemode());

        // Update subscription status
        subscriptionSyncService.updateInvoiceStatus(invoice);
        
        // Create payment history record
        createPaymentHistory(invoice, PaymentStatus.FAILED, event.getLivemode());
    }
    
    private void handleInvoicePaymentActionRequired(Event event) {
        Invoice eventInvoice = (Invoice) getEventDataObject(event);
        Invoice invoice = retrieveInvoice(eventInvoice.getId(), event.getLivemode());
        log.info("Invoice requires payment action: {} for customer: {}", 
            invoice.getId(), invoice.getCustomer());
        
        // TODO: Handle 3DS or other payment actions
        // - Find user by stripe_customer_id
        // - Send notification with payment action URL
        // - Update subscription status to REQUIRES_ACTION
    }
    
    // ============================================
    // Payment History Helper
    // ============================================

    void handleCheckoutSessionCompleted(Session session, Boolean livemode) {
        if (session == null) {
            return;
        }

        if (session.getMode() != null && !"subscription".equalsIgnoreCase(session.getMode())) {
            log.debug("Ignoring checkout session {} with mode {}", session.getId(), session.getMode());
            return;
        }

        String subscriptionId = session.getSubscription();
        if (subscriptionId == null || subscriptionId.isBlank()) {
            log.warn("Checkout session {} completed without a subscription id", session.getId());
            return;
        }

        syncCheckoutSubscription(subscriptionId, livemode);
    }

    void syncCheckoutSubscription(String subscriptionId, Boolean livemode) {
        Subscription subscription = retrieveSubscription(subscriptionId, livemode);
        subscriptionSyncService.syncSubscription(subscription);
    }

    void ensureSubscriptionSyncedFromInvoice(Invoice invoice, Boolean livemode) {
        String subscriptionId = subscriptionSyncService.resolveSubscriptionIdFromInvoice(invoice);
        if (subscriptionId == null || subscriptionId.isBlank()) {
            return;
        }

        if (subscriptionRepository.findByStripeSubscriptionId(subscriptionId).isPresent()) {
            return;
        }

        log.info("Subscription {} missing locally before invoice {}; syncing from Stripe",
            subscriptionId,
            invoice != null ? invoice.getId() : null);
        syncCheckoutSubscription(subscriptionId, livemode);
    }

    Subscription retrieveSubscription(String subscriptionId, Boolean livemode) {
        try {
            return Subscription.retrieve(subscriptionId, buildRequestOptions(livemode));
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to retrieve Stripe subscription: " + subscriptionId, e);
        }
    }

    private Invoice retrieveInvoice(String invoiceId, Boolean livemode) {
        try {
            return Invoice.retrieve(invoiceId, buildRequestOptions(livemode));
        } catch (StripeException e) {
            throw new IllegalStateException("Failed to retrieve Stripe invoice: " + invoiceId, e);
        }
    }

    private RequestOptions buildRequestOptions(Boolean livemode) {
        StripeMode mode = Boolean.TRUE.equals(livemode) ? StripeMode.PROD : StripeMode.TEST;
        return RequestOptions.builder()
            .setApiKey(stripeConfigService.getSecretKey(mode))
            .build();
    }
    
    /**
     * Create payment history record from invoice webhook event.
     */
    void createPaymentHistory(Invoice invoice, PaymentStatus status, Boolean livemode) {
        try {
            // Check if payment history already exists
            if (paymentHistoryRepository.existsByStripeInvoiceId(invoice.getId())) {
                log.debug("Payment history already exists for invoice: {}", invoice.getId());
                return;
            }
            
            // Find user by stripe_customer_id
            String stripeCustomerId = invoice.getCustomer();
            Optional<User> userOpt = userRepository.findByStripeCustomerIdTestOrStripeCustomerIdProd(
                stripeCustomerId, stripeCustomerId
            );
            if (userOpt.isEmpty()) {
                log.warn("User not found for stripe_customer_id: {}", stripeCustomerId);
                return;
            }
            User user = userOpt.get();
            
            String stripeSubscriptionId = subscriptionSyncService.resolveSubscriptionIdFromInvoice(invoice);
            if (stripeSubscriptionId == null) {
                log.warn("Invoice {} has no subscription", invoice.getId());
                return;
            }
            
            Optional<com.marketmono.entity.Subscription> subscriptionOpt = 
                subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId);
            if (subscriptionOpt.isEmpty()) {
                log.warn("Subscription not found for stripe_subscription_id: {}", stripeSubscriptionId);
                return;
            }
            com.marketmono.entity.Subscription subscription = subscriptionOpt.get();
            
            // Extract payment method details
            String paymentMethodLast4 = null;
            Integer paymentMethodExpMonth = null;
            Integer paymentMethodExpYear = null;
            
            // Access payment_intent from raw JSON (Stripe SDK v30+ doesn't expose getPaymentIntent() directly)
            String paymentIntentId = null;
            var rawJson = invoice.getRawJsonObject();
            if (rawJson != null && rawJson.has("payment_intent") && !rawJson.get("payment_intent").isJsonNull()) {
                paymentIntentId = rawJson.get("payment_intent").getAsString();
            }
            
            if (paymentIntentId != null) {
                try {
                    // Use per-request API key based on livemode (thread-safe)
                    StripeMode mode = Boolean.TRUE.equals(livemode) ? StripeMode.PROD : StripeMode.TEST;
                    RequestOptions requestOptions = RequestOptions.builder()
                        .setApiKey(stripeConfigService.getSecretKey(mode))
                        .build();

                    com.stripe.model.PaymentIntent paymentIntent = 
                        com.stripe.model.PaymentIntent.retrieve(paymentIntentId, requestOptions);
                    
                    // paymentIntent.getPaymentMethod() returns String ID
                    String paymentMethodId = paymentIntent.getPaymentMethod();
                    
                    if (paymentMethodId != null) {
                        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId, requestOptions);
                        
                        if (paymentMethod.getCard() != null) {
                            paymentMethodLast4 = paymentMethod.getCard().getLast4();
                            paymentMethodExpMonth = Math.toIntExact(paymentMethod.getCard().getExpMonth());
                            paymentMethodExpYear = Math.toIntExact(paymentMethod.getCard().getExpYear());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to retrieve payment method details: {}", e.getMessage());
                }
            }
            
            // Convert Stripe timestamps to LocalDateTime
            LocalDateTime paidAt = status == PaymentStatus.PAID
                && invoice.getStatusTransitions() != null
                && invoice.getStatusTransitions().getPaidAt() != null
                ? LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(invoice.getStatusTransitions().getPaidAt()), 
                    ZoneId.systemDefault()
                )
                : null;

            SubscriptionSyncService.InvoiceCoveragePeriod coveragePeriod =
                subscriptionSyncService.resolveInvoiceCoveragePeriod(invoice);
            if (coveragePeriod == null) {
                log.warn("Invoice {} has no billing coverage period", invoice.getId());
                return;
            }
            
            // Create payment history entity
            PaymentHistory paymentHistory = PaymentHistory.builder()
                .stripeInvoiceId(invoice.getId())
                .user(user)
                .subscription(subscription)
                .stripePaymentIntentId(paymentIntentId)
                .amount(BigDecimal.valueOf(invoice.getAmountPaid()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP))
                .currency(invoice.getCurrency().toUpperCase())
                .status(status)
                .paidAt(paidAt)
                .periodStart(coveragePeriod.start())
                .periodEnd(coveragePeriod.end())
                .paymentMethodLast4(paymentMethodLast4)
                .paymentMethodExpMonth(paymentMethodExpMonth)
                .paymentMethodExpYear(paymentMethodExpYear)
                .stripeMode(Boolean.TRUE.equals(livemode) ? StripeMode.PROD : StripeMode.TEST)
                .build();
            
            paymentHistoryRepository.save(paymentHistory);

            referralService.qualifyReferral(user, subscription, invoice.getId());
            
            log.info("Payment history created: {} - {} - {}", 
                invoice.getId(), status, paymentHistory.getAmount());
            
        } catch (Exception e) {
            log.error("Failed to create payment history for invoice {}: {}", 
                invoice.getId(), e.getMessage(), e);
        }
    }
}
