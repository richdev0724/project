package com.marketmono.service;

import com.marketmono.dto.AdminGrantSubscriptionRequest;
import com.marketmono.entity.HonorCode;
import com.marketmono.entity.HonorCodeStatus;
import com.marketmono.entity.StripeConfig;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.HonorCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HonorCodeServiceTest {

    @Mock
    private HonorCodeRepository honorCodeRepository;

    @Mock
    private StripeConfigService stripeConfigService;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private HonorCodeService honorCodeService;

    @Test
    void shouldAcceptActiveHonorCodeForValidation() {
        HonorCode honorCode = HonorCode.builder()
            .code("HONOR-TEST01")
            .status(HonorCodeStatus.ACTIVE)
            .build();

        when(honorCodeRepository.findByCode("HONOR-TEST01")).thenReturn(Optional.of(honorCode));
        when(honorCodeRepository.save(any(HonorCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = honorCodeService.validate("honor-test01");

        assertThat(response.valid()).isTrue();
        assertThat(response.promoCode()).isEqualTo("HONOR-TEST01");
        assertThat(honorCode.getValidationAttempts()).isEqualTo(1);
    }

    @Test
    void shouldRejectRedeemedHonorCodeForValidation() {
        HonorCode honorCode = HonorCode.builder()
            .code("HONOR-USED01")
            .status(HonorCodeStatus.REDEEMED)
            .build();

        when(honorCodeRepository.findByCode("HONOR-USED01")).thenReturn(Optional.of(honorCode));

        var response = honorCodeService.validate("HONOR-USED01");

        assertThat(response.valid()).isFalse();
        assertThat(response.message()).contains("already been used");
        verify(honorCodeRepository, never()).save(any());
    }

    @Test
    void shouldRedeemHonorCodeAndGrantComplimentarySubscription() {
        UUID productId = UUID.randomUUID();
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("hero@example.com")
            .build();
        user.setEmail("hero@example.com");

        HonorCode honorCode = HonorCode.builder()
            .code("HONOR-REDEEM1")
            .status(HonorCodeStatus.ACTIVE)
            .build();

        when(honorCodeRepository.findByCodeForUpdate("HONOR-REDEEM1"))
            .thenReturn(Optional.of(honorCode));
        when(honorCodeRepository.save(any(HonorCode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(stripeConfigService.getValue(StripeConfig.HONOR_COMPLIMENTARY_PRODUCT_ID, null))
            .thenReturn(productId.toString());
        when(stripeConfigService.getValue(StripeConfig.HONOR_GRANT_DURATION_DAYS, "3660"))
            .thenReturn("3660");

        honorCodeService.redeemForUser("HONOR-REDEEM1", user);

        assertThat(user.getHonorUser()).isTrue();
        assertThat(honorCode.getStatus()).isEqualTo(HonorCodeStatus.REDEEMED);
        assertThat(honorCode.getRedeemedAt()).isNotNull();
        assertThat(honorCode.getRedeemedByUser()).isSameAs(user);
        verify(subscriptionService).grantSubscription(
            user.getId(),
            new AdminGrantSubscriptionRequest(
                productId,
                com.marketmono.entity.BillingPeriod.MONTHLY,
                3660,
                "HONOR program complimentary access"
            )
        );
    }

    @Test
    void shouldRejectEmailBoundHonorCodeWhenEmailDoesNotMatch() {
        User user = User.builder()
            .id(UUID.randomUUID())
            .email("other@example.com")
            .build();
        user.setEmail("other@example.com");

        HonorCode honorCode = HonorCode.builder()
            .code("HONOR-BOUND1")
            .status(HonorCodeStatus.ACTIVE)
            .email("hero@example.com")
            .build();

        when(honorCodeRepository.findByCodeForUpdate("HONOR-BOUND1"))
            .thenReturn(Optional.of(honorCode));

        assertThatThrownBy(() -> honorCodeService.redeemForUser("HONOR-BOUND1", user))
            .isInstanceOf(HonorCodeException.class)
            .hasMessageContaining("email address");
    }
}
