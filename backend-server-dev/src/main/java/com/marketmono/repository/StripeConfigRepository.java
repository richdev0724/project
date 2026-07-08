package com.marketmono.repository;

import com.marketmono.entity.StripeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for StripeConfig entity operations.
 * Key-value storage for Stripe configuration settings.
 */
@Repository
public interface StripeConfigRepository extends JpaRepository<StripeConfig, UUID> {
    
    /**
     * Find configuration by name.
     */
    Optional<StripeConfig> findByName(String name);
    
    /**
     * Check if configuration exists by name.
     */
    boolean existsByName(String name);
    
    /**
     * Find all configurations.
     */
    List<StripeConfig> findAll();
    
    /**
     * Find configurations by names.
     */
    @Query("SELECT c FROM StripeConfig c WHERE c.name IN :names")
    List<StripeConfig> findByNames(@Param("names") List<String> names);
    
    /**
     * Get value by name, returns null if not found.
     */
    @Query("SELECT c.value FROM StripeConfig c WHERE c.name = :name")
    Optional<String> findValueByName(@Param("name") String name);
    
    /**
     * Update or insert a configuration value.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
        INSERT INTO stripe_config (id, name, value, created_at, updated_at)
        VALUES (gen_random_uuid(), :name, :value, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (name) DO UPDATE SET value = :value, updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertConfig(@Param("name") String name, @Param("value") String value);
    
    /**
     * Check if Stripe is configured (has any keys).
     */
    @Query("""
        SELECT COUNT(c) 
        FROM StripeConfig c 
        WHERE c.name IN ('test_public_key', 'test_secret_key', 'prod_public_key', 'prod_secret_key')
        AND c.value IS NOT NULL AND c.value <> ''
        """)
    long countAnyKeysConfigured();

    default boolean hasAnyKeysConfigured() {
        return countAnyKeysConfigured() > 0;
    }
    
    /**
     * Check if test keys are configured.
     */
    @Query("""
        SELECT COUNT(c) 
        FROM StripeConfig c 
        WHERE c.name IN ('test_public_key', 'test_secret_key')
        AND c.value IS NOT NULL AND c.value <> ''
        """)
    long countTestKeysConfigured();

    default boolean hasTestKeysConfigured() {
        return countTestKeysConfigured() == 2;
    }
    
    /**
     * Check if production keys are configured.
     */
    @Query("""
        SELECT COUNT(c) 
        FROM StripeConfig c 
        WHERE c.name IN ('prod_public_key', 'prod_secret_key')
        AND c.value IS NOT NULL AND c.value <> ''
        """)
    long countProdKeysConfigured();

    default boolean hasProdKeysConfigured() {
        return countProdKeysConfigured() == 2;
    }
}
