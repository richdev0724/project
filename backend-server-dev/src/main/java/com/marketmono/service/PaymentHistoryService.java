package com.marketmono.service;

import com.marketmono.dto.PaymentHistoryDto;
import com.marketmono.entity.auth.User;
import com.marketmono.repository.PaymentHistoryRepository;
import com.marketmono.repository.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service for user-facing billing history operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentHistoryService {

    private final PaymentHistoryRepository paymentHistoryRepository;
    private final UserRepository userRepository;

    /**
     * Get payment history for the specified user ordered by newest first.
     */
    @Transactional(readOnly = true)
    public List<PaymentHistoryDto> getUserPaymentHistory(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<PaymentHistoryDto> paymentHistory = paymentHistoryRepository.findByUserOrderByPaidAtDesc(user).stream()
            .map(PaymentHistoryDto::from)
            .toList();

        log.debug("Loaded {} payment history records for user {}", paymentHistory.size(), userId);
        return paymentHistory;
    }
}
