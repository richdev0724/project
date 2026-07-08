package com.marketmono.service;

import com.marketmono.entity.PaymentHistory;
import com.marketmono.entity.PaymentStatus;
import com.marketmono.entity.StripeMode;
import com.marketmono.entity.Subscription;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentHistoryServiceTest {

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PaymentHistoryService paymentHistoryService;

    @Test
    void shouldReturnMappedUserPaymentHistory() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("billing@marketmono.com")
            .emailLowercase("billing@marketmono.com")
            .build();

        PaymentHistory paymentHistory = PaymentHistory.builder()
            .stripeInvoiceId("in_123")
            .user(user)
            .subscription(new Subscription())
            .amount(new BigDecimal("49.99"))
            .currency("usd")
            .status(PaymentStatus.PAID)
            .paidAt(LocalDateTime.of(2026, 3, 15, 10, 30))
            .periodStart(LocalDateTime.of(2026, 3, 1, 0, 0))
            .periodEnd(LocalDateTime.of(2026, 3, 31, 23, 59))
            .paymentMethodLast4("4242")
            .paymentMethodExpMonth(12)
            .paymentMethodExpYear(2027)
            .stripeMode(StripeMode.TEST)
            .createdAt(LocalDateTime.of(2026, 3, 15, 10, 31))
            .updatedAt(LocalDateTime.of(2026, 3, 15, 10, 31))
            .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(paymentHistoryRepository.findByUserOrderByPaidAtDesc(user))
            .thenReturn(List.of(paymentHistory));

        var result = paymentHistoryService.getUserPaymentHistory(userId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().stripeInvoiceId()).isEqualTo("in_123");
        assertThat(result.getFirst().amount()).isEqualByComparingTo("49.99");
        assertThat(result.getFirst().paymentMethodLast4()).isEqualTo("4242");
        assertThat(result.getFirst().stripeMode()).isEqualTo(StripeMode.TEST);
    }
}
