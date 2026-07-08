package com.marketmono.repository;

import com.marketmono.entity.Politician;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Politician entity
 * Provides CRUD operations and custom queries
 */
@Repository
public interface PoliticianRepository extends JpaRepository<Politician, UUID> {
    
    /**
     * Find a politician by exact name match
     * Used by sync service to match Congress trades to politicians
     * 
     * @param name the politician's full name
     * @return Optional containing the politician if found
     */
    Optional<Politician> findByName(String name);
}
