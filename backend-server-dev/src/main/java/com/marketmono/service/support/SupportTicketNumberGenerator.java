package com.marketmono.service.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;

@Service
public class SupportTicketNumberGenerator {

    @PersistenceContext
    private EntityManager entityManager;

    public String nextTicketNumber() {
        Object value = entityManager
            .createNativeQuery("SELECT nextval('support_ticket_number_seq')")
            .getSingleResult();
        long sequenceValue = ((Number) value).longValue();
        return "MM-%04d".formatted(sequenceValue);
    }
}
