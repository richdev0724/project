package com.marketmono.repository;

import com.marketmono.entity.Referral;
import com.marketmono.entity.ReferralReward;
import com.marketmono.entity.ReferralRewardStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface ReferralRewardRepository extends JpaRepository<ReferralReward, UUID> {

    boolean existsByReferral(Referral referral);

    long countByUserIdAndStatus(UUID userId, ReferralRewardStatus status);

    @Query("SELECT COALESCE(SUM(r.rewardAmount), 0) FROM ReferralReward r WHERE r.user.id = :userId AND r.status = :status")
    BigDecimal sumRewardAmountByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") ReferralRewardStatus status);
}
