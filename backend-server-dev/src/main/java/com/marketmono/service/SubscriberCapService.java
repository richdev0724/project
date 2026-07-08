package com.marketmono.service;

import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.SubscriberCapReservation;
import com.marketmono.entity.SubscriberCapReservationStatus;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRoleType;
import com.marketmono.exception.SubscriberCapReachedException;
import com.marketmono.repository.SubscriptionRepository;
import com.marketmono.repository.SubscriberCapReservationRepository;
import com.stripe.model.Subscription;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriberCapService {

    public static final String RESERVATION_METADATA_KEY = "subscriber_cap_reservation_id";

    private static final int DEFAULT_CAP_LIMIT = 100;

    private final StripeConfigService stripeConfigService;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriberCapReservationRepository reservationRepository;

    @Transactional
    public Optional<SubscriberCapReservation> reserveCheckoutSlot(
            User user,
            StripeMode stripeMode,
            String priceId) {
        if (stripeMode != StripeMode.PROD || !isCapEnabled() || isExcludedFromPaidCap(user)) {
            return Optional.empty();
        }

        if (subscriptionRepository.existsRealPaidSubscriberByUserId(user.getId())) {
            return Optional.empty();
        }

        reservationRepository.lockLaunchCapScope();
        int limit = subscriberCapLimit();
        long paidCount = subscriptionRepository.countRealPaidSubscribersForLaunchCap();
        long pendingCount = reservationRepository.countPendingReservationsForLaunchCap(LocalDateTime.now());

        if (paidCount + pendingCount >= limit) {
            throw new SubscriberCapReachedException(limit, paidCount, pendingCount);
        }

        return Optional.of(reservationRepository.save(
            SubscriberCapReservation.pending(user, priceId, stripeMode, limit)
        ));
    }

    @Transactional
    public Optional<SubscriberCapReservation> reserveWhopCheckoutSlot(User user, String priceId) {
        if (!isCapEnabled() || isExcludedFromPaidCap(user)) {
            return Optional.empty();
        }

        if (subscriptionRepository.existsRealPaidSubscriberByUserId(user.getId())) {
            return Optional.empty();
        }

        reservationRepository.lockLaunchCapScope();
        int limit = subscriberCapLimit();
        long paidCount = subscriptionRepository.countRealPaidSubscribersForLaunchCap();
        long pendingCount = reservationRepository.countPendingReservationsForLaunchCap(LocalDateTime.now());

        if (paidCount + pendingCount >= limit) {
            throw new SubscriberCapReachedException(limit, paidCount, pendingCount);
        }

        return Optional.of(reservationRepository.save(
            SubscriberCapReservation.pendingWhop(user, priceId, limit)
        ));
    }

    @Transactional
    public void markStripeSessionCreated(SubscriberCapReservation reservation, String stripeSessionId) {
        if (reservation == null) {
            return;
        }
        reservation.markStripeSessionCreated(stripeSessionId);
        reservationRepository.save(reservation);
    }

    @Transactional
    public void markWhopCheckoutCreated(SubscriberCapReservation reservation, String whopCheckoutConfigurationId) {
        if (reservation == null) {
            return;
        }
        reservation.markWhopCheckoutCreated(whopCheckoutConfigurationId);
        reservationRepository.save(reservation);
    }

    @Transactional
    public void consumeWhopReservation(SubscriberCapReservation reservation, String whopMembershipId) {
        if (reservation == null || !reservation.isPending()) {
            return;
        }
        reservation.consumeWhopMembership(whopMembershipId);
        reservationRepository.save(reservation);
    }

    @Transactional
    public void releaseReservation(SubscriberCapReservation reservation) {
        if (reservation == null || !reservation.isPending()) {
            return;
        }
        reservation.release();
        reservationRepository.save(reservation);
    }

    @Transactional
    public boolean canSyncStripeSubscription(Subscription stripeSubscription, User user) {
        StripeMode mode = stripeSubscription != null && Boolean.TRUE.equals(stripeSubscription.getLivemode())
            ? StripeMode.PROD
            : StripeMode.TEST;
        if (mode != StripeMode.PROD || !isCapEnabled() || isExcludedFromPaidCap(user)) {
            return true;
        }

        if (!"active".equalsIgnoreCase(stripeSubscription.getStatus())) {
            return true;
        }

        Optional<SubscriberCapReservation> reservation = findValidReservation(stripeSubscription, user);
        if (reservation.isPresent()) {
            return true;
        }

        if (subscriptionRepository.existsRealPaidSubscriberByUserId(user.getId())) {
            return true;
        }

        reservationRepository.lockLaunchCapScope();
        int limit = subscriberCapLimit();
        long paidCount = subscriptionRepository.countRealPaidSubscribersForLaunchCap();
        long pendingCount = reservationRepository.countPendingReservationsForLaunchCap(LocalDateTime.now());
        return paidCount + pendingCount < limit;
    }

    @Transactional
    public void consumeReservationFromStripeSubscription(Subscription stripeSubscription, User user) {
        if (!isActivePaidStripeSubscription(stripeSubscription)) {
            return;
        }

        findValidReservation(stripeSubscription, user).ifPresent(reservation -> {
            reservation.consume(stripeSubscription.getId());
            reservationRepository.save(reservation);
        });
    }

    @Transactional(readOnly = true)
    public long currentPaidSubscriberCount() {
        return subscriptionRepository.countRealPaidSubscribersForLaunchCap();
    }

    @Transactional(readOnly = true)
    public long currentPendingReservationCount() {
        return reservationRepository.countPendingReservationsForLaunchCap(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public boolean isCapEnabled() {
        return Boolean.parseBoolean(stripeConfigService.getValue(StripeConfig.SUBSCRIBER_CAP_ENABLED, "false"));
    }

    @Transactional(readOnly = true)
    public int subscriberCapLimit() {
        return Math.max(0, stripeConfigService.getValueAsInt(
            StripeConfig.SUBSCRIBER_CAP_LIMIT,
            DEFAULT_CAP_LIMIT
        ));
    }

    private Optional<SubscriberCapReservation> findValidReservation(Subscription stripeSubscription, User user) {
        UUID reservationId = extractReservationId(stripeSubscription);
        if (reservationId == null) {
            return Optional.empty();
        }

        Optional<SubscriberCapReservation> reservation =
            reservationRepository.findByIdAndStatus(reservationId, SubscriberCapReservationStatus.PENDING);
        if (reservation.isEmpty()) {
            return Optional.empty();
        }

        SubscriberCapReservation value = reservation.get();
        if (value.isExpired(LocalDateTime.now())) {
            return Optional.empty();
        }

        if (value.getUser() == null || !value.getUser().getId().equals(user.getId())) {
            log.warn("Ignoring subscriber cap reservation {} because user does not match Stripe subscription {}",
                reservationId,
                stripeSubscription.getId());
            return Optional.empty();
        }

        return reservation;
    }

    private UUID extractReservationId(Subscription stripeSubscription) {
        if (stripeSubscription == null) {
            return null;
        }
        Map<String, String> metadata = stripeSubscription.getMetadata();
        String rawReservationId = metadata != null ? metadata.get(RESERVATION_METADATA_KEY) : null;
        if (rawReservationId == null || rawReservationId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawReservationId);
        } catch (IllegalArgumentException ex) {
            log.warn("Ignoring malformed subscriber cap reservation id on Stripe subscription {}",
                stripeSubscription.getId());
            return null;
        }
    }

    private boolean isActivePaidStripeSubscription(Subscription stripeSubscription) {
        return stripeSubscription != null
            && Boolean.TRUE.equals(stripeSubscription.getLivemode())
            && "active".equalsIgnoreCase(stripeSubscription.getStatus());
    }

    private boolean isExcludedFromPaidCap(User user) {
        return user == null
            || Boolean.TRUE.equals(user.getBetaUser())
            || user.hasRole(UserRoleType.ADMIN);
    }
}
