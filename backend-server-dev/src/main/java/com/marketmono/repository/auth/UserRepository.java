package com.marketmono.repository.auth;

import com.marketmono.entity.auth.User;
import com.marketmono.entity.auth.UserRegistrationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Find user by email (case-insensitive).
     */
    @Query("SELECT u FROM User u WHERE u.emailLowercase = LOWER(:email) AND u.deletedAt IS NULL")
    Optional<User> findByEmail(@Param("email") String email);

    /**
     * Find user by email with a write lock for registration lifecycle upgrades.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.emailLowercase = LOWER(:email) AND u.deletedAt IS NULL")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    /**
     * Find user by email with a write lock, including soft-deleted accounts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.emailLowercase = LOWER(:email)")
    Optional<User> findByEmailIncludingDeletedForUpdate(@Param("email") String email);

    /**
     * Find user by email and registration status.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.emailLowercase = LOWER(:email)
        AND u.registrationStatus = :status
        AND u.deletedAt IS NULL
        """)
    Optional<User> findByEmailAndRegistrationStatus(
        @Param("email") String email,
        @Param("status") UserRegistrationStatus status
    );

    /**
     * Find users by registration status for admin/CRM read models.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.registrationStatus = :status
        AND u.deletedAt IS NULL
        ORDER BY u.waitlistJoinedAt DESC, u.createdAt DESC
        """)
    List<User> findByRegistrationStatusOrderByWaitlistJoinedAtDesc(@Param("status") UserRegistrationStatus status);

    /**
     * Find customer users created through the SuperAdmin invite flow.
     */
    @Query("""
        SELECT u FROM User u
        WHERE u.customerInvitedAt IS NOT NULL
        AND u.deletedAt IS NULL
        ORDER BY u.customerInvitedAt DESC
        """)
    List<User> findCustomerInvitesOrderByInvitedAtDesc();

    /**
     * Find active waitlisted users by IDs with a write lock for admin lifecycle actions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u FROM User u
        WHERE u.id IN :ids
        AND u.registrationStatus = com.marketmono.entity.auth.UserRegistrationStatus.WAITLISTED
        AND u.deletedAt IS NULL
        """)
    List<User> findActiveWaitlistedByIdInForUpdate(@Param("ids") List<UUID> ids);

    /**
     * Find active waitlisted user by ID with a write lock for admin lifecycle actions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u FROM User u
        WHERE u.id = :id
        AND u.registrationStatus = com.marketmono.entity.auth.UserRegistrationStatus.WAITLISTED
        AND u.deletedAt IS NULL
        """)
    Optional<User> findActiveWaitlistedByIdForUpdate(@Param("id") UUID id);

    /**
     * Find waitlist-origin users by IDs for beta invite preview.
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.betaAccess ba
        WHERE u.id IN :ids
        AND u.deletedAt IS NULL
        AND (
            u.registrationStatus = com.marketmono.entity.auth.UserRegistrationStatus.WAITLISTED
            OR ba.betaSignedUpAt IS NOT NULL
        )
        """)
    List<User> findBetaWaitlistCandidatesByIdInForPreview(@Param("ids") List<UUID> ids);

    /**
     * Find waitlist-origin users by IDs with a write lock for beta invite lifecycle actions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.betaAccess ba
        WHERE u.id IN :ids
        AND u.deletedAt IS NULL
        AND (
            u.registrationStatus = com.marketmono.entity.auth.UserRegistrationStatus.WAITLISTED
            OR ba.betaSignedUpAt IS NOT NULL
        )
        """)
    List<User> findBetaWaitlistCandidatesByIdInForUpdate(@Param("ids") List<UUID> ids);

    /**
     * Find active waitlisted users that have not already been converted into beta users.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.betaAccess ba
        WHERE u.registrationStatus = com.marketmono.entity.auth.UserRegistrationStatus.WAITLISTED
        AND COALESCE(u.betaUser, false) = false
        AND u.deletedAt IS NULL
        ORDER BY u.waitlistJoinedAt ASC, u.createdAt ASC
        """)
    List<User> findAllEligibleBetaWaitlistInviteUsersForUpdate();

    /**
     * Preview active waitlisted users that have not already been converted into beta users.
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.betaAccess ba
        WHERE u.registrationStatus = com.marketmono.entity.auth.UserRegistrationStatus.WAITLISTED
        AND COALESCE(u.betaUser, false) = false
        AND u.deletedAt IS NULL
        ORDER BY u.waitlistJoinedAt ASC, u.createdAt ASC
        """)
    List<User> findEligibleBetaWaitlistInviteUsersForPreview();

    /**
     * Find waitlist-origin beta history and active waitlist rows for admin read models.
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        LEFT JOIN FETCH u.betaAccess ba
        WHERE u.deletedAt IS NULL
        AND (
            u.registrationStatus = com.marketmono.entity.auth.UserRegistrationStatus.WAITLISTED
            OR ba.betaSignedUpAt IS NOT NULL
        )
        ORDER BY CASE WHEN ba.betaSignedUpAt IS NULL THEN 1 ELSE 0 END,
                 ba.betaSignedUpAt DESC,
                 u.waitlistJoinedAt DESC,
                 u.createdAt DESC
        """)
    List<User> findWaitlistAndBetaHistoryOrderByRecentActivity();
    
    /**
     * Find user by email including deleted accounts.
     */
    @Query("SELECT u FROM User u WHERE u.emailLowercase = LOWER(:email)")
    Optional<User> findByEmailIncludingDeleted(@Param("email") String email);
    
    /**
     * Check if email exists (case-insensitive).
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.emailLowercase = LOWER(:email)")
    long countByEmail(@Param("email") String email);

    default boolean existsByEmail(String email) {
        return countByEmail(email) > 0;
    }

    /**
     * Find user by referral code.
     */
    @Query("SELECT u FROM User u WHERE u.referralCode = :referralCode AND u.deletedAt IS NULL")
    Optional<User> findByReferralCode(@Param("referralCode") String referralCode);

    /**
     * Check if a referral code is already in use.
     */
    @Query("SELECT COUNT(u) FROM User u WHERE u.referralCode = :referralCode")
    long countByReferralCode(@Param("referralCode") String referralCode);

    default boolean existsByReferralCode(String referralCode) {
        return countByReferralCode(referralCode) > 0;
    }
    
    /**
     * Find active user by ID (not deleted).
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveById(@Param("id") UUID id);

    /**
     * Find active user by ID with a write lock for admin lifecycle actions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<User> findActiveByIdForUpdate(@Param("id") UUID id);

    /**
     * Find user by ID with a write lock, including soft-deleted accounts.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdIncludingDeletedForUpdate(@Param("id") UUID id);

    /**
     * Check whether another active user owns the normalized email.
     */
    @Query("""
        SELECT COUNT(u) > 0 FROM User u
        WHERE u.emailLowercase = LOWER(:emailLowercase)
        AND u.id <> :id
        AND u.deletedAt IS NULL
        """)
    boolean existsActiveByEmailLowercaseAndIdNot(
        @Param("emailLowercase") String emailLowercase,
        @Param("id") UUID id
    );

    /**
     * Check whether a normalized email is blocked by an admin ban.
     */
    @Query("""
        SELECT COUNT(u) > 0 FROM User u
        WHERE u.bannedEmailLowercase = LOWER(:emailLowercase)
        AND u.bannedAt IS NOT NULL
        """)
    boolean existsBannedByEmailLowercase(@Param("emailLowercase") String emailLowercase);
    
    /**
     * Find user by Stripe customer ID (test or prod).
     */
    @Query("""
        SELECT u FROM User u 
        WHERE u.deletedAt IS NULL 
        AND (u.stripeCustomerIdTest = :customerIdTest OR u.stripeCustomerIdProd = :customerIdProd)
        """)
    Optional<User> findByStripeCustomerIdTestOrStripeCustomerIdProd(
        @Param("customerIdTest") String customerIdTest, 
        @Param("customerIdProd") String customerIdProd
    );
}
