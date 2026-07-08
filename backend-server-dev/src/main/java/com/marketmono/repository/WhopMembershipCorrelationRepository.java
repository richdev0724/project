package com.marketmono.repository;

import com.marketmono.entity.WhopMembershipCorrelation;
import com.marketmono.entity.WhopMembershipCorrelationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhopMembershipCorrelationRepository extends JpaRepository<WhopMembershipCorrelation, UUID> {

    Optional<WhopMembershipCorrelation> findByWhopMembershipId(String whopMembershipId);

    @Query(
        value = """
            SELECT c
            FROM WhopMembershipCorrelation c
            LEFT JOIN FETCH c.product
            WHERE c.status IN :statuses
            AND (
                :search IS NULL
                OR LOWER(c.buyerEmail) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.whopMembershipId) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """,
        countQuery = """
            SELECT COUNT(c)
            FROM WhopMembershipCorrelation c
            WHERE c.status IN :statuses
            AND (
                :search IS NULL
                OR LOWER(c.buyerEmail) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.whopMembershipId) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """
    )
    Page<WhopMembershipCorrelation> findReviews(
        @Param("statuses") List<WhopMembershipCorrelationStatus> statuses,
        @Param("search") String search,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT c
        FROM WhopMembershipCorrelation c
        LEFT JOIN FETCH c.product p
        LEFT JOIN FETCH p.prices
        WHERE c.id = :id
        """)
    Optional<WhopMembershipCorrelation> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
        SELECT c
        FROM WhopMembershipCorrelation c
        LEFT JOIN FETCH c.product p
        LEFT JOIN FETCH p.prices
        WHERE LOWER(c.buyerEmail) = LOWER(:buyerEmail)
        AND c.status = :status
        ORDER BY c.createdAt ASC
        """)
    List<WhopMembershipCorrelation> findByBuyerEmailAndStatusForReconciliation(
        @Param("buyerEmail") String buyerEmail,
        @Param("status") WhopMembershipCorrelationStatus status
    );
}
