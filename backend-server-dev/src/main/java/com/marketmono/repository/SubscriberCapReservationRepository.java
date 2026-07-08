package com.marketmono.repository;

import com.marketmono.entity.SubscriberCapReservation;
import com.marketmono.entity.SubscriberCapReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriberCapReservationRepository extends JpaRepository<SubscriberCapReservation, UUID> {

    @Query(value = "SELECT pg_advisory_xact_lock(742031010)", nativeQuery = true)
    void lockLaunchCapScope();

    @Query("""
        SELECT COUNT(r)
        FROM SubscriberCapReservation r
        WHERE r.status = 'PENDING'
        AND r.expiresAt > :now
        """)
    long countPendingReservationsForLaunchCap(LocalDateTime now);

    Optional<SubscriberCapReservation> findByIdAndStatus(UUID id, SubscriberCapReservationStatus status);
}
