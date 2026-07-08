package com.marketmono.repository;

import com.marketmono.entity.Referral;
import com.marketmono.entity.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    Optional<Referral> findByReferredUser(User referredUser);

    @Query("SELECT r FROM Referral r JOIN FETCH r.referredUser WHERE r.referrer = :referrer ORDER BY r.createdAt DESC")
    List<Referral> findByReferrerOrderByCreatedAtDesc(@Param("referrer") User referrer);
}
