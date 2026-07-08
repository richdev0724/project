package com.marketmono.controller;

import com.marketmono.config.security.InternalAdminPrincipal;
import com.marketmono.config.security.UserPrincipal;
import com.marketmono.dto.CancelSubscriptionRequest;
import com.marketmono.dto.AdminGrantSubscriptionRequest;
import com.marketmono.dto.AdminRevokeSubscriptionRequest;
import com.marketmono.dto.SubscriptionFeatureAccessDto;
import com.marketmono.dto.UserSubscriptionDto;
import com.marketmono.dto.DowngradeSubscriptionRequest;
import com.marketmono.dto.SubscriptionDto;
import com.marketmono.dto.UpgradeSubscriptionRequest;
import com.marketmono.entity.auth.User;
import com.marketmono.entity.BillingPeriod;
import com.marketmono.entity.BillingProvider;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.SubscriptionStatus;
import com.marketmono.service.SubscriptionFeatureService;
import com.marketmono.service.PaymentHistoryService;
import com.marketmono.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private PaymentHistoryService paymentHistoryService;

    @Mock
    private SubscriptionFeatureService subscriptionFeatureService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Test
    void shouldUpgradeSubscriptionThroughInternalAdminBridge() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpgradeSubscriptionRequest request = new UpgradeSubscriptionRequest(UUID.randomUUID(), BillingPeriod.ANNUAL);
        SubscriptionDto subscriptionDto = subscriptionDto(userId, SubscriptionStatus.ACTIVE, false, null);

        when(subscriptionService.upgradeSubscription(userId, request)).thenReturn(subscriptionDto);

        ResponseEntity<SubscriptionDto> response = subscriptionController.adminUpgradeSubscription(
            new InternalAdminPrincipal(adminId),
            userId,
            request
        );

        assertThat(response.getBody()).isEqualTo(subscriptionDto);
        verify(subscriptionService).upgradeSubscription(userId, request);
    }

    @Test
    void shouldDowngradeSubscriptionThroughInternalAdminBridge() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        DowngradeSubscriptionRequest request = new DowngradeSubscriptionRequest(UUID.randomUUID(), BillingPeriod.MONTHLY, "Requested by support");
        SubscriptionDto subscriptionDto = subscriptionDto(userId, SubscriptionStatus.ACTIVE, true, "Requested by support");

        when(subscriptionService.downgradeSubscription(userId, request)).thenReturn(subscriptionDto);

        ResponseEntity<SubscriptionDto> response = subscriptionController.adminDowngradeSubscription(
            new InternalAdminPrincipal(adminId),
            userId,
            request
        );

        assertThat(response.getBody()).isEqualTo(subscriptionDto);
        verify(subscriptionService).downgradeSubscription(userId, request);
    }

    @Test
    void shouldCancelAndReactivateSubscriptionThroughInternalAdminBridge() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CancelSubscriptionRequest cancelRequest = new CancelSubscriptionRequest("Support requested", true);
        SubscriptionDto cancelled = subscriptionDto(userId, SubscriptionStatus.CANCELED, false, "Support requested");
        SubscriptionDto reactivated = subscriptionDto(userId, SubscriptionStatus.ACTIVE, false, null);

        when(subscriptionService.cancelSubscription(userId, cancelRequest)).thenReturn(cancelled);
        when(subscriptionService.reactivateSubscription(userId)).thenReturn(reactivated);

        ResponseEntity<SubscriptionDto> cancelResponse = subscriptionController.adminCancelSubscription(
            new InternalAdminPrincipal(adminId),
            userId,
            cancelRequest
        );
        ResponseEntity<SubscriptionDto> reactivateResponse = subscriptionController.adminReactivateSubscription(
            new InternalAdminPrincipal(adminId),
            userId
        );

        assertThat(cancelResponse.getBody()).isEqualTo(cancelled);
        assertThat(reactivateResponse.getBody()).isEqualTo(reactivated);
        verify(subscriptionService).cancelSubscription(userId, cancelRequest);
        verify(subscriptionService).reactivateSubscription(userId);
    }

    @Test
    void shouldGrantAndRevokeSubscriptionThroughInternalAdminBridge() {
        UUID adminId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AdminGrantSubscriptionRequest grantRequest = new AdminGrantSubscriptionRequest(
            UUID.randomUUID(),
            BillingPeriod.MONTHLY,
            30,
            "QA grant"
        );
        AdminRevokeSubscriptionRequest revokeRequest = new AdminRevokeSubscriptionRequest("QA revoke");
        SubscriptionDto granted = subscriptionDto(userId, SubscriptionStatus.ACTIVE, false, null);
        SubscriptionDto revoked = subscriptionDto(userId, SubscriptionStatus.CANCELED, false, "QA revoke");

        when(subscriptionService.grantSubscription(userId, grantRequest)).thenReturn(granted);
        when(subscriptionService.revokeSubscription(userId, revokeRequest)).thenReturn(revoked);

        ResponseEntity<SubscriptionDto> grantResponse = subscriptionController.adminGrantSubscription(
            new InternalAdminPrincipal(adminId),
            userId,
            grantRequest
        );
        ResponseEntity<SubscriptionDto> revokeResponse = subscriptionController.adminRevokeSubscription(
            new InternalAdminPrincipal(adminId),
            userId,
            revokeRequest
        );

        assertThat(grantResponse.getBody()).isEqualTo(granted);
        assertThat(revokeResponse.getBody()).isEqualTo(revoked);
        verify(subscriptionService).grantSubscription(userId, grantRequest);
        verify(subscriptionService).revokeSubscription(userId, revokeRequest);
    }

    @Test
    void shouldRejectInternalAdminRequestWithoutActorId() {
        UUID userId = UUID.randomUUID();
        UpgradeSubscriptionRequest request = new UpgradeSubscriptionRequest(UUID.randomUUID(), BillingPeriod.MONTHLY);

        assertThatThrownBy(() -> subscriptionController.adminUpgradeSubscription(
            new InternalAdminPrincipal(null),
            userId,
            request
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("admin actor ID");
    }

    @Test
    void shouldReturnSubscriptionStatusWithFeatureAccess() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("featuretest@example.com")
            .enabled(true)
            .emailVerified(true)
            .build();

        UserSubscriptionDto userSubscription = new UserSubscriptionDto(
            UUID.randomUUID(),
            SubscriptionStatus.ACTIVE,
            BillingPeriod.MONTHLY,
            null,
            false,
            null,
            BillingProvider.STRIPE,
            null,
            UUID.randomUUID(),
            "Market Mono Monthly",
            "market-mono-monthly",
            null,
            null,
            true,
            true,
            false,
            false,
            30L,
            null,
            null
        );
        SubscriptionFeatureAccessDto featureAccess = SubscriptionFeatureAccessDto.premium();

        when(subscriptionService.hasActiveSubscription(userId)).thenReturn(true);
        when(subscriptionService.getUserActiveSubscription(userId)).thenReturn(userSubscription);
        when(subscriptionFeatureService.getFeatureAccess(userSubscription)).thenReturn(featureAccess);

        ResponseEntity<SubscriptionController.SubscriptionStatusResponse> response =
            subscriptionController.getMySubscriptionStatus(new UserPrincipal(user));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().hasActiveSubscription()).isTrue();
        assertThat(response.getBody().hasAccess()).isTrue();
        assertThat(response.getBody().featureAccess()).isEqualTo(featureAccess);
    }

    @Test
    void shouldAcceptDirectUserForUserSubscriptionFlow() {
        UUID userId = UUID.randomUUID();
        UserDetails user = userDetailsBackedByUser(userId);

        subscriptionController.getMyPaymentHistory(user);

        verify(paymentHistoryService).getUserPaymentHistory(userId);
    }

    @Test
    void shouldRejectInternalAdminPrincipalForUserSubscriptionFlow() {
        assertThatThrownBy(() -> subscriptionController.getMyPaymentHistory(new InternalAdminPrincipal(UUID.randomUUID())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unable to extract user ID");
    }

    private SubscriptionDto subscriptionDto(
            UUID userId,
            SubscriptionStatus status,
            boolean cancelAtPeriodEnd,
            String cancellationReason) {
        return new SubscriptionDto(
            UUID.randomUUID(),
            userId,
            UUID.randomUUID(),
            "Pro",
            "sub_test_123",
            "cus_test_123",
            StripeMode.TEST,
            BillingProvider.STRIPE,
            "sub_test_123",
            "cus_test_123",
            null,
            null,
            null,
            null,
            null,
            status,
            BillingPeriod.MONTHLY,
            null,
            null,
            null,
            cancelAtPeriodEnd,
            null,
            cancellationReason,
            null,
            null,
            null,
            "USD",
            null,
            null,
            null,
            null,
            null,
            status == SubscriptionStatus.ACTIVE,
            status == SubscriptionStatus.ACTIVE,
            false,
            cancelAtPeriodEnd,
            0L,
            false,
            false
        );
    }

    private static UserDetails userDetailsBackedByUser(UUID userId) {
        User user = mock(User.class, withSettings().extraInterfaces(UserDetails.class));
        when(user.getId()).thenReturn(userId);
        return (UserDetails) user;
    }
}
