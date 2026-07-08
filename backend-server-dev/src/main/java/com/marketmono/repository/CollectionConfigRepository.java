package com.marketmono.repository;

import com.marketmono.entity.CollectionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for key-value collection configuration records.
 */
@Repository
public interface CollectionConfigRepository extends JpaRepository<CollectionConfig, Long> {

    Optional<CollectionConfig> findByConfigKey(String configKey);

    @Modifying
    @Query(value = """
        INSERT INTO collection_config (config_key, config_value, description, updated_by, created_at, updated_at)
        VALUES (:configKey, CAST(:configValue AS jsonb), :description, :updatedBy, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON CONFLICT (config_key) DO UPDATE
        SET config_value = CAST(:configValue AS jsonb),
            description = COALESCE(:description, collection_config.description),
            updated_by = :updatedBy,
            updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertConfig(
        @Param("configKey") String configKey,
        @Param("configValue") String configValue,
        @Param("description") String description,
        @Param("updatedBy") String updatedBy
    );
}