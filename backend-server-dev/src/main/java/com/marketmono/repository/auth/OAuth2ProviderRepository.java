package com.marketmono.repository.auth;

import com.marketmono.entity.auth.OAuth2Provider;
import com.marketmono.entity.auth.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OAuth2ProviderRepository extends JpaRepository<OAuth2Provider, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p FROM OAuth2Provider p
        JOIN FETCH p.user
        WHERE p.provider = :provider
        AND p.providerUserId = :providerUserId
        """)
    Optional<OAuth2Provider> findByProviderAndProviderUserIdForUpdate(
        @Param("provider") String provider,
        @Param("providerUserId") String providerUserId
    );

    void deleteByUser(User user);
}
